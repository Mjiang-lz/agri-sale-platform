#!/usr/bin/env bash
#
# 构建「解压即到位」的部署包（宝塔手工部署用，配合 docs/部署-腾讯云共享环境.md）
#
# 为什么要有这个脚本：
#   在它之前，每轮发版都是人手工拼目录、手工敲 zip —— 于是漏掉了 sql/migrations/。
#   后果是真实发生过的：2026-10-13 新增的 t_notification 表永远到不了服务器，
#   通知中心的两个接口一直 500「系统繁忙」，直到 10-14 才被查出来。
#   ★ 手工流程里「漏一个目录」不报错、不显眼，只有到线上才炸 —— 这类环节必须固化。
#
# 用法：
#   scripts/build-deploy.sh                 # 只打包三端 + sql + nginx conf（默认，安全）
#   scripts/build-deploy.sh --with-jar      # 额外构建后端并装入 app.jar
#   scripts/build-deploy.sh --skip-build    # 跳过构建，只重新组装（产物已在时用）
#   scripts/build-deploy.sh -o /tmp/x.zip   # 指定输出路径
#
# ★ 为什么默认「不含 app.jar」：
#   `unzip -o` 对已存在的文件是「就地截断覆盖」（同一个 inode），而在跑的 JVM 已经打开着那个 jar，
#   且 Java 是懒加载 class 的 —— 拿一个内容没变的 jar 去覆盖，也可能让运行中的进程抛
#   NoClassDefFoundError，报错位置还跟改动毫无关系。所以「后端没改就别装 jar」，
#   这里把它做成必须显式 --with-jar 才装。
#
set -euo pipefail

# ---------------------------------------------------------------------------
# 0. 定位与参数
# ---------------------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

WITH_JAR=0
SKIP_BUILD=0
OUT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --with-jar)   WITH_JAR=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    -o)           OUT="${2:-}"; shift ;;
    -h|--help)    sed -n '2,26p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *)            echo "未知参数：$1（-h 看用法）" >&2; exit 2 ;;
  esac
  shift
done

ok()   { printf '\033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '\033[33m!\033[0m %s\n' "$1"; }
die()  { printf '\033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. 打之前先确认「发的是哪个提交」
# ---------------------------------------------------------------------------
HEAD_SHA="$(git rev-parse HEAD)"
HEAD_SHORT="$(git rev-parse --short HEAD)"
DEV_SHORT="$(git rev-parse --short dev 2>/dev/null || echo '-')"
ORIGIN_DEV_SHORT="$(git rev-parse --short origin/dev 2>/dev/null || echo '-')"

echo "=========================================================="
echo " 当前 HEAD   : ${HEAD_SHORT}"
echo " 本地 dev    : ${DEV_SHORT}"
echo " origin/dev  : ${ORIGIN_DEV_SHORT}"
echo "=========================================================="
echo

if [ -n "$(git status --porcelain)" ]; then
  git status --short
  warn "工作区不干净（上面这些改动会被打进产物）。确认这是你要发的状态。"
  echo
fi

if [ "${HEAD_SHORT}" != "${DEV_SHORT}" ]; then
  warn "当前不在 dev 上！在 feature 分支上构建会『静默丢掉』已并入 dev 的别人的改动，"
  warn "而且构建照样成功、看不出来。本项目要发的分支是 dev。"
  echo
fi

# ---------------------------------------------------------------------------
# 2. 构建（★ 三个构建期变量，漏一个就是线上白屏 / 图裂 / 链接指到访问者本机）
# ---------------------------------------------------------------------------
if [ "${SKIP_BUILD}" = "1" ]; then
  warn "--skip-build：跳过构建，直接用现有产物"
else
  echo "── 构建业务端（RXT_BASE=/app/）──"
  ( cd frontend-web     && NODE_OPTIONS= RXT_BASE=/app/   npm run build )
  echo
  echo "── 构建管理后台（RXT_BASE=/admin/）──"
  ( cd frontend-admin   && NODE_OPTIONS= RXT_BASE=/admin/ npm run build )
  echo
  echo "── 构建落地页（VITE_PLATFORM_BASE_URL=/app）──"
  ( cd frontend-landing && NODE_OPTIONS= VITE_PLATFORM_BASE_URL=/app npm run build )
  echo

  if [ "${WITH_JAR}" = "1" ]; then
    echo "── 构建后端（--with-jar）──"
    ( cd backend && NODE_OPTIONS= mvn -q -DskipTests clean package )
    echo
  fi
fi

for d in frontend-web frontend-admin frontend-landing; do
  [ -f "${d}/dist/index.html" ] || die "${d}/dist/index.html 不存在 —— 构建没成功？"
done
if [ "${WITH_JAR}" = "1" ]; then
  [ -f backend/target/rxt-backend.jar ] || die "backend/target/rxt-backend.jar 不存在"
fi

# ---------------------------------------------------------------------------
# 3. 组装 rxt/
#    布局（★ www/ 那一层是防火墙本身，不是多余层级：Nginx root 直接指到 rxt/ 的话，
#    app.jar 与 sql/data.sql 会变成可下载的静态文件）
# ---------------------------------------------------------------------------
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/rxt-stage.XXXXXX")"
trap 'rm -rf "${STAGE}"' EXIT
R="${STAGE}/rxt"
mkdir -p "${R}/sql/migrations" "${R}/www"

cp deploy/nginx-baota-8080.conf "${R}/nginx-baota-8080.conf"

if [ "${WITH_JAR}" = "1" ]; then
  cp backend/target/rxt-backend.jar "${R}/app.jar"
fi

cp backend/src/main/resources/db/schema.sql "${R}/sql/schema.sql"
cp backend/src/main/resources/db/data.sql   "${R}/sql/data.sql"
# ★★ 这条就是本次要补的那一条：增量迁移脚本必须一起进包，否则「新表/新列」永远到不了服务器
#    ★ 排除 _TEMPLATE.sql —— 它是写迁移脚本用的模板（内含 CREATE TABLE t_example），不是真迁移
for f in backend/src/main/resources/db/migrations/*.sql; do
  case "$(basename "${f}")" in
    _TEMPLATE.sql) continue ;;
  esac
  cp "${f}" "${R}/sql/migrations/"
done

cp -R frontend-landing/dist/. "${R}/www/"
mkdir -p "${R}/www/app" "${R}/www/admin"
cp -R frontend-web/dist/.   "${R}/www/app/"
cp -R frontend-admin/dist/. "${R}/www/admin/"

find "${STAGE}" -name '.DS_Store' -delete
find "${STAGE}" -name '__MACOSX' -prune -exec rm -rf {} + 2>/dev/null || true

echo "── 打包前的自检 ──"
FAIL=0
note() { printf '   %-46s %s\n' "$1" "$2"; }
bad()  { printf '   %-46s %s\n' "$1" "\033[31m$2\033[0m"; FAIL=1; }

# ① 三端入口前缀（判据 = src/href 是否带子路径；不带就白屏 / 图变首字色块）
web_entry="$(/usr/bin/grep -oE 'src="[^"]*index-[^"]*\.js"' "${R}/www/app/index.html" | head -1 || true)"
adm_entry="$(/usr/bin/grep -oE 'src="[^"]*index-[^"]*\.js"' "${R}/www/admin/index.html" | head -1 || true)"
lnd_entry="$(/usr/bin/grep -oE 'src="[^"]*index-[^"]*\.js"' "${R}/www/index.html" | head -1 || true)"
case "${web_entry}" in *'"/app/assets/'*)  note "业务端入口前缀 /app/"   "OK";; *) bad "业务端入口前缀 /app/"   "${web_entry}";; esac
case "${adm_entry}" in *'"/admin/assets/'*) note "管理后台入口前缀 /admin/" "OK";; *) bad "管理后台入口前缀 /admin/" "${adm_entry}";; esac
case "${lnd_entry}" in *'"/assets/'*)      note "落地页入口前缀 /"       "OK";; *) bad "落地页入口前缀 /"       "${lnd_entry}";; esac

# ② 落地页不能残留平台地址的默认值（漏了 VITE_PLATFORM_BASE_URL 时「进入平台」会指向 localhost:5173）
if /usr/bin/grep -rq 'localhost:5173' "${R}/www" 2>/dev/null; then
  bad "落地页产物里含 localhost:5173" "漏了 VITE_PLATFORM_BASE_URL=/app"
else
  note "落地页无 localhost:5173" "OK"
fi

# ③ 商品图逐名 diff（★ 不要写死条数：条数会变，而「少一个文件」只有 diff 能报出来）
if diff -q <(/bin/ls -1 frontend-web/public/products) <(/bin/ls -1 "${R}/www/app/products") >/dev/null; then
  note "业务端商品图与源码逐名一致" "OK"
else
  bad "业务端商品图与源码逐名一致" "有条目差异"
fi
if [ -d frontend-landing/public/products ]; then
  if diff -q <(/bin/ls -1 frontend-landing/public/products) <(/bin/ls -1 "${R}/www/products") >/dev/null; then
    note "落地页商品图与源码逐名一致" "OK"
  else
    bad "落地页商品图与源码逐名一致" "有条目差异"
  fi
fi

# ④ index.html 里引用的每个文件都要真实存在（防「SPA 回退 200 + text/html」假通过）
for pair in "app:${R}/www/app" "admin:${R}/www/admin" "landing:${R}/www"; do
  name="${pair%%:*}"; root="${pair#*:}"
  miss=0
  while IFS= read -r p; do
    [ -n "${p}" ] || continue
    rel="$(printf '%s' "${p}" | /usr/bin/sed 's|^/app/||; s|^/admin/||; s|^/||')"
    [ -e "${root}/${rel}" ] || { miss=$((miss+1)); warn "   ${name} 缺文件：${p}"; }
  done < <(/usr/bin/grep -oE '(src|href)="[^"]*"' "${root}/index.html" | /usr/bin/sed 's/^[a-z]*="//; s/"$//')
  if [ "${miss}" = "0" ]; then note "${name} 入口引用的资源全部存在" "OK"; else bad "${name} 入口引用的资源" "${miss} 个缺失"; fi
done

# ⑤ www/ 那层防火墙：rxt/ 根下不能出现可下载的 index.html
if [ -e "${R}/index.html" ]; then bad "rxt/ 根下不应有 index.html" "防火墙被破坏"
else note "www/ 防火墙完整（rxt/ 根下无 index.html）" "OK"; fi

# ⑥ jar 的安全断言（★ 判据必须能区分修前修后：只查「local 消失了」不够，
#    还要确认 application.yml 没被误伤；另用真实密钥形态正则，避免 sk- 子串假阳性）
if [ "${WITH_JAR}" = "1" ]; then
  if unzip -l "${R}/app.jar" | /usr/bin/grep -q 'application-local'; then
    bad "jar 内不含 application-local.yml" "★ 真实密钥会被烤进产物！"
  else
    note "jar 内不含 application-local.yml" "OK"
  fi
  # ★ 末尾的 `|| true` 不能省：脚本开了 `set -o pipefail`，而 grep 零命中会返回非 0，
  #   经管道传下去会让整条赋值语句失败 ⇒ `set -e` 直接中断脚本（静默「跑一半就停」）。
  #   这类「管道把退出码传下去」的坑在本项目出现过多次，凡「期望可能为空」的 grep 都要兜。
  n_sk="$( { unzip -p "${R}/app.jar" | /usr/bin/grep -aoE 'sk-[A-Za-z0-9_-]{20,}' || true; } | wc -l | tr -d ' ')"
  if [ "${n_sk}" = "0" ]; then note "jar 内无 sk- 形态密钥" "OK"; else bad "jar 内无 sk- 形态密钥" "${n_sk} 处命中"; fi
  note "jar sha256(前16)" "$(shasum -a 256 "${R}/app.jar" | cut -c1-16)"
fi

[ "${FAIL}" = "0" ] || die "自检未通过 —— 不要上传这个包"

# ---------------------------------------------------------------------------
# 4. 出包
# ---------------------------------------------------------------------------
[ -n "${OUT}" ] || OUT="${HOME}/Desktop/rxt-deploy-${HEAD_SHORT}.zip"
rm -f "${OUT}"
( cd "${STAGE}" && zip -rq "${OUT}" rxt -x '*.DS_Store' )

echo
echo "=========================================================="
echo " 部署包 : ${OUT}"
echo " 大小   : $(wc -c < "${OUT}" | tr -d ' ') 字节"
echo " sha256 : $(shasum -a 256 "${OUT}" | cut -d' ' -f1)"
echo " 条目数 : $(unzip -l "${OUT}" | tail -1 | awk '{print $(NF-1)}')"
echo " 内容   : $(cd "${STAGE}" && /bin/ls -1 rxt | tr '\n' ' ')"
echo " sql/   : schema.sql data.sql migrations/*.sql（$(/bin/ls -1 "${R}/sql/migrations" | wc -l | tr -d ' ') 个迁移脚本）"
[ "${WITH_JAR}" = "1" ] && echo " 含 app.jar（★ 覆盖后必须重启 Java 进程才生效）" || echo " ★ 不含 app.jar（后端无需更新）"
echo "=========================================================="
echo
echo "上传后在服务器上："
echo "  cd /www/wwwroot && unzip -o $(basename "${OUT}")"
echo "  （★ 不是 cd /www/wwwroot/rxt，否则会铺成 rxt/rxt/ 而看不到任何变化）"
[ "${WITH_JAR}" = "1" ] && echo "  然后重启 Java 进程（jar 变了）"
echo
echo "★ 本次是否有新迁移脚本要跑："
for f in "${R}"/sql/migrations/*.sql; do
  echo "    $(basename "${f}")"
done
echo "  服务器上比对后按需执行：mysql -uroot -p < /www/wwwroot/rxt/sql/migrations/<文件>"

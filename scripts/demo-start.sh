#!/usr/bin/env bash
#
# 融销通 · 答辩演示一键启动
#
# 一条命令把演示要用的进程拉起来，并逐个等它们「真的能用了」再报成功：
#
#   后端 API    http://localhost:8080/api          （Swagger: /api/swagger-ui.html）
#   业务端      http://localhost:5173
#   管理后台    http://localhost:5174
#   落地页      http://localhost:3000
#
# 用法：
#   scripts/demo-start.sh                起全部（后端已在跑就复用，不重复拉）
#   scripts/demo-start.sh --no-backend   只起三个前端（后端你自己开着）
#   scripts/demo-start.sh --open         起来后顺带用默认浏览器打开落地页
#
# Ctrl-C 退出时，本次拉起的进程会被一并收掉；启动前就已经在跑的进程（比如你自己
# 开的后端）不会被碰。
#
# 端口可用环境变量覆盖：BACKEND_PORT / WEB_PORT / ADMIN_PORT / LANDING_PORT
#
# ⚠️ 维护提示：本脚本带大量中文输出，写变量时一律用 "${var}" 花括号形式。
# 形如 "$busy）" 的写法会踩坑 —— bash 在解析变量名时会把后面那个全角字符的字节
# 并进名字里，取到一个叫 "busy<乱码>" 的变量；在 set -u 下这会直接让脚本挂掉，
# 而且挂的正是"本该打印错误信息"的那一刻，真正的错误反而被吃掉。
#
set -uo pipefail

# 让每个后台任务自成一个进程组，退出时才能连同子进程一起收干净。
# 这一条很关键：`mvn spring-boot:run` 会 fork 出一个 java 子进程，
# `npm`/`vite` 同样是套娃，只 kill 直接子进程会留下孤儿占着端口。
set -m

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT}/logs/demo"

BACKEND_PORT="${BACKEND_PORT:-8080}"
WEB_PORT="${WEB_PORT:-5173}"
ADMIN_PORT="${ADMIN_PORT:-5174}"
LANDING_PORT="${LANDING_PORT:-3000}"

START_BACKEND=1
OPEN_BROWSER=0

usage() {
  sed -n '2,23p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
  case "${arg}" in
    --no-backend) START_BACKEND=0 ;;
    --open)       OPEN_BROWSER=1 ;;
    -h|--help)    usage; exit 0 ;;
    *)            printf '未知参数：%s（-h 看用法）\n' "${arg}" >&2; exit 2 ;;
  esac
done

if [ -t 1 ]; then
  C_OK=$'\033[32m'; C_ERR=$'\033[31m'; C_WARN=$'\033[33m'; C_DIM=$'\033[2m'; C_OFF=$'\033[0m'
else
  C_OK=; C_ERR=; C_WARN=; C_DIM=; C_OFF=
fi
info() { printf '%s\n' "$*"; }
ok()   { printf '%s✔%s %s\n' "${C_OK}" "${C_OFF}" "$*"; }
warn() { printf '%s▲%s %s\n' "${C_WARN}" "${C_OFF}" "$*" >&2; }
die()  { printf '%s✘%s %s\n' "${C_ERR}" "${C_OFF}" "$*" >&2; exit 1; }
step() { printf '\n%s── %s ──%s\n' "${C_DIM}" "$*" "${C_OFF}"; }

# ---------------------------------------------------------------------------
# 环境清理
#
# 有些沙箱 / CI 的外壳会往每个子进程里注入下面这些变量，而它们会以非常难查的方式生效：
#   SERVER__PORT / SERVER__HOST —— Spring 的 relaxed binding 把双下划线当分隔符，
#     读成 server.port / server.address，优先级高于 application.yml。
#     症状是「启动日志明明写着 Tomcat started on port X，curl 过去拿到的却是别的东西」。
#   NODE_OPTIONS —— 部分环境通过它注入 preload 脚本，会让 npm/vite 建目录失败。
#   ELECTRON_RUN_AS_NODE —— 会让 Electron 退化成纯 Node。
# 在普通终端里这些变量本来就不存在，unset 无副作用。
# ---------------------------------------------------------------------------
unset SERVER__PORT SERVER__HOST NODE_OPTIONS ELECTRON_RUN_AS_NODE

# ---------------------------------------------------------------------------
# 探测工具
#
# ⚠️ 一律带 --noproxy '*'：环境里可能注入了 HTTP_PROXY，本地回环请求走代理
# 会拿到 502 之类的假象，看起来像「服务没起来」。
# ---------------------------------------------------------------------------
http_get() { curl -sS --noproxy '*' -m "${2:-5}" "$1" 2>/dev/null; }

# ⚠️ 回环探测必须两个协议族都试。
# vite 在 macOS 上默认只监听 `localhost`，而这个名字解析优先给 ::1 ——
# `lsof` 会看到 `TCP [::1]:5173 (LISTEN)`，此时用 http://127.0.0.1:5173 去探
# 是 connection refused，于是「已经起好了」被误判成「没起来」，
# 脚本会一直等到超时再报一个完全错误的结论。
probe_loopback() {
  local port="$1" path="$2" tmo="${3:-3}" marker="$4"
  http_get "http://127.0.0.1:${port}${path}" "${tmo}" | grep -q "${marker}" && return 0
  http_get "http://[::1]:${port}${path}" "${tmo}" | grep -q "${marker}"
}

port_pids() { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | tr '\n' ' '; }

# 判据不是「端口通不通」，而是「响应体是不是我们自己的东西」。
# 只看端口通会误判 —— 别的服务占了 8080 一样"有响应"，然后我们会带着一个假的后端
# 把整个演示跑下去。这类"看起来对了"的失败最难发现。
is_our_backend()  { probe_loopback "${BACKEND_PORT}" "/api/trade/products" 3 '"code"'; }
is_our_frontend() { probe_loopback "$1" "/" 2 '融销通'; }

wait_backend() {
  # ⚠️ 必须拆成两条 local：bash 对同一条 local 里的多个赋值是先做完全部词展开、
  # 再逐个赋值，所以写成 `local limit=... deadline=$((...+limit))` 时，
  # 算术展开跑的时候 limit 还没被声明 —— set -u 下直接报 unbound variable。
  local limit="${1:-180}"
  local deadline=$(( $(date +%s) + limit ))
  printf '  等待后端就绪（最多 %ss）' "${limit}"
  while [ "$(date +%s)" -lt "${deadline}" ]; do
    if is_our_backend; then printf '\n'; ok "后端已就绪 http://localhost:${BACKEND_PORT}/api"; return 0; fi
    printf '.'; sleep 3
  done
  printf '\n'; return 1
}

wait_frontend() {
  local name="$1" port="$2"
  local limit="${3:-90}"
  local deadline=$(( $(date +%s) + limit ))
  printf '  等待 %s 就绪' "${name}"
  while [ "$(date +%s)" -lt "${deadline}" ]; do
    if is_our_frontend "${port}"; then printf '\n'; ok "${name} 已就绪 http://localhost:${port}"; return 0; fi
    printf '.'; sleep 1
  done
  printf '\n'; return 1
}

PIDS=""
LAST_LOG=""

# spawn <名字> <工作目录> <命令...>
spawn() {
  local name="$1" dir="$2"; shift 2
  LAST_LOG="${LOG_DIR}/${name}.log"
  ( cd "${dir}" && exec "$@" ) >"${LAST_LOG}" 2>&1 &
  local pid=$!
  PIDS="${PIDS} ${pid}"
  printf '  启动 %s（pid %s）→ %s\n' "${name}" "${pid}" "${LOG_DIR}/${name}.log"
}

CLEANED=0
cleanup() {
  [ "${CLEANED}" = 1 ] && return 0
  CLEANED=1
  [ -z "${PIDS}" ] && return 0
  printf '\n%s── 收尾：关闭本次拉起的进程 ──%s\n' "${C_DIM}" "${C_OFF}"
  local pid
  for pid in ${PIDS}; do
    kill -TERM -"${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null
  done
  local i alive
  for i in 1 2 3 4 5 6 7 8; do
    alive=0
    for pid in ${PIDS}; do kill -0 "${pid}" 2>/dev/null && alive=1; done
    [ "${alive}" = 0 ] && break
    sleep 1
  done
  for pid in ${PIDS}; do
    if kill -0 "${pid}" 2>/dev/null; then
      kill -KILL -"${pid}" 2>/dev/null || kill -KILL "${pid}" 2>/dev/null
    fi
  done
  ok "已关闭"
}
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# 预检
# ---------------------------------------------------------------------------
step "预检"

[ -d "${ROOT}/backend" ] || die "没找到 backend/，本脚本要放在仓库的 scripts/ 下运行"
mkdir -p "${LOG_DIR}"

# —— MySQL ——
MYSQL_UP=0
if nc -z 127.0.0.1 3306 2>/dev/null; then
  MYSQL_UP=1; ok "MySQL 3306 可达"
else
  warn "MySQL 3306 不可达"
fi

# —— Java / Maven ——
if [ "${START_BACKEND}" = 1 ]; then
  command -v mvn >/dev/null || die "找不到 mvn（Maven）。后端起不来，也可以加 --no-backend 只起前端。"
  if [ -z "${JAVA_HOME:-}" ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
    export JAVA_HOME
  fi
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    ok "JAVA_HOME=${JAVA_HOME}"
  else
    warn "JAVA_HOME 未设置 —— 后端需要 JDK 17，mvn 会用 PATH 里的 java，版本不对会编译不过"
  fi
  if [ "${MYSQL_UP}" = 0 ]; then
    die "MySQL 没起来，后端连不上库。先启动 MySQL（macOS 常见：brew services start mysql 或 mysql.server start），或加 --no-backend 只起前端。"
  fi
fi

# —— 前端依赖 ——
for pair in "frontend-web:业务端" "frontend-admin:管理后台" "frontend-landing:落地页"; do
  d="${pair%%:*}"; n="${pair##*:}"
  [ -d "${ROOT}/${d}/node_modules" ] || die "${n}（${d}）还没装依赖。先跑：cd ${d} && npm install"
  [ -x "${ROOT}/${d}/node_modules/.bin/vite" ] || die "${n}（${d}）的 node_modules 里没有 vite，依赖装坏了。重装：cd ${d} && rm -rf node_modules && npm install"
done
ok "三个前端的依赖都在"

# ---------------------------------------------------------------------------
# 起后端
# ---------------------------------------------------------------------------
step "后端"

if [ "${START_BACKEND}" = 0 ]; then
  if is_our_backend; then
    ok "复用已有的后端 http://localhost:${BACKEND_PORT}/api"
  else
    warn "指定了 --no-backend，但 ${BACKEND_PORT} 上不是本项目的后端；前端代理会打不通。"
  fi
elif is_our_backend; then
  ok "端口 ${BACKEND_PORT} 上已有一个本项目的后端在跑，直接复用（不会重复启动，也不会动它）"
else
  BUSY="$(port_pids "${BACKEND_PORT}")"
  if [ -n "${BUSY}" ]; then
    die "端口 ${BACKEND_PORT} 被别的进程占着（pid: ${BUSY}），且响应不是本项目后端。
       先确认那是什么再决定要不要停它： lsof -nP -iTCP:${BACKEND_PORT} -sTCP:LISTEN
       或者换个端口： BACKEND_PORT=18080 scripts/demo-start.sh"
  fi

  # 显式传 --server.port（命令行参数优先级最高，盖得住任何被注入的配置）。
  # 刻意不传 --server.address：让它保持 Spring 默认的「所有网卡 + 双栈」，
  # 否则只绑 IPv4 时，vite 代理把 localhost 解析成 ::1 会连不上。
  spawn "backend" "${ROOT}/backend" mvn spring-boot:run \
    -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT}"
  if ! wait_backend 240; then
    warn "后端 240s 内没就绪。最后 30 行日志："
    tail -30 "${LAST_LOG}" >&2
    die "后端启动失败，详见 ${LAST_LOG}"
  fi
fi

# ---------------------------------------------------------------------------
# 起三个前端
# ---------------------------------------------------------------------------

# start_frontend <名字> <目录> <端口> <覆盖端口用的环境变量名> [附加环境变量 K=V ...]
start_frontend() {
  local name="$1" dir="$2" port="$3" envkey="$4" extra_env="${5:-}"

  if is_our_frontend "${port}"; then
    ok "${name} 已在 ${port} 上跑着，复用"
    return 0
  fi

  local busy
  busy="$(port_pids "${port}")"
  if [ -n "${busy}" ]; then
    die "${name} 要用的端口 ${port} 被别的进程占着（pid: ${busy}）。
       先看看是谁： lsof -nP -iTCP:${port} -sTCP:LISTEN
       或者换个端口： ${envkey}=18080 scripts/demo-start.sh"
  fi

  # 显式 --strictPort：默认行为是端口被占就悄悄换一个，
  # 那样脚本打印的地址就是错的，演示时打开会扑空。
  #
  # extra_env 用 `env K=V` 前缀注入即可 —— spawn 内部就是 exec "$@"，
  # 不必为它再加参数。空值时 `env` 原样透传，是安全的空操作。
  # shellcheck disable=SC2086
  spawn "${name}" "${ROOT}/${dir}" env ${extra_env} "${ROOT}/${dir}/node_modules/.bin/vite" --port "${port}" --strictPort
  if ! wait_frontend "${name}" "${port}" 90; then
    warn "${name} 90s 内没就绪。最后 30 行日志："
    tail -30 "${LAST_LOG}" >&2
    die "${name} 启动失败，详见 ${LAST_LOG}"
  fi
}

step "前端"

# 三个前端的 /api 代理目标必须跟着**本次实际使用的**后端端口走。
# 写死 8080 的话，BACKEND_PORT=18080 这种演示会全部打偏 —— 8080 上要么没后端
# （前端报错但看不出为什么），要么躺着别人的进程（拿到看不懂的响应）。
API_ENV="VITE_API_TARGET=http://localhost:${BACKEND_PORT}"

start_frontend "业务端"   "frontend-web"     "${WEB_PORT}"     "WEB_PORT"     "${API_ENV}"
start_frontend "管理后台" "frontend-admin"   "${ADMIN_PORT}"   "ADMIN_PORT"   "${API_ENV}"
# 落地页还要知道业务端在哪：页面上「进入平台 / 登录 / 免费入驻」是**构建时/启动时**
# 定死的地址，前端默认值是 http://localhost:5173。只在 WEB_PORT 保持默认时才碰巧对，
# 一旦 WEB_PORT 被覆盖，这些按钮就会指向一个没人监听的端口。
start_frontend "落地页"   "frontend-landing" "${LANDING_PORT}" "LANDING_PORT" \
  "${API_ENV} VITE_PLATFORM_BASE_URL=http://localhost:${WEB_PORT}"

# ---------------------------------------------------------------------------
# 就绪
# ---------------------------------------------------------------------------
printf '\n'
info "${C_OK}════════ 演示环境已就绪 ════════${C_OFF}"
info ""
info "  落地页      http://localhost:${LANDING_PORT}/"
info "  业务端      http://localhost:${WEB_PORT}/"
info "  管理后台    http://localhost:${ADMIN_PORT}/"
if is_our_backend; then
  info "  接口文档    http://localhost:${BACKEND_PORT}/api/swagger-ui.html"
fi
info ""
info "  演示账号（密码统一 123456）"
info "    农户     13800000002     采购方   13800000005"
info "    专家     13800000007     银行用户 13800000009"
info "    管理员   13800000001"
info ""
info "  日志目录    ${LOG_DIR}"
info "  ${C_WARN}Ctrl-C 退出会一并关闭本次拉起的进程${C_OFF}"
info "${C_OK}═══════════════════════════════${C_OFF}"
printf '\n'

if [ "${OPEN_BROWSER}" = 1 ]; then
  open "http://localhost:${LANDING_PORT}/" 2>/dev/null || warn "打不开浏览器，手动访问上面的地址"
fi

# 挂住等 Ctrl-C。
#
# ⚠️ 这里必须用 wait，不能写成 `while true; do sleep 3600; done`：
# bash 收到信号后，要等「当前正在执行的那条前台命令」结束才会去跑 trap，
# 而 sleep 3600 要等一小时才结束 —— 实际表现是 Ctrl-C 之后脚本卡住不退出、
# 端口一直占着（看起来像已经关了，其实进程还活着，下次再跑就报端口冲突，
# 而且查不出是谁占的）。wait 是可被信号打断的，信号一到立刻返回，trap 随即执行。
wait

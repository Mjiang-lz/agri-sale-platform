#!/bin/bash
# 把本机正在跑的后端，通过临时公网隧道共享给队友。
#
# ── 解决什么问题 ────────────────────────────────────────────────
# 队友不在同一局域网（访问不到你的 192.168.x.x），但需要看到**和你完全一致的
# 图片与数据**。做法不是「让队友同步你的图片」，而是「让队友直接连你的后端」：
# 只有一份后端、一份数据库，所以你上传什么、他们就看到什么。
#   · 队友不需要配 OBS、不需要建库、不需要跑 data.sql
#   · 也不需要任何「同步」动作 —— 本项目本来就没有同步机制
#     （`rxt.obs.enabled` 是互斥开关，只影响之后新上传的文件落哪边）
#
# ── 为什么隧道共享后端，而不是共享 MySQL ────────────────────────
# 暴露 3306 要改 MySQL 监听地址 + 建远程账号 + 开防火墙，数据库裸奔在公网，
# 而且队友仍要自己跑后端、自己配 OBS（否则他上传的图会写成 /api/files/... 只落
# 他自己磁盘，共享库也救不了）。暴露后端只有一个 HTTP 端口，队友侧改动只有一行。
#
# ── 用法 ────────────────────────────────────────────────────────
#   bash scripts/share-backend.sh                  # 共享本机 18080
#   BACKEND_PORT=8080 bash scripts/share-backend.sh
#   Ctrl+C 结束共享（隧道随之关闭，不会残留进程）
#
# ── 前提：后端必须用随机 JWT 密钥运行 ──────────────────────────
# 仓库里那个 dev 默认密钥是**公开的**。后端一旦上公网，知道仓库的人就能用
# 它伪造任意用户（含管理员）的登录态。所以：
#     export RXT_JWT_SECRET=$(openssl rand -base64 48)
#     BACKEND_PORT=18080 bash scripts/demo-start.sh
# 本脚本第 2 步会**自己伪造一个 token 去验证**这件事，不通过就直接拒绝共享
# （用的是肯定式判据：伪造 token 打登录态接口能过，说明密钥没换）。
#
# ── 已知限制（说清楚比藏着好）────────────────────────────────
#   · 免费快速隧道**每次启动域名都会变**，重启后要把新地址重新发给队友。
#     要固定域名得改用命名隧道（需要 Cloudflare 账号 + 自己的域名），本脚本不做。
#   · 你这台机器必须常开，关机即断。
#   · 公网可达 = 全世界可达。演示数据是假的，但别把它当生产环境。
#   · 队友的浏览器只经由他自己本地的 vite 代理访问你的后端，所以不涉及跨域；
#     图片是 OBS 绝对地址，由浏览器直连对象存储，同样不经过隧道。

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${BACKEND_PORT:-18080}"
BASE="http://localhost:${PORT}"
CF_DIR="$HOME/.rxt/bin"
CF_BIN="$CF_DIR/cloudflared"
LOG="/tmp/rxt-share-tunnel.log"

# 与 application.yml 里 rxt.jwt.secret 的默认值必须逐字一致。
DEFAULT_SECRET='rxt-platform-dev-secret-key-please-change-in-production'

C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_DIM=$'\033[2m'; C_OFF=$'\033[0m'

step() { printf '\n%s==> %s%s\n' "$C_DIM" "$1" "$C_OFF"; }
ok()   { printf '%s  ✓ %s%s\n' "$C_OK" "$1" "$C_OFF"; }
warn() { printf '%s  ! %s%s\n' "$C_WARN" "$1" "$C_OFF"; }
die()  { printf '%s  ✗ %s%s\n' "$C_ERR" "$1" "$C_OFF"; exit 1; }

# ────────────────────────────────────────────────────────────────
step "0/5 确认本机后端在跑"
# ────────────────────────────────────────────────────────────────
CODE=$(curl --noproxy '*' -s -m 8 -o /dev/null -w '%{http_code}' "$BASE/api/trade/products?pageNum=1&pageSize=1" || true)
[ "$CODE" = "200" ] || die "${BASE} 上没有后端在响应（HTTP ${CODE:-无}）。先起后端：BACKEND_PORT=${PORT} bash scripts/demo-start.sh"
ok "后端在 ${BASE}"

# ────────────────────────────────────────────────────────────────
step "1/5 安全闸：后端用的是不是版本库里那个公开的默认 JWT 密钥"
# ────────────────────────────────────────────────────────────────
# 判据是肯定式的：用默认密钥签一个管理员 token，打需要登录的 /api/user/profile。
#   返回非 401 ⇒ 签名被接受 ⇒ 密钥就是公开那个 ⇒ 绝不能挂公网。
#   返回 401   ⇒ 签名被拒 ⇒ 已经换过密钥，可以共享。
# 这条探针本身做过反向验证（把密钥改一个字符必须变 401），不是恒真的假判据。

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

forge_token() {
  local secret="$1" h p sig now exp
  h=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64url)
  now=$(date +%s); exp=$((now + 3600))
  p=$(printf '{"sub":"1","phone":"13800000001","roleName":"管理员","iat":%s,"exp":%s}' \
        "$now" "$exp" | b64url)
  sig=$(printf '%s' "$h.$p" | openssl dgst -sha256 -hmac "$secret" -binary | b64url)
  printf '%s.%s.%s' "$h" "$p" "$sig"
}

probe_code() {
  local secret="$1"
  curl --noproxy '*' -s -m 10 \
    -H "Authorization: Bearer $(forge_token "$secret")" \
    "$BASE/api/user/profile" 2>/dev/null \
    | /usr/bin/grep -o '"code":[0-9]*' | head -1 | /usr/bin/grep -o '[0-9]*'
}

GATE=$(probe_code "$DEFAULT_SECRET")
if [ "${GATE:-}" != "401" ]; then
  printf '\n'
  die "后端正在使用版本库里的公开默认密钥（伪造 token 被接受，code=${GATE:-无}）。
     现在挂公网 = 任何人都能伪造管理员登录态。
     先换随机密钥并重启后端，再跑本脚本：

       export RXT_JWT_SECRET=\$(openssl rand -base64 48)
       BACKEND_PORT=${PORT} bash scripts/demo-start.sh

     （换密钥会让所有人的登录态失效，重新登录即可。）"
fi
ok "伪造 token 被拒（401）—— 后端已用非默认密钥，可以暴露"

# ────────────────────────────────────────────────────────────────
step "2/5 准备 cloudflared"
# ────────────────────────────────────────────────────────────────
if [ ! -x "$CF_BIN" ] && command -v cloudflared >/dev/null 2>&1; then
  CF_BIN="$(command -v cloudflared)"
fi

if [ ! -x "$CF_BIN" ]; then
  OS=$(uname -s | tr '[:upper:]' '[:lower:]')
  MACH=$(uname -m)
  case "$MACH" in
    arm64|aarch64) A=arm64 ;;
    x86_64|amd64)  A=amd64 ;;
    *) die "未识别的 CPU 架构：$MACH" ;;
  esac
  case "$OS" in
    darwin|linux) ;;
    *) die "未识别的系统：$OS（本脚本只在 macOS / Linux 上验证过）" ;;
  esac
  PKG="cloudflared-${OS}-${A}.tgz"
  mkdir -p "$CF_DIR"
  printf '  %s正在下载 %s（约 20MB，只下这一次）...%s\n' "$C_DIM" "$PKG" "$C_OFF"
  curl -sL -m 180 -o "/tmp/$PKG" \
    "https://github.com/cloudflare/cloudflared/releases/latest/download/$PKG" \
    || die "下载失败。可手动安装后重跑（brew install cloudflared 也行，脚本会优先用 PATH 里的）"
  tar -xzf "/tmp/$PKG" -C "$CF_DIR" cloudflared || die "解包失败"
  rm -f "/tmp/$PKG"
  chmod +x "$CF_BIN"
fi
ok "cloudflared $("$CF_BIN" --version 2>&1 | head -1 | sed 's/^cloudflared version //')"

# ────────────────────────────────────────────────────────────────
step "3/5 起隧道"
# ────────────────────────────────────────────────────────────────
rm -f "$LOG"
"$CF_BIN" tunnel --no-autoupdate --url "$BASE" >"$LOG" 2>&1 &
CFPID=$!
# 退出时收干净：这个脚本 Ctrl+C 之后不该留下隧道进程在后台跑。
trap 'printf "\n%s正在关闭隧道...%s\n" "$C_DIM" "$C_OFF"; kill "$CFPID" 2>/dev/null; sleep 1; kill -0 "$CFPID" 2>/dev/null && kill -9 "$CFPID" 2>/dev/null; echo "已关闭。"' EXIT INT TERM

PUB=""
for _ in $(seq 1 30); do
  sleep 2
  PUB=$(/usr/bin/grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG" 2>/dev/null | head -1)
  [ -n "$PUB" ] && break
done
[ -n "$PUB" ] || { printf '\n--- 隧道日志 ---\n'; tail -20 "$LOG"; die "30 次轮询没拿到公网地址，见上方日志"; }
ok "公网地址 ${C_OK}${PUB}${C_OFF}"

# ────────────────────────────────────────────────────────────────
step "4/5 从公网地址自检（走完整公网链路，确认隧道真的通到后端）"
# ────────────────────────────────────────────────────────────────
# 刚拿到地址就请求会撞 Cloudflare 的 530（连接还没注册到边缘），实测要等十几秒。
printf '  %s预热 15s（快速隧道注册到边缘需要时间，早请求会拿到 530）...%s\n' "$C_DIM" "$C_OFF"
sleep 15

CHECK_OK=0
for n in 1 2 3 4 5; do
  RESP=$(curl --noproxy '*' -s -m 25 "${PUB}/api/trade/products?pageNum=1&pageSize=1" 2>/dev/null)
  if printf '%s' "$RESP" | /usr/bin/grep -q '"code":200'; then CHECK_OK=1; break; fi
  printf '  %s第 %s 次未通，重试...%s\n' "$C_DIM" "$n" "$C_OFF"
  sleep 6
done
[ "$CHECK_OK" = "1" ] || die "${PUB} 通了地址但拉不到数据。多半是隧道没连上边缘（看 ${LOG}），或网络环境屏蔽了 Cloudflare。"
ok "公网侧能取到真实数据（code:200）"

# ────────────────────────────────────────────────────────────────
step "5/5 把下面这两行发给队友"
# ────────────────────────────────────────────────────────────────
cat <<EOF

  ${C_DIM}队友那边只需要：先 git pull 到你这次的提交，然后在对应前端目录里带上这一行。${C_OFF}

  业务端 / 管理后台：
    cd frontend-web   && NODE_OPTIONS= VITE_API_TARGET=${PUB} npm run dev
    cd frontend-admin && NODE_OPTIONS= VITE_API_TARGET=${PUB} npm run dev

  落地页（它另外需要知道业务端在哪，指向队友**本机**的 5173）：
    cd frontend-landing && NODE_OPTIONS= VITE_API_TARGET=${PUB} \\
        VITE_PLATFORM_BASE_URL=http://localhost:5173 npm run dev

  ${C_DIM}因为只有一份后端、一份库，你上传的图队友立刻就能看到；
  OBS 由你这一台机器负责，队友不需要配 OBS、不需要建库、不需要跑 data.sql。${C_OFF}

  ${C_WARN}注意：免费隧道的域名每次重启都会变，重启后要把新地址重新发一遍。${C_OFF}
  ${C_WARN}这台机器要一直开着；Ctrl+C 即停止共享。${C_OFF}

EOF

printf '%s共享进行中，Ctrl+C 结束...%s\n' "$C_DIM" "$C_OFF"
wait "$CFPID"

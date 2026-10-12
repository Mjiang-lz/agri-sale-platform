#!/bin/zsh
# 把华为云 OBS 的 AK/SK 写进本机私有配置，并打开 OBS 开关。
#
# 为什么用脚本而不是手改文件：
#   这两串是账号级凭据，不该出现在聊天记录、截图或终端回显里；
#   而且都是长随机串，手抄极易错位。这里用 `read -s` 静默读入，
#   只写进已被 .gitignore 忽略的 application-local.yml，全程不回显值。
#
# 用法：
#   开启：  bash scripts/setup-obs-local.sh
#   关闭：  bash scripts/setup-obs-local.sh --off
#
# 写入后需重启后端才生效（application.yml 已 `include: local`）。

set -u

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGET="$REPO_ROOT/backend/src/main/resources/application-local.yml"

if [ "${1:-}" = "--off" ]; then
  umask 077
  {
    printf '# 本机私有配置（已被 .gitignore 忽略，不会被提交）\n'
    printf '# OBS 已关闭 —— 文件存储走本地磁盘 backend/uploads\n'
    printf '# 重新开启：bash scripts/setup-obs-local.sh\n\n'
    printf 'rxt:\n  obs:\n    enabled: false\n'
  } > "$TARGET"
  chmod 600 "$TARGET"
  echo "✅ 已关闭 OBS，文件存储回到本地磁盘。重启后端后生效。"
  echo "   ★ 注意：本地磁盘上的老文件会重新可达，但 OBS 上的新文件将不再被 /api/files 提供。"
  exit 0
fi

echo "请从华为云控制台「我的凭证 → 访问密钥」复制对应值粘贴进来。"
echo "（输入时终端不会显示字符，这是正常的；粘贴完直接回车）"
echo
printf 'Access Key (AK): '
read -r -s AK
echo
printf 'Secret Key (SK): '
read -r -s SK
echo
echo

if [ -z "$AK" ] || [ -z "$SK" ]; then
  echo "❌ AK / SK 不能为空，已中止，什么都没改。"
  exit 1
fi
case "$AK$SK" in
  *[[:space:]]*) echo "❌ 输入里含空格或换行，可能粘贴时多带了字符，已中止，什么都没改。"; exit 1 ;;
esac

umask 077
{
  printf '# 本机私有配置（已被 .gitignore 忽略，不会被提交）\n'
  printf '# 由 scripts/setup-obs-local.sh 生成于 %s\n' "$(date '+%Y-%m-%d %H:%M:%S')"
  printf '# 关闭 OBS：bash scripts/setup-obs-local.sh --off\n'
  printf '#\n'
  printf '# 这个文件只对本机生效：application.yml 里 `include: local` 是追加语义，\n'
  printf '# 文件不存在时 Spring 只是找不到配置、不报错，所以队友 clone 下来仍走本地磁盘。\n\n'
  printf 'rxt:\n  obs:\n    enabled: true\n'
  printf '    access-key: "%s"\n' "$AK"
  printf '    secret-key: "%s"\n' "$SK"
} > "$TARGET"
chmod 600 "$TARGET"

# 只报长度，不回显内容；顺便当一次「粘全了没」的体检
echo "✅ 已写入 $TARGET"
echo "   AK 长度 ${#AK} 位，SK 长度 ${#SK} 位"
echo "   权限已设为 600（仅本人可读）"
echo
echo "下一步：重启后端。启动日志里应出现"
echo "   「OBS 模式已启用，endpoint=obs.cn-north-4.myhuaweicloud.com，bucket=rxt-oss」"
echo "若出现「本地磁盘模式已启用」，说明这个文件没被读到，把这条日志发我。"
echo
echo "⚠️ 切到 OBS 后，此前存在本地磁盘的老文件（如旧头像）将不再可达，"
echo "   需要重新上传一次。"

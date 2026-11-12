#!/usr/bin/env bash
#
# 生成「朝夕」自己的发布签名密钥。
#
# 密码由你在终端输入，只进 keystore 文件，不落任何仓库文件、不进 shell 历史。
# 用法：
#   ./scripts/create-keystore.sh                 # 默认 ~/.morrow/morrow-release.jks
#   ./scripts/create-keystore.sh /path/to/x.jks  # 指定位置
#
set -euo pipefail

OUT="${1:-$HOME/.morrow/morrow-release.jks}"
ALIAS="${MORROW_KEY_ALIAS:-morrow}"

if command -v keytool >/dev/null 2>&1; then
    KT="keytool"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    KT="$JAVA_HOME/bin/keytool"
else
    echo "找不到 keytool。请先安装 JDK 21，或 export JAVA_HOME 指向 JDK 目录。" >&2
    echo "本仓库的工具链示例：export JAVA_HOME=\"$(cd "$(dirname "$0")/../../" && pwd)/.toolchain/jdk/jdk-21.0.13+9\"" >&2
    exit 1
fi

if [ -e "$OUT" ]; then
    echo "已存在：$OUT" >&2
    echo "同一路径重新生成会覆盖旧密钥，已发布版本将无法再覆盖升级。" >&2
    echo "确实要换，请换一个路径，或先自行备份并删除旧文件。" >&2
    exit 1
fi

read -rsp "设置 keystore 密码（至少 6 位）: " PASS; echo
read -rsp "再输入一次: " PASS2; echo
[ "$PASS" = "$PASS2" ] || { echo "两次输入不一致。" >&2; exit 1; }
[ "${#PASS}" -ge 6 ] || { echo "密码太短，keytool 要求至少 6 位。" >&2; exit 1; }

mkdir -p "$(dirname "$OUT")"
"$KT" -genkeypair \
    -keystore "$OUT" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 10000 \
    -storepass "$PASS" \
    -keypass "$PASS" \
    -dname "CN=Chaoxi, OU=Mobile, O=MorrowViento, C=CN"

chmod 600 "$OUT" 2>/dev/null || true

echo
echo "已生成：$OUT"
echo "别名：  $ALIAS"
echo
echo "把下面四行追加到 ~/.gradle/gradle.properties（该文件在用户目录，不会进仓库）："
echo
echo "morrow.keystore=$OUT"
echo "morrow.storePassword=<你刚才输入的密码>"
echo "morrow.keyAlias=$ALIAS"
echo "morrow.keyPassword=<你刚才输入的密码>"
echo
echo "然后构建：JAVA_HOME=... gradle :app:assembleRelease"
echo "详见 docs/SIGNING.md"

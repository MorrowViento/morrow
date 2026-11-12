#!/usr/bin/env bash
# 朝夕 · 一键发版
#
# 把「改版本号 → 构建 → 打标签 → 建 Release → 传 APK → 回填 update/manifest.json」
# 这条链路固化成一条命令。这些步骤在 2026-11-12 首次发版时全是手工 curl，
# 代理抖动、摘要抄错、清单漏填都出过一次，所以脚本里每一项都做了校验。
#
# 用法：
#   scripts/release.sh                          # 用 gradle 里的版本号发当前版本
#   scripts/release.sh --notes notes.md         # 指定发布说明（Markdown）
#   scripts/release.sh --prerelease             # 标为预发布（见下方警告）
#   scripts/release.sh --dry-run                # 只校验与打印计划，不构建、不发请求
#   scripts/release.sh --skip-manifest          # 不回填/提交 update/manifest.json
#
# 认证：优先读 GITHUB_TOKEN；没有就用 git credential fill 取本机已存的 GitHub 凭据。
# 签名：配了 MORROW_KEYSTORE（或 ~/.gradle/gradle.properties 的 morrow.keystore）就出
#       release 包，否则出 debug 包——两者签名不同，不能互相覆盖安装。
#
# ⚠️ 默认发布为**正式** Release：应用走 /releases/latest，该接口不返回预发布版本，
#    标成 prerelease 手机就永远检测不到更新。--prerelease 只在明确不想让用户升级时用。
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

GRADLE_BIN="${GRADLE_BIN:-../.toolchain/gradle-9.6.0/bin/gradle}"
JAVA_HOME_DIR="${JAVA_HOME:-$ROOT/../.toolchain/jdk/jdk-21.0.13+9}"
NOTES_FILE=""
PRERELEASE=false
DRY_RUN=false
SKIP_MANIFEST=false
DIST_DIR="$(cd "$ROOT/.." && pwd)"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --notes) NOTES_FILE="${2:?--notes 需要文件路径}"; shift 2 ;;
        --prerelease) PRERELEASE=true; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        --skip-manifest) SKIP_MANIFEST=true; shift ;;
        --dist) DIST_DIR="${2:?--dist 需要目录}"; shift 2 ;;
        -h|--help) sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "未知参数：$1（--help 看用法）" >&2; exit 2 ;;
    esac
done

say()  { printf '\033[36m▸\033[0m %s\n' "$*"; }
warn() { printf '\033[33m!\033[0m %s\n' "$*"; }
die()  { printf '\033[31m✗\033[0m %s\n' "$*" >&2; exit 1; }

# ---- 1. 版本号：以 gradle 为唯一事实来源 --------------------------------------
CODE="$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | head -1 | grep -oE '[0-9]+')"
NAME="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | sed -E 's/.*"(.*)"/\1/')"
[[ -n "$CODE" && -n "$NAME" ]] || die "读不到 versionCode / versionName，检查 app/build.gradle.kts"
TAG="v$NAME-$CODE"

# ---- 2. 远端与身份 ----------------------------------------------------------
REMOTE_URL="$(git config --get remote.origin.url)"
SLUG="$(printf '%s' "$REMOTE_URL" | sed -E 's#.*github\.com[:/]##; s#\.git$##')"
OWNER="${SLUG%%/*}"
REPO="${SLUG##*/}"
[[ -n "$OWNER" && -n "$REPO" ]] || die "解析不出 GitHub owner/repo：$REMOTE_URL"

say "发版目标：$OWNER/$REPO  tag=$TAG  code=$CODE"

# ---- 3. 工作区与标签校验 ----------------------------------------------------
DIRTY="$(git status --porcelain | grep -v '^ M docs/MANUAL-TEST.md' || true)"
if [[ -n "$DIRTY" ]]; then
    printf '%s\n' "$DIRTY" >&2
    die "工作区有未提交改动（docs/MANUAL-TEST.md 除外），先提交再发版"
fi
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    die "本地已存在标签 $TAG：发新版本请先把 versionCode / versionName 改大"
fi
if [[ -n "$(git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null || true)" ]]; then
    die "远端已存在标签 $TAG：同一版本不能重复发布"
fi

# ---- 4. 选签名与产物名 ------------------------------------------------------
HAS_KEYSTORE=false
if [[ -n "${MORROW_KEYSTORE:-}" ]] || grep -qE '^morrow\.keystore=' "$HOME/.gradle/gradle.properties" 2>/dev/null; then
    HAS_KEYSTORE=true
fi
VARIANT="debug"
[[ "$HAS_KEYSTORE" == true ]] && VARIANT="release"
ASSET="morrow-$CODE-$NAME-$VARIANT.apk"

if [[ "$PRERELEASE" == true ]]; then
    warn "--prerelease：/releases/latest 不返回预发布，应用内更新检测不到这一版"
fi
say "产物：$ASSET（$VARIANT 签名）"

if [[ "$DRY_RUN" == true ]]; then
    say "dry-run：跳过构建与网络请求"
    echo "  将执行：JAVA_HOME=$JAVA_HOME_DIR $GRADLE_BIN :app:assemble${VARIANT^}"
    echo "  将创建 Release：$TAG（prerelease=$PRERELEASE）"
    echo "  将上传资产：$DIST_DIR/$ASSET"
    [[ "$SKIP_MANIFEST" == false ]] && echo "  将回填 update/manifest.json 并提交推送"
    exit 0
fi

# ---- 5. 构建 ----------------------------------------------------------------
say "构建中（${VARIANT}）…"
JAVA_HOME="$JAVA_HOME_DIR" "$GRADLE_BIN" ":app:assemble${VARIANT^}" --offline --console=plain \
    | tail -5
APK="app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"
[[ -f "$APK" ]] || die "构建没有产出 $APK"

mkdir -p "$DIST_DIR"
cp "$APK" "$DIST_DIR/$ASSET"
BYTES="$(wc -c < "$DIST_DIR/$ASSET" | tr -d ' ')"
SHA="$(sha256sum "$DIST_DIR/$ASSET" | cut -d' ' -f1)"
say "已产出 $DIST_DIR/$ASSET（$BYTES B）"
say "sha256 $SHA"

# ---- 6. 令牌 ----------------------------------------------------------------
TOKEN="${GITHUB_TOKEN:-}"
if [[ -z "$TOKEN" ]]; then
    TOKEN="$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p')"
fi
[[ -n "$TOKEN" ]] || die "拿不到 GitHub 令牌：设置 GITHUB_TOKEN，或让 git 凭据里存好 github.com 的凭据"

API="https://api.github.com/repos/$OWNER/$REPO"
UA="Morrow-Release-Script"
api() { curl -sS --max-time 60 -H "Authorization: Bearer $TOKEN" \
        -H "Accept: application/vnd.github+json" -H "User-Agent: $UA" "$@"; }

# ---- 7. 发布说明 ------------------------------------------------------------
if [[ -n "$NOTES_FILE" ]]; then
    [[ -f "$NOTES_FILE" ]] || die "找不到说明文件：$NOTES_FILE"
else
    NOTES_FILE="$(mktemp)"
    {
        echo "## 朝夕 $NAME ($CODE)"
        echo
        echo "\`versionCode=$CODE\` · \`$VARIANT\` 签名"
        echo
        echo "### 本次提交"
        echo
        PREV_TAG="$(git tag --sort=-creatordate | head -1 || true)"
        if [[ -n "$PREV_TAG" ]]; then
            git log --no-merges --pretty='- %s' "$PREV_TAG..HEAD" || true
        else
            git log --no-merges -12 --pretty='- %s' || true
        fi
        echo
        echo "### 校验"
        echo
        echo "- SHA-256：\`$SHA\`"
        echo "- 大小：$BYTES B"
    } > "$NOTES_FILE"
fi

# ---- 8. 建 Release ----------------------------------------------------------
PAYLOAD="$(mktemp)"
python - "$TAG" "$NAME" "$CODE" "$NOTES_FILE" "$PRERELEASE" > "$PAYLOAD" <<'PY'
import json, sys
tag, name, code, notes_path, prerelease = sys.argv[1:6]
body = open(notes_path, encoding="utf-8").read()
json.dump({
    "tag_name": tag,
    "target_commitish": "main",
    "name": f"朝夕 {name} ({code})",
    "body": body,
    "draft": False,
    "prerelease": prerelease.lower() == "true",
}, sys.stdout, ensure_ascii=False)
PY

say "创建 Release $TAG …"
RESP="$(api -X POST "$API/releases" -H "Content-Type: application/json; charset=utf-8" \
    --data-binary "@$PAYLOAD")"
REL_ID="$(printf '%s' "$RESP" | python -c 'import sys,json;print(json.load(sys.stdin).get("id",""))' 2>/dev/null || true)"
[[ -n "$REL_ID" ]] || { printf '%s\n' "$RESP" | head -c 500; die "创建 Release 失败"; }
say "Release id=$REL_ID"

# ---- 9. 传资产（代理抖动是常态，重试几轮） ----------------------------------
say "上传资产（$BYTES B）…"
for attempt in 1 2 3; do
    HTTP="$(curl -sS --retry 2 --retry-delay 5 --max-time 900 \
        -o "$DIST_DIR/.upload.json" -w '%{http_code}' \
        -X POST "https://uploads.github.com/repos/$OWNER/$REPO/releases/$REL_ID/assets?name=$ASSET" \
        -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
        -H "User-Agent: $UA" -H "Content-Type: application/vnd.android.package-archive" \
        --data-binary "@$DIST_DIR/$ASSET" || true)"
    [[ "$HTTP" == "201" ]] && break
    warn "上传第 $attempt 次失败（HTTP ${HTTP:-000}），重试…"
    sleep 5
done
[[ "${HTTP:-}" == "201" ]] || { head -c 400 "$DIST_DIR/.upload.json" 2>/dev/null; die "上传失败"; }
rm -f "$DIST_DIR/.upload.json"

# ---- 10. 回读校验 -----------------------------------------------------------
# 走临时文件而不是 argv：发布说明可能有几 KB，塞进参数会被系统长度限制截断
LATEST_FILE="$(mktemp)"
api "$API/releases/latest" > "$LATEST_FILE"
python - "$LATEST_FILE" "$TAG" "$ASSET" "$SHA" <<'PY'
import json, sys
latest_path, tag, asset, sha = sys.argv[1:5]
with open(latest_path, encoding="utf-8") as f:
    d = json.load(f)
if d.get("tag_name") != tag:
    sys.exit(f"/releases/latest 返回的是 {d.get('tag_name')}，不是 {tag}"
             "（多半是标成了 prerelease）")
names = [a["name"] for a in d.get("assets", [])]
if asset not in names:
    sys.exit(f"Release 上找不到资产 {asset}，实际有：{names}")
digest = (next(a for a in d["assets"] if a["name"] == asset).get("digest") or "")
if digest and digest.split(":")[-1].lower() != sha.lower():
    sys.exit(f"资产摘要不一致：远端 {digest} / 本地 {sha}")
print(f"✓ /releases/latest = {tag}，资产 {asset} 摘要一致")
PY

# ---- 11. 回填清单 -----------------------------------------------------------
if [[ "$SKIP_MANIFEST" == true ]]; then
    warn "--skip-manifest：跳过 update/manifest.json"
else
    say "回填 update/manifest.json …"
    DOWNLOAD="https://github.com/$OWNER/$REPO/releases/download/$TAG/$ASSET"
    python - "$LATEST_FILE" "$ASSET" "$BYTES" "$SHA" "$DOWNLOAD" <<'PY'
import json, sys
latest_path, asset, size, sha, url = sys.argv[1:6]
with open(latest_path, encoding="utf-8") as f:
    d = json.load(f)
manifest = {
    "tag_name": d["tag_name"],
    "name": d["name"],
    "body": (d.get("body") or "")[:500],
    "published_at": d["published_at"],
    "prerelease": d["prerelease"],
    "assets": [{
        "name": asset,
        "browser_download_url": url,
        "size": int(size),
        "digest": "sha256:" + sha,
    }],
}
with open("update/manifest.json", "w", encoding="utf-8", newline="\n") as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY
    git add update/manifest.json
    git commit -q -m "chore: 回填更新清单到 $TAG"
    git push origin main >/dev/null 2>&1 || warn "推送失败（代理抖动？），稍后手动 git push origin main"
fi

echo
say "发布完成：https://github.com/$OWNER/$REPO/releases/tag/$TAG"
echo "  资产：$ASSET（$BYTES B）"
echo "  sha256：$SHA"
echo "  本地副本：$DIST_DIR/$ASSET"

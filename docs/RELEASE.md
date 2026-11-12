# 发版流程（RELEASE）

> 首次发版（v4.0.0-alpha2-41）是全手工 curl 做的：建 Release、传 76 MB 资产、算摘要、
> 回填 `update/manifest.json`，中间踩过代理 502 与摘要抄错。这里的脚本把这条链路固化，
> 一条命令完成，并把每一处易错点做成硬校验。

## 一条命令

```bash
# 0) 先改版本号（重要：应用内更新只比 versionCode）
#    app/build.gradle.kts: versionCode += 1，versionName 同步
# 1) 提交干净后发版
cd morrow
./scripts/release.sh --notes ../release-notes.md     # --notes 可省，省了自动用 git log 生成

# 只看计划、不构建不发请求
./scripts/release.sh --dry-run
```

脚本做的事：

1. 从 `app/build.gradle.kts` 读 `versionCode` / `versionName`，拼出 `v<name>-<code>`；
2. 校验工作区干净（`docs/MANUAL-TEST.md` 例外，它是本地验收记录）、本地与远端都没有同名标签；
3. 有 keystore 出 `release` 包，没有出 `debug` 包；
4. 构建 → 复制成 `morrow-<code>-<name>-<variant>.apk` → 算 B 与 SHA-256；
5. 建 Release → 上传资产（失败重试 3 轮，代理抖动是常态）→ 回读 `/releases/latest` 核对标签、
   资产名与摘要；
6. 回填 `update/manifest.json`，提交并推送。

## 三条硬规则

1. **资产名必须是 `morrow-<code>-…`**。客户端 `UpdatePolicy` 从资产名里解析 versionCode
   （正则 `(?:^|[-_])morrow[-_](\d{1,9})`），名字不合规就取不到版本号，会被判成「没有更新」。
2. **不能标成 prerelease**。应用走 `GET /releases/latest`，该接口**不返回**预发布版本；
   标了 prerelease，手机上永远显示「已是最新版本」。脚本默认 `prerelease=false`，
   传 `--prerelease` 时也会警告。
3. **`update/manifest.json` 必须回填**。它是 `api.github.com` 不可达时 jsDelivr / raw
   两路的回退数据；字段形状与 Releases API 一致，客户端复用同一个解析器。
   脚本用 `--skip-manifest` 可以跳过，但那样只剩一条检查路径。

## 签名

- 未配密钥 → 出 **debug** 包。只能覆盖旧的 debug 安装，用户换正式包必须先卸载。
- 配了密钥（`MORROW_KEYSTORE` 或 `~/.gradle/gradle.properties` 的 `morrow.keystore`）→ 出
  **release** 包（开启 R8，体积从 76 MB 降到十几 MB）。生成与注入见 `docs/SIGNING.md`。

## 手工兜底

脚本卡在某一步时，可以只做后半段：

```bash
# 已经建好 Release，只是资产没传上去
TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | sed -n 's/^password=//p')
curl -X POST "https://uploads.github.com/repos/MorrowViento/morrow/releases/<ID>/assets?name=<ASSET>" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @<ASSET>

# 顺带一提：带中文的 description 调 PATCH /repos 会 400，只传 name 改名才行
```

## 撤回

- Release 建错（版本号重复、资产不对）：网页端删掉该 Release 与对应 tag，再重新发一版**更高**
  的 versionCode。不要复用同一个版本号——已装该版本的用户不会再次收到提示。
- 发现严重问题需要止损：把 Release 转为 draft（应用立刻检测不到），而不是只删资产。

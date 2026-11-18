# RELEASE — 正式发布流程

> 更新：2026-11-18。只有涉及发布才读本文件；当前任务看 HANDOFF / TODO。脚本会写远端与推送，文档更新不等于发布授权。

## 当前发布基线与前提

- 最新正式版 **`v4.1.2`**（Release「朝夕 4.1.2」，id `449651404`，`published_at` 2026-11-18T07:32:07Z），资产 `morrow-71-4.1.2-release.apk`，**5,199,013 B**，SHA-256 `601b3beb4db1c20f9cf94f31ff933e79295c917608d499d577df032f4f8673a6`；本地 `update/manifest.json` 已按 `/releases/latest` 真实数据回填，标签指向 `f9da8d8`（= main）。
  - 上一版 `v4.1.1`（`449587411`，70 包 5,196,689 B / `7F92400A…D963`）与 `v4.0.0-68`（5,028,400 B / `2dcc4921…bb8f`）均保持不动，未被覆盖。
  - 4.1.1 起 tag 与标题按用户指定不带 code（`v4.1.2` /「朝夕 4.1.2」），与 `v4.0.0-68` 的旧命名不同；资产名仍必须是 `morrow-<code>-<name>-release.apk`，客户端从资产名取 code。全程手工 API 发布（未改 `scripts/release.sh`，其默认仍生成 `v<name>-<code>`）；发布路径为 draft → 上传 → 回下载比对 → 转正式。
- 发布前已确认：380 项单测通过、lint 0 errors / 15 warnings、R8 + 资源裁剪实际执行、证书 SHA-256 与正式 68 一致（`8894605E…8D02`）、**8 份**许可资产与源一致（新增 `protobuf-bsd-3.txt` 静态归属）、图像条目 0 且资源表无 Apple 素材、`verifyRuntimeNoticeCompleteness` 退出 0。真机与 68→71 保数据升级仍待用户验收。
- 沿用 4.0.0 的项目自有发布密钥，步骤见 [SIGNING](SIGNING.md)。**正式更新禁止退回 debug 签名**；脚本缺密钥会自动选 debug，必须在执行前人工确认 variant=release。
- 4.1.0 的 AL 真机验收、核心回归与 N 更新链路尚待结果；按 TODO 确认未解决缺陷和发布结论。
- 脚本固定 `target_commitish=main`，且最后推送 `origin main`：待发代码必须先进入并同步 main，不能在功能分支上直接发而让标签指向别的代码。
- 发布、清单提交/推送、撤回均是共享状态操作，执行前确认已有对应授权。`MANUAL-TEST.md` 的提交仍由用户逐次决定。

## 执行顺序

1. 查远端最新 Release / 标签与当前代码。应用只比较 `versionCode`；69 未发布时可用当前 69，已被使用则递增，不复用已发布 code，也不因「准备发版」就无条件再加一。
2. 重跑 `assembleDebug + testDebugUnitTest + lintDebug`，完成真机结论；签名 release 另行构建并验证。脚本**只构建所选 APK，不自动跑单测/lint/真机验收**。
3. 确认原密钥、包名、versionCode/versionName、R8 产物、证书 SHA-256 与基线一致。密钥/密码不打印、不放仓库。
4. 获授权后提交并同步待发代码；工作区除本地未暂存的 `MANUAL-TEST.md` 外应干净。不要 `git add -A` 带走用户验收记录。
5. 在支持的 Bash 环境从仓库根执行计划检查，再正式发布。Windows PowerShell 不是 Bash；工具模式不允许启动另一 shell 时，不通过 PowerShell 启动 Bash 绕过限制。

```bash
# 从仓库根运行；默认使用上级 .toolchain 的 JDK 21 / Gradle 9.6.0
./scripts/release.sh --dry-run
# 发布前必须确认计划里的 variant 为 release
./scripts/release.sh --notes ../release-notes.md
# --notes 可省，省略时脚本使用最近标签以来的 git log
```

Windows 本地 debug 验证命令见 HANDOFF，release 构建和 apksigner 命令见 SIGNING。
`--dry-run` 会读取工作区/本地标签并打印计划，不构建、不发网络请求；**不证明远端标签未占用，也不证明签名配置有效**。

## 脚本实际行为

`scripts/release.sh`：

1. 从 `app/build.gradle.kts` 读版本，生成 `v<name>-<code>`；从 Git remote 解析仓库。
2. 检查工作区、本地标签；按密钥配置选择 debug/release。仅忽略未暂存的 `docs/MANUAL-TEST.md` 修改，它不是通用脏工作区豁免。
3. 非 dry-run 时读取认证（环境变量或 Git 凭据），通过 API 检查同名 Release；网络失败就退出。该检查不替代人工核对独立远端 Git tag。
4. 使用本地 JDK/Gradle **offline** 构建所选 variant；复制到仓库上级（或 `--dist`），计算真实大小与 SHA-256。
5. 基于远端 main 创建正式 Release、上传 APK（失败最多三轮），回读 `/releases/latest` 核对标签、资产与摘要。
6. 回填 `update/manifest.json`，只暂存该文件，提交并推送 origin main。**推送失败仅警告**，须再核对远端清单，不能把脚本结束当成已同步证明。

## 三条更新协议硬规则

1. **资产名必须匹配 `morrow-<code>-…`**。客户端从资产名提取 code，不能只给普通 `app-release.apk`。正式单包命名 `morrow-<code>-<name>-release.apk`；ABI 拆分默认关闭。
2. **公开更新不能是 prerelease/draft**。当前客户端走 `/releases/latest`，不返回预发布。脚本虽有 `--prerelease`，后续仍核对 latest，不能把这个参数当成可正常验收的预发布流水线。
3. **发布成功才回填 manifest**。这是 GitHub API 不可达时 raw/jsDelivr 的回退数据，需含真实下载地址、大小和摘要；`--skip-manifest` 会削弱回退，不作为默认流程。未发布的 69 不提前写入清单。

更新检测取各探测源中最大的 code；测速只决定下载线路。缓存落后应按 N 组测试，不擅自改版本判定。
PackageInstaller 会话安装仍有系统确认，同包名同签名才可原位升级保留数据。

## 失败续接与撤回

- 先确认远端 Release / 资产 / 清单实际到哪一步；避免直接重跑造成同名版本冲突。上传失败可只补资产，摘要/大小从实际 APK 重算，不手抄。
- 代理 502 先排查环境；只有已获授权的操作可受控重试，不更改代码/remote 或绕过校验。Git 不在 PATH 时先找实际工具，不能凭 reflog 宣称已推送。
- 严重问题需撤回时先获授权，再将 Release 转 draft或按约定处理 Release/tag，并核对所有回退源。**仅转 draft 不会清掉已写入 raw/jsDelivr 的 manifest 或缓存**；不能宣称客户端立刻全线检测不到。
- 修复版用更高 code，不复用已安装用户的版本。已有错误摘要场景应隔离测试，不对真实用户发布损坏 APK。

发布完成后同步 HANDOFF / TODO / CHANGELOG，分别记录真实构建结果、真机结论、Release 与清单推送状态。

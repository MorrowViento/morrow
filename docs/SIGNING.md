# SIGNING — 发布签名与安装路径

> 更新：2026-11-16。涉及签名/安装时才读；发布操作看 RELEASE，当前任务看 HANDOFF / TODO。
> **沿用原发布密钥，不重新生成。** 密钥与密码只在用户目录或 CI Secrets，不进仓库、聊天或日志。

## 当前状态与不可混淆项

- 2026-11-18 已沿用同一把原 RSA 4096 发布密钥构建并发布 **4.1.1 (70)** release；密钥与密码仍只在用户目录（`~/.gradle/gradle.properties` 外部注入），未读取/输出到仓库或日志，没有生成新密钥。
- 该版 release / debug 经 `output-metadata.json` 与 `aapt2` 核实为 **4.1.1 (70)**；`apksigner verify --print-certs` V2 通过，证书 SHA-256 `8894605E2B84AEC593D95E9DFB8C315D18CD48D99FAC1E29F69CAA4B78B18D02` 与正式 68 **一致**，具备同签名原位升级条件（实际安装与保数据仍待真机）。已上传 `morrow-70-4.1.1-release.apk`（5,196,689 B / `7F92400A…D963`）到 Release `v4.1.1`。
- Gradle 未提供 keystore 时 `assembleRelease` 保持未签名，debug 不受影响；发版脚本缺密钥则改选 debug。这两种行为不同，发布前必须人工确认。

## 安装/迁移路径

| 已安装 → 目标 | 正确路径 |
|---|---|
| 朝夕正式 68 → 同包名同签名正式 69 | 验证后原位升级；更高 code，不应要求卸载，数据/提醒/小部件需按 N 组回归 |
| 朝夕 debug ↔ 朝夕 release | 签名不同不能覆盖；先导出备份、卸载、安装、导入，重新检查提醒并添加小部件 |
| 旧「萤」→ 新「朝夕」 | 包名不同，是独立沙盒，不能自动读取旧私有数据；先从旧应用导出，按用户迁移流程安装/导入 |
| 换机导入备份 | widgetId 不同，使用 default_style 兜底后重加小部件；4.1 备份不含背景图片本体，须重选图 |

旧包 `com.jiuxina.ying` 与新包 `com.morrowviento.morrow` 不共享应用数据。
卸载清空私有数据（包括自定义背景图片），先完成备份并保留原图；不要将 debug→release 重装算作 N7 的保数据升级通过。
PackageInstaller 会话安装仍弹系统确认，不承诺免确认静默安装。

## 签名参数（现有密钥配置）

环境变量优先，其次用户级 Gradle properties：

| 环境变量 | 用户级属性 | 含义 |
|---|---|---|
| `MORROW_KEYSTORE` | `morrow.keystore` | keystore 绝对路径，支持 `~/` |
| `MORROW_KEYSTORE_PASSWORD` | `morrow.storePassword` | keystore 密码 |
| `MORROW_KEY_ALIAS` | `morrow.keyAlias` | 现有密钥别名，脚本默认 morrow |
| `MORROW_KEY_PASSWORD` | `morrow.keyPassword` | 密钥密码 |

本地文件为 `C:/Users/<用户>/.gradle/gradle.properties`，不是仓库根的同名文件。Windows 属性路径使用真正的 Windows 绝对路径，不能填 Git Bash 的 `/c/...`：

```properties
morrow.keystore=C:/Users/<用户>/.morrow/morrow-release.jks
morrow.storePassword=<现有密码，仅在用户级文件填写>
morrow.keyAlias=morrow
morrow.keyPassword=<现有密钥密码，仅在用户级文件填写>
```

`.gitignore` 忽略 jks 不能保护写进仓库属性文件的明文密码。不要输出整个用户属性文件、凭据或开启会泄露签名信息的调试日志。

## 构建与核验（Windows PowerShell，从仓库根）

```powershell
$env:JAVA_HOME = (Resolve-Path '../.toolchain/jdk/jdk-21.0.13+9').Path
& '../.toolchain/gradle-9.6.0/bin/gradle.bat' -p . :app:assembleRelease --console=plain
# 构建成功后确认输出元数据中的 versionCode / versionName
& '../.toolchain/sdk/build-tools/37.0.0/apksigner.bat' verify --print-certs 'app/build/outputs/apk/release/app-release.apk'
```

SDK 不在该路径时按本机实际 SDK 路径定位 apksigner，不猜安装目录。
对已知 4.0.0 正式 APK 与新 APK **分别**执行 apksigner，确认签名验证通过、证书 SHA-256 一致，并查新包版本确实高于安装端。
`keytool -printcert -jarfile` 依赖 JAR/v1 签名，只有 v2/v3 签名的 APK 不能可靠用它核验；不要据此误判未签名或重新生成密钥。
ABI 拆分默认关闭，标准产物是 `app/build/outputs/apk/release/app-release.apk`；开启拆分后需核对每份实际资产。

## 新环境与密钥备份

- **续接/换开发机优先恢复原密钥与配置**，不是再运行生成脚本。备份 keystore 与密码分开保存，恢复后先比对旧正式 APK 的证书。
- 只有从未发版的新应用或经明确批准建立新签名身份，才使用 `scripts/create-keystore.sh`；这是 Bash 脚本，需支持的 Bash 环境，不能通过 PowerShell 启动另一 shell 绕过工具限制。
- 生成脚本默认在 `~/.morrow/morrow-release.jks` 建 RSA 4096、有效期 10000 天、别名 morrow；需交互输入密码，不在非交互工具中运行。当前应用已发布，**此流程不是当前待办**。
- 丢失原密钥会使现有签名链更新受阻，先查备份/已有签名管理方案，不通过随意换包名或换签名规避；如确需改变身份，另行明确用户数据迁移与发布方案。

## CI 与常见故障

CI 签名方案可使用 Secrets `MORROW_KEYSTORE_BASE64` / `MORROW_KEYSTORE_PASSWORD` / `MORROW_KEY_ALIAS` / `MORROW_KEY_PASSWORD`，构建时还原原 keystore 并注入对应参数；这是配置方案，不代表当前 CI 已产出并发布签名正式包。修改 CI/Secrets 需单独授权。

| 症状 | 检查 / 处理 |
|---|---|
| keystore 找不到 | Windows 真正绝对路径、文件是否存在；不要用 /c/ 路径 |
| 密码或别名错误 | 核对原配置，不打印值、不重新生成 |
| release 构建成功但未签名 | keystore 未注入；用 apksigner 验证，不能只看构建成功 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 证书不同；确认是否装了 debug。需要重装时先备份，不默认删用户数据 |
| 版本比预期低 | APK 是旧产物，查 output-metadata 并重建；目录/文件名不是版本证明 |
| 证书 CN 一样仍不能升级 | 名称可相同，比较证书 SHA-256 与实际已装包签名 |

核验结果写入 HANDOFF / CHANGELOG 时只记录「与发布基线一致/不一致」和验证来源；不复制密钥或密码。发布状态另按 RELEASE 核对远端。

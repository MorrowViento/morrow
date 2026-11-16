# HANDOFF — 朝夕 · 会话续接入口

> 更新：2026-11-16。默认只读本文件和 [TODO](TODO.md)；历史、验收、发布细节按需查。
> 本批六阶段功能改进、自动回归与 feature-update 测试包已完成；功能提交 `4bfc12b` 已推送 GitHub main 并回读核验，文档收尾独立提交。下一步由用户测试并按反馈修复；未公开发布或安装到设备。

## 任务目标与边界

以 ying 原版功能与数据语义为基础，用 Kotlin + Compose + Glasmio 0.1.3 重写 Android 13+「朝夕」。核心重写已落地，不另起工程、不并行维护 Flutter 主线、不重做旧里程碑。
本批按用户已批准的六阶段实施：类型/重复 → 详情/选择 → 首页搜索 → 年历 → 图片裁剪 → 回归出包。字体库、节日皮肤等延期项不自动开工，正式发布另需授权。

## 当前进度（本地事实，不是远端实时状态）

| 项目 | 当前记录 / 证据 |
|---|---|
| 仓库 | 本文件上一级；`C:/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/morrow` |
| Git | main；功能提交 `4bfc12b` 已普通推送并回读核验，原有 2782dd1/f5d4220/a7a112a 一并同步；文档收尾独立提交。MANUAL-TEST 保留本地未提交修改；Git 使用 WorkBuddy 自带 PortableGit |
| 应用 / 版本 | 「朝夕」；`com.morrowviento.morrow`；最终 APK 与 output-metadata 均为 **4.1.0 (69)**；minSdk 33 / targetSdk 37 |
| 数据库 | **Room schema 2**，显式非破坏性 1→2 迁移；新增 kind 与 last_handled_occurrence；旧事件默认 TODO，保留日期/完成/置顶 |
| 推荐测试包 | 仓库上级 `morrow-69-4.1.0-release-feature-update.apk`，**5,110,088 B（约 4.87 MiB）**；R8 + 资源裁剪；原正式签名 |
| 调试包 | 仓库上级 `morrow-69-4.1.0-debug-feature-update.apk`，**80,818,980 B（约 77.07 MiB）**；不能覆盖正式签名安装 |
| 构建输出 | `app/build/outputs/apk/{debug/app-debug.apk,release/app-release.apk}`；最终副本 SHA-256 与对应源包一致 |
| 最新回归 | **338 tests / 0 failures / 0 errors / 0 skipped**，40 份 XML；最终全量实际执行，不是局部结果拼数 |
| lint / 构建 | **0 errors / 13 warnings**；debug、release、release lintVital 成功，R8/资源裁剪实际执行 |
| 签名核验 | apksigner v2 验证通过；release 证书 SHA-256 与正式 68 一致，debug 不同；aapt2 核验内部包名/版本/SDK |
| 发布基线 | 本地 `update/manifest.json` 仍 v4.0.0-68；已同步 GitHub main，但未查询最新 Release、修改清单、上传 APK 或公开发布 |
| 真机 | `adb devices -l` 为空；未安装/卸载、测视觉/触摸/系统提醒/升级保数据或测 FPS/耗电/启动时间；MANUAL-TEST 未修改或代勾 |
| 工具链 | 父目录 `.toolchain`：JDK 21.0.13+9、Gradle 9.6.0、SDK build-tools 37.0.0；保持现有 AGP/Kotlin/依赖/CI/密钥 |

最终交付 APK SHA-256：
- release：`3932EF2C6321497D9BB6C933DE2BADA5B4CA3188FDC887FC2479C44B0F147CEA`
- debug：`234C5953048A31702863CC7320DF89B5CF582B26B26D6313677848EEF6967D88`

上一批 code-review 包保留，不是本批功能包；其 313 项测试/12 warnings 与旧摘要是历史记录，见 CHANGELOG。

## 本批实现与已确认规则

- **类型/重复**：明确 TODO/ANNIVERSARY 与 NONE/MONTHLY/YEARLY，分类仍是名称身份，不据分类猜类型。新建默认待办、不重复；旧已完成重复事件保持停用，恢复后才继续。
- **统一主动作**：已完成待办→恢复；每年→进入下一年；每月→进入下一月；不重复待办→标记完成；不重复纪念日→无主动作。详情为 52dp 置顶图标—56dp 大主动作—52dp 删除图标；选择 chip 加强选中反馈与语义。
- **周期推进**：不改 targetDate 原锚点，只写已处理周期游标；月末 1/31→2/28→3/31、闰日平年夹取后闰年恢复。未来卡片/提醒/部件考虑首次日期与游标，历史日历保留系列历史；重复推进不永久完成整个系列，旧周期连点拒绝二次推进。
- **兼容存储/编辑**：Room 游标为 epochDay，备份/ying/Undo 为 ISO 日期；原生备份写 v2、兼容 v1、拒绝未来版本。编辑在操作锁内读最新记录，保留完成/置顶/游标；只改标题不重设重复锚点，改日期/重复规则清游标；删除后的旧表单不复活事件。真实旧 v1 数据库迁移/关闭重开已回归。
- **提醒**：FIRE/COMPLETE/SNOOZE 使用精确目标校验与周期身份；PendingIntent data URI 含事件/目标/动作。旧闹钟缺标题或新动作字段时从当前记录补齐，不误取消合法通知；已处理目标仍取消。通知完成只推进一次且保留 Undo，过期稍后动作不取消当前通知，合法稍后保留具体周期。全天 09:00 与两周期×五提醒上限不变。
- **首页搜索**：只展开 SearchBar 与筛选，结果由原首页 EventList/EventCard 展示；无全屏遮罩/独立结果页。普通与搜索列表分别保存滚动位置，搜索展示匹配完成分区；打开详情保留查询/筛选并收键盘。
- **年视图**：十二个月周一开头的固定 42 格小日历，今日/事件标记、最多三条日期·标题摘要及剩余次数；懒网格按宽度/字体缩放适配。统计是落点次数，不是去重事件数，文案统一“次事件”。每月一层玻璃，不为每个日期格创建玻璃材质。
- **背景裁剪**：比例取完整 GlassBackdropHost 实测窗口，位于 safeDrawingPadding 前；选图仅有界软件解码，不发布文件/写设置。源图浮点裁剪框支持移动、双指缩放、8 倍上限、重置、可见缩放按钮和无障碍四向移动。确认后 Matrix 映射选区、取最终画面颜色、JPEG 90 发布唯一文件并提交；取消/提交失败保旧图，不 recycle UI 借用图。长边 2400、EXIF、缓存/唯一文件身份保护保留，无新裁剪依赖。

## 回归证据与曾出现的异常

- 上一批审核 313 项；搜索/年历阶段 328 项；裁剪/取消/VM 集成后 334 项；本次接收器处理链路新增 4 项后，最终 **338 项全通过**。
- 前次联合命令曾出现 **334 项/11 失败**：Robolectric Native 字体 ZIP FileSystemAlreadyExistsException、DataStore 临时文件重命名 AccessDeniedException，另有 SQLite CloseGuard 警告。独立重跑在修改测试前已 334 全通过，不认定异常必然来自生产代码或 Gradle 并行。
- 本次强化 SettingsTypeSafetyTest：隔离为普通 Application，DataStore Job 与收集协程 cancelAndJoin 后才清临时目录；未删除断言、关闭 Native 图形测试、升级依赖或改 Gradle 并行设置。其后 334 联合/独立强制运行及最终 338 联合运行通过，成功输出未再出现上述异常；**原生 ZIP/Windows 文件占用根因未被确证，不宣称永久消除**。复发先查资源生命周期/报告，不降测试强度。
- 新 EventViewModelTest 覆盖实际 Room/DataStore 的推进幂等、Undo、编辑合并、删除不复活、纪念日不完成、批量通知取消、首页/部件目标一致。
- 新 ReminderReceiverTest 覆盖实际 suspend 处理器 + Room/DataStore/NotificationManager，系统排期用记录型 fake；**不是 onReceive/goAsync/系统闹钟投递端到端验证**，也不是实际 RemoteViews/升级验收。
- 13 个 lint 警告：7 个版本提示、4 个 KTX 建议、窗口宽度/dataExtractionRules 各 1；删除确实无引用的旧提醒完成字符串，未 suppress 或随意升级依赖消警告。

## 下一步：用户测试与反馈修复

1. **先导出备份并保留背景原图，再按签名选 feature-update 包**。正式 68 用户优先 release 原位升级；debug 用户使用同 debug 签名。debug↔release 不能直接覆盖，需要用户自行确认备份/卸载/安装/导入；不要默认卸载正式版。
2. 优先测试：详情图标/大按钮与类型动作；每月/月末/闰日推进和撤销；旧已完成重复事件恢复；提醒选中反馈；搜索输入/键盘/返回/详情保持/普通滚动恢复；年历窄屏/大字体/摘要；裁剪移动/缩放/重置/取消/取色/配置变化与保存失败保旧图。
3. 再按 MANUAL-TEST 适用的 AL/A–D/G/H/J/L/M/N 编号回归背景/玻璃/核心数据/系统提醒/SAF/权限/部件/升级。旧搜索浮层样例不代表当前 SearchBar，旧编号适用版本以文档顶部为准；不代勾。
4. N 更新链路须更新端 code 大于安装端，69 对清单 68 无更新正常；PackageInstaller 仍有系统确认。实际 68→69 Room 迁移和设置/提醒/部件保留须真机验证，不把模拟旧库测试等同真机升级。
5. 收到设备/版本/步骤/现象后复现、修复、补回归、重建并核验新包。性能目前仅代码与自动回归证据，无真机测量结论。
6. 真机通过且另获发布授权后，再按 RELEASE 核对远端版本/main/签名/资产并发布；未发布前清单保持 68。

## 现行工程约束

- Glasmio 锁 **0.1.3**，业务不直接 import，仅 ui/glass 封装；查该版源码/AAR，不照 main/旧摘录写 API。不引入 NDK/CMake、WorkManager、navigation-compose。
- 真玻璃可采样：统一 Clear、glassFrost 默认 0.62；模糊 `0.5 + 25*t` dp、色纱 `0.26*t*t`。不恢复不透明 Regular/旧曲线；GlassAppearance 默认，减透明不交库关闭采样。
- LocalGlassDark/LocalAccentPalette 统一主题；浮层同页不切断 backdrop，禁止恢复自绘 GlassPress/Material 方形 ripple。壁纸后台渲染/上一帧/三槽缓存，丝缎首张同步。图片只在私有目录，备份含种子不含图片本体。
- 事件操作锁 → 撤销持久化锁；内部写入口不重复加锁/嵌套重入。撤销仅最近一次，立即落库+快照，前台 6 秒/后台暂停/重开 10 分钟内给满；编辑和分类修改清旧快照，保留 NonCancellable 提交边界。
- 分类名称为身份，重命名级联，删除占用分类先迁移不删事件。REPLACE Room 事务、MERGE 只写 incoming 行（仍读取全表供对账）；Room/DataStore 分开提交，不承诺跨存储原子性。ying 未知枚举/默认分类兼容不顺手改。
- 提醒临近 12 小时优先 AlarmClock，远期精确/无权限降级；广播 goAsync/finally finish；导入/清空在锁内取消消失 id 后重排。
- 部件 RemoteViews 非实时真玻璃；保留 notifyAppWidgetViewDataChanged + adapter data URI 重绑/可变模板 PendingIntent，删除实例清配置、默认样式不含 eventId，零点刷新，updatePeriodMillis=0；不做 ColorOS 私有卡片。
- API33 日期 Instant→atZone→toLocalDate，不用 API34 LocalDate.ofInstant；SAF 覆盖写 wt；RemoteViews 分隔线 ImageView，LazyColumn 嵌套有界。测试 DataStore 独立文件/受控 scope，取消并等待写任务结束后清理。
- MANUAL-TEST 编号/checkbox 由用户维护，提交逐次授权，禁 git add -A；已有本地修改不纳入本次提交。**用户已授权本批代码/工程文档 commit/push；未授权公开发布/改线上清单/CI/密钥/安装/卸载**；密码不进仓库/日志。
- 代理 502 先核对网络/凭据/远端，获授权推送才重试，不绕过检查；整文件差异先查 LF/CRLF；提交格式 feat/fix/chore(scope): 中文描述。

## 构建与报告（Windows PowerShell，从仓库根）

```powershell
$env:JAVA_HOME = (Resolve-Path '../.toolchain/jdk/jdk-21.0.13+9').Path
& '../.toolchain/gradle-9.6.0/bin/gradle.bat' -p . :app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain
# release 沿用外部注入原密钥；不输出用户级属性或密码
```

- 最终上述四任务成功，338 全量单测实际执行，lint 分析/报告与 release R8/资源裁剪实际执行。XML：app/build/test-results/testDebugUnitTest/；单测 HTML：app/build/reports/tests/testDebugUnitTest/index.html；lint：app/build/reports/lint-results-debug.xml。
- Git 已定位：`C:/Users/yunbo.wang/.workbuddy/binaries/PortableGit/versions/1.3.0/cmd/git.exe`，实际版本 2.55.0.windows.3；后续用该工具检查工作区/远端，不再沿用“没有 Git”的旧判断。源代码推送用普通非强制 push，最终状态用 ls-remote 回读核验；MANUAL-TEST 单独留在本地。
- 本会话主动后台执行不受当前工具模式支持，使用前台足够超时；不绕过工具边界启动其他 shell。scripts/release.sh 是 Bash 且会公开发布/改清单，不在当前提交推送任务中执行。

## 按需定位与文档分工

源码前缀 app/src/main/java/com/morrowviento/morrow/：类型/周期 domain/model、domain/time；迁移 data/db；表单/详情 ui/event；搜索 ui/search/SearchOverlay.kt；年历 ui/calendar；裁剪 data/backdrop/BackdropImage.kt 与 ui/settings/SettingsScreen.kt/SettingsViewModel.kt；提醒 reminder、data/reminder；部件 widget；更新 service/update、ui/update。
上级 .ref 仅 ying Dart 摘录，不是完整 Flutter 仓库；旧 Glasmio 摘录不是当前 API 基准。

[TODO](TODO.md) 当前待办；[MANUAL-TEST](MANUAL-TEST.md) 用户验收；[CHANGELOG](CHANGELOG.md) 历史；[SIGNING](SIGNING.md)/[RELEASE](RELEASE.md) 签名/发布。后续只更新短快照、对应待办与新增历史，区分代码完成、本地验证、真机通过、远端发布。

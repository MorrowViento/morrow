# HANDOFF — 朝夕 · 会话续接入口

> 更新：2026-11-19。默认只读本文件和 [TODO](TODO.md)；历史、验收、发布细节按需查。
> 当前状态：**4.2.0 / 73 代码完成，构建核验已过，发布进行中**。两批一起发：上一批（完整重复规则 / 农历循环 / 多点提醒 / 持续事件 + 节日皮肤修复）+ 本批（**时间块视图 / 多维标记 / 日期计算器 / 归档**）。已升版 `versionCode 73` / `versionName 4.2.0`；release APK 已出（5,313,701 B / `d1c02522…097d`），aapt2 73/4.2.0、证书 `8894605E…8D02` 同 68、`runtime.txt` 116,447 B 未变、`verifyRuntimeNoticeCompleteness` 退出 0、全量 **436 项 / 53 XML / 0 失败**、lint 0 errors / 15 warnings。远端最新仍是 4.1.3 / 72（tag `v4.1.3` 指向 `f5553f7`、Release `449719768`、资产 `morrow-72-4.1.3-release.apk` 5,231,781 B / `10258ec2…f39c`）。4.1.3 四件事与上一版 4.1.2 / 71（`601b3beb…73a6`、Release `449651404`）均为历史证据。**续接先读「#25」（两批实现）与「#24」（发布实录）。** 剩余：人工逐组件源码层版权审阅与真机验收（含 72→新版保数据升级）。

## #25 两批：重复规则 / 农历循环 / 多点提醒 / 持续事件 + 时间块 / 标记 / 计算器 / 归档（代码完成，发布中）

**用户指令**：第一句「首先节假日皮肤没有效果，先做完整重复规则，农历生日农历循环，多点提醒，持续事件」；第二句「现在做时间轴时间块，多维标记，日期计算器。归档和现在的已完成有什么区别，有区别的话就做，做完后和上一版一起 release」。

**归档与已完成的区别（本批的关键判断）**：已完成 = 事情办到了，留在列表里看成绩、日历仍标勾；归档 = 从日常视线收起来（不做了/过气了/暂时搁置），**不提醒、不上日历、不计入已完成、不进成绩区**，只能从归档箱找回。这是第三条状态线，不是「已完成」的别名——所以「有区别」成立，做了。

**节日皮肤「没效果」的真因**：不是没接上。`LocalFestivalSkin` 由 `HomeScreen` 包在 `GlassBackdropHost` 外层，设置页打开时背景仍走同一个宿主，链路是通的。两个原因叠在一起——① **2026-11-18 是农历十月初十，不是那八个节日**；② 4.1.3 的柔光只有 0.22/0.16，叠在丝缎四团大色块上几乎看不出来。修法：浓度 0.34/0.26 + 底部第二团；设置页加**预览**（当场换背景，因为背景就在设置页底下），解决「一年只有八九天能验」的问题。

**数据模型一次到位（schema 2 → 3）**：`CountdownEvent` 新增 `repeatInterval`（1..99）、`repeatWeekdays`（ISO 1..7 集合）、`repeatOnLunar`、`repeatUntil`、`endDate`；`RepeatType` 扩为 NONE/DAILY/WEEKLY/MONTHLY/YEARLY；`EventPrimaryAction` 新增 `NEXT_OCCURRENCE`。Room 五列全带默认值（`MIGRATION_2_3` 纯 ALTER），旧行升级后仍是「不重复、单日」。备份 format 2→3：新字段可选、旧备份可读、**新备份在旧 App 上被拒绝**（`version > CURRENT_VERSION`）而不是静默丢字段；`UndoCodec` 同步，保证撤销逐字段等价。

**重复规则的两个坑**：
1. **周档要按「周」对齐，不能按 7 天加**。锚点 11/18 周三、选「每 2 周的周一/周三」，正确的下一次是 11/30 与 12/2（两周后那一周），如果按 `锚点+7N` 算会得到 12/2 与 12/4。实现以锚点所在周的**周一**为格点，每 `interval` 周展开选中的星期，再用 `filter { it >= lower }` 掉掉锚点之前的日期。
2. **农历月日不能靠 repeatMonth/repeatDay 判断**。这两列在公历模式存公历月日、农历模式存农历月日，单看数值分不出来；`RepeatRule.lunarYearly` 因此**以锚点日期的农历换算为准**，两列只作超出年表时的兜底。首版实现用了 `repeatMonth ?: anchorLunar.month`，被 `RepeatRuleTest` 抓出「2027-03-24 落在农历二月」——测试里 helper 把 `repeatMonth` 设成了公历 2 月。

**农历换算**：新增 `LunarCalendar.toSolar(年, 月, 日)`，用同一份 `YEAR_STARTS` 累计表 + 月长；日超出该月取月末（三十遇小月取廿九），闰月不参与。`RepeatRuleTest` 用「扫 2026 全年做公历→农历→公历往返」把它钉死（闰月跳过，因为生日本来就不按闰月过）。

**持续事件**：`endDate`（含当天）而不是时长——用户理解的是「9 月 1 日到 9 月 5 日」。重复时每次按同样跨度平移。三处落点要注意：
- 日历月视图的 42 格与「选中某天」用 `coverageBetween`（中间日也亮、点进去也看得到），**年视图与列表仍按起点**，否则一个月的出差会在年摘要里刷 30 行；
- 首页卡片进行中时，目标取 `coveringStart`（这一次的起点）而不是 `upcomingTarget`（下一次），否则 9/3 会显示「还有 362 天」；徽章改读「还剩 N 天 / 今天结束」；
- 提醒仍按第一天（出发前提醒，不是结束前）。

**多点提醒**：数据层早就是 list（上限 5），缺的是入口——4.1.3 只有 9 个预设 chip。本轮加自定义分钟输入 + 「已设」摘要。

**文案集中**：新增 `domain/time/RepeatText`（`short/of/span`），首页卡片、日历行、详情页、小部件共用。4.1.x 是四处各写一份 `when (repeatType)`，加两档就要改四遍。

**本批四功能（时间块 / 标记 / 计算器 / 归档）**：

- **时间块视图**（日历第四视图）：月视图回答「哪一天有事」，时间块回答「这一天被什么占着」。`TimelineLayout.kt` 是纯函数布局——全天/跨天条画在轴上方、时段事件按分钟定位、重叠事件自动分列（`lane`/`laneCount`）；`TimelineView.kt` 渲染 + 每 30 秒推进的当前时刻线 + 滚到最早事件前半小时。分段控件「月/年/列表」扩四档；`CalendarActions.shiftDay`、`CalendarNav.clampDay` 配合翻日。重叠分列是最容易写错的逻辑，`TimelineLayoutTest` 专门钉死。
- **多维标记**：`EventTags.kt`——一条事件可挂多个标签（与单值分类是两个维度：分类回答「属于生活哪一块」，标签回答「在哪些场景要出现」），上限 5 个、每个最长 8 字；归一化（去 `#` 前缀、逗号/全角逗号换空格、去重忽略大小写、保持输入顺序）在写库/备份/撤销三条路径复用。编辑面板加标签输入与已有标签点选，卡片/详情展示，搜索命中标签。
- **日期计算器**：`DateMath.kt`（纯函数，无 Android 依赖）+ `DateCalculatorSheet.kt`，入口在首页菜单。有符号天数差、含首尾天数、几周零几天、几年几月几天、基准日加减、农历换算、周岁。**刻意不做**工作日/节假日计算（需要维护一份会过期的调休表，成本高于便利）。
- **归档**：`CountdownEvent.archivedAt`（+ `isArchived` 派生）。首页/日历（`CalendarPresentation.strip` 过滤）/提醒排期（`ReminderPlan` 排除）/小部件（`WidgetSnapshot` 过滤）四处统一排除归档；首页菜单「归档箱」+ `ArchiveSheet`（恢复/删除/清空）；`UndoKind` 加 `TOGGLE_ARCHIVED` / `CLEAR_ARCHIVED`。

**数据模型二次扩展（schema 3 → 4）**：`CountdownEvent` 新增 `tags` / `archivedAt` / `durationMinutes`；Room `MIGRATION_3_4` 三列全带默认值（`tags` / `archived_at` / `duration_minutes`）。备份 format 3→4（新字段可选、旧备份可读、旧 App 拒绝新备份）；`UndoCodec` 同步。

**验证**：**436 项单测 / 53 份 XML / 0 failures·errors·skipped**（394 + 上一批 18 + 本批 `DateMathTest` / `TimelineLayoutTest` / `EventTagsTest`）；lint release **0 errors / 15 warnings**（同一组基线规则，本批 `TimelineView` 新增的一条 `UseOfNonLambdaOffsetOverload` 已改为 lambda 重载收掉）；`assembleDebug` / `assembleRelease` 成功。`BackupRepositoryTest` 的 1→3→4 迁移断言、`BackupCodecTest` 的版本号（2→3→4）与未知枚举用例（`WEEKLY` 已是合法值，改用 `FORTNIGHTLY`）已同步。

**未做 / 未验**：上一批未验项（三处新滚轮在长表单里的滚动、进行中卡片观感、农历生日跨年、星期多选在窄屏的横向滚动、72→新版保数据升级）与本批未验项（时间块重叠分列观感、当前时刻线推进、归档箱交互、日期计算器面板）合并为一次真机验收清单，见 TODO。发布流程见下。

## 当前批次与授权

- 新版已发布：**tag `v4.1.2`**（指向 `f9da8d8`）、Release「朝夕 4.1.2」（id `449651404`，2026-11-18T07:32:07Z 正式，非 draft/prerelease）、资产 `morrow-71-4.1.2-release.apk`（5,199,013 B / `601b3beb…73a6`）；**tag 与标题按用户指定不带 code**（资产名仍必须带 code）。`update/manifest.json` 已按 `/releases/latest` 真实字段回填并推送（见 #22 节）。上一版 `v4.1.1`（`449587411` / 70 包 5,196,689 B）与 `v4.0.0-68` 均未改动。
- 用户已授权本批提交推送、原签名 release APK 上传、正式 Release，以及发布核验成功后回填真实 manifest。自身代码 MIT 已选定；ying 的 MIT 与 Glasmio 0.1.3 的 Apache-2.0 分别保留。许可资源已实施（根 LICENSE、ying.txt、Glasmio NOTICES、generateRuntimeNotices、三份静态协议/版权正文）；十项 Apple 素材已证明本次 69 release 审计包不分发，正文缺口为 0 且完整性门槛通过；README 与设置页可读许可本轮已完成。逐组件源码层版权审阅与全量回归未完成，正式 70 包仍须重验，见 #19 章节。不要重复询问同一授权。
- #16：ChoiceChip 外层 CenterStart、16sp 居中行盒/无字体 padding；首页/日历使用持续存在的活动采样槽，只有当前内容绘制，底栏/浮层在槽外；日历不在安全区重绘背景。新增 Compose v2 + Robolectric Native 真实布局测试（4 项），既有手势测试（5 项）通过；未真机验视觉。
- #17：完整 CalendarUiState 共享 Transition，模式/周期 contentKey 抑制同周期切页；退出页使用旧快照、交互仅当前周期；模式 crossfade/周期定向滑动，不插值高度、不读惰性邻页。明确数字行盒/固定字重及大字体格高。7 项动画与真实日历测试通过（含连续反向、2×字体、边界箭头/手势、reduceMotion），未真机测帧率。
- #18：小部件复用应用丝缎/私有图片（cover 裁剪），后台预乘 alpha 三次可分离盒滤波；共享 frost/veil/强调色规格，外背景连续圆角 26dp。工作图长边 384、输出不超过 262,144 像素，最多 16 个宿主 SizeF 变体均分总预算，LRU 8 MiB；挂起渲染锁不阻塞主线程查缓存，不回收借出的源图/缓存图，不污染首页 placeholder。列表行用原生 26dp 圆角色纱/边缘（不是逐行实时采样或连续圆角 shader），不分配整幅行位图。
- #18 验证：新增 15 项回归（9 像素/尺寸/缓存、2 原生列表快照、2 保存刷新、1 应用主题、1 Compose 实际预览/2×字体）；聚焦 45 项通过后补齐最后 2 项，最终全量 366 项实际通过。预览按测量尺寸后台生成，切主题不混旧位图，列表保留同次刷新外观快照；frost/减透明提交成功后真实刷新。未验启动器/设备帧率，不承诺模糊桌面壁纸。
- 当前阶段：#19 代码与文档部分已交付，**全量回归已完成（379 项 / lint 0 errors·15 warnings / 原签名 release 成功）**，只剩人工版权归属审阅，详见下方专门章节；之后才 #20 全量验证与 4.1.1 / 70 原签名构建、#21 推送发布。保持 schema/包名/密钥/生产依赖/CI 不变。
- HEAD 基线 `d3f3e93`（4.1.2 发布代码 + 回填的清单与文档），`v4.1.2` 已发布并指向 `f9da8d8`；远端 latest 与 `update/manifest.json` 均已回读为 `v4.1.2` 且语义一致。MANUAL-TEST 原有本地修改不编辑、不代勾、不提交。
- 暂停动画时钟的测试须显式 `Snapshot.sendApplyNotifications()`：否则 Robolectric 全局快照尚未通知重组、语义树仍为旧页；不能把这种测试调度失败当生产动画问题。当前未关闭 Native 或删断言。

## 任务目标与边界

以 ying 原版功能与数据语义为基础，用 Kotlin + Compose + Glasmio 0.1.3 重写 Android 13+「朝夕」。核心重写已落地，不另起工程、不并行维护 Flutter 主线、不重做旧里程碑。
上一批 4.1.0 六阶段（类型/重复 → 详情/选择 → 首页搜索 → 年历 → 图片裁剪 → 回归出包）已完成；当前按顶部 4.1.1 计划继续。字体库、节日皮肤等延期项不自动开工。

## 上一批 4.1.0 交付基线（历史，不是远端实时状态）

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

## #19 运行时许可与 README（进行中，含发布阻塞）

> 2026-11-18 续接实录。初始许可文件/收集器、AAR/Apple 素材核验、生成器修复、**正文缺口补齐**、**README 与设置页可读许可**、**许可改动后的全量回归**六步均已区分留证。当前 **379 项全量单测（47 份 XML，0 failures/errors/skipped）通过**，`lintDebug` **0 errors / 15 warnings（与 366 基线同一组，无新增）**，debug 与原签名 release（含 `lintVitalRelease`）构建成功，release 证书 SHA-256 与正式 68 一致；`verifyRuntimeNoticeCompleteness` 再次**退出 0（缺口 0）**。**仍未构建 4.1.1/70、未提交或发行**；366 基线 + 13 项新增 = 379，可对账。

### 已完成（本阶段实际写入）
- `app/build.gradle.kts`：`generateRuntimeNotices` 按 `releaseRuntimeClasspath` 的实际 artifact 输出 `runtime.txt` 与根 MIT 的 `morrow.txt`，generated assets 经 `main.assets.directories.add(...)` 挂载；`verifyRuntimeNotices` → generate → preBuild；另有必须在正式发行前通过的 `verifyRuntimeNoticeCompleteness`，本轮已通过（缺口 0），正式包发布前须再跑一次。
- 保留已验证的 AGP 延迟 `provider { configurations.getByName("releaseRuntimeClasspath") }` / `assets.directories.add(...)`；当前使用精确坐标允许集、受控归档/文本与原字节去重，不再是旧 group 映射或递归所有 jar。生产依赖、包名、schema、CI、签名、版本未改。
- 根 `LICENSE` 为朝夕自有代码 MIT，`Copyright (c) 2026 Morrow contributors` + `Copyright (c) 2026 Gao Shuai (ying-derived portions)` 与完整标准 MIT；本步原字节复制至 APK 的 `assets/licenses/morrow.txt`，已核实一致。
- `app/src/main/assets/licenses/ying.txt`：原项目完整 MIT，含 ying Source/License URL 与 `Copyright (c) 2026 Gao Shuai`；1,187 B，本步 APK 核验仍与源一致。
- `app/src/main/assets/licenses/glasmio-third-party-notices.txt`（本步新增）：v0.1.3 固定上游全文，5,151 B，Git blob 与 SHA-256 锁定；保留 Apple 排除项并已打包，见下方修复证据。
- 三份静态全文（本轮新增，均在 `app/src/main/assets/licenses/`，均带 `Source:`/`License:` 头）：
  - `apache-2.0.txt`，10,775 B，SHA-256 `1781B86746A630256C72FBA8C819AD7700131E858FF0D83F5304CEC8CA771D53`。正文取自本机 Gradle 缓存 `androidx.core:core:1.18.0` AAR 内 `META-INF/androidx/core/core/LICENSE.txt`（该全文 10,175 B、SHA-256 `809FA1ED21450F59827D1E9AEC720BBC4B687434FA22283C6CB5DD82A47AB9C0`），逐字节未改动，只在前面加来源/许可/适用范围说明。它是协议副本，不代替各组件源码层版权归属审阅。
  - `materialkolor.txt`，1,631 B，SHA-256 `D29AC877EE63071FC030507F29F70E4A24417CEDAB0C497E28552BFA79476904`。MIT，`Copyright (c) 2025 Jordon de Hoog`，对应标签 `4.1.1`；正文与上游 Git blob `614400dbd5d11ad779e1caa5694dc0efba75b984` 一致。
  - `material-color-utilities.txt`，11,195 B，SHA-256 `8741D7EDB8AE7B5EF9873BC5034D4C5C9E9D5E0CA9791A688F9DD748067A7A82`。Apache-2.0 + `Copyright 2021 Google LLC`；正文与上游 Git blob `3879bcb2002e2b2e38a953e1149c14cef55ca555` 一致（= 标准 Apache 全文 + Google 附录）。
- 生成物（非源控）`runtime.txt`：现 111,134 B，81 份归档原文按原字节合并为 **8 组**、保留 **118 个不同来源**（含 34 条 Apache 静态归属、MaterialKolor 的 MIT/MCU 两份各 1 条）；旧 928,540 B 版本及其 lint.jar/CopyrightKt.class 误收不再是当前产物。
- `README.md`（本轮重写，替换旧版）：清除了「仓库外计划必读」、WorkManager、「22 项测试」、「M1 已完成」、「只写每年重复」、旧 GlassPress 与旧模块里程碑；改为当前真实的八项功能表、技术形态（Compose BOM 2026.09.00 / Room 2.8.5 / DataStore 1.3.0 / JDK 21 / compileSdk 37 / Gradle 9.6.0）、构建与 `verifyRuntimeNoticeCompleteness` 门槛、Glasmio `v0.1.3` 封装约定、实际包结构、许可边界与致谢，并**保留**包名区别段（`com.morrowviento.morrow` vs `com.jiuxina.ying`，不能覆盖升级、需备份迁移）。README 明确写出 Glasmio 为 Apache-2.0、Apple 图像不随包分发、`runtime.txt` 为逐坐标清单。
- `app/src/main/java/com/morrowviento/morrow/ui/settings/LicenseCatalog.kt`（本轮新增）：7 份可离线阅读的许可文档（朝夕 MIT、ying MIT、Glasmio NOTICES、MaterialKolor MIT、MCU Apache、Apache-2.0 全文、逐组件 `runtime.txt`），`assetPath` 直接指向已随包 assets；`licenseBlocks()` 按空行分段、单段超 2,000 字符再按行切块，`readLicenseText()` 在 `Dispatchers.IO` 读 assets。**不在代码里复制许可正文**，也不一次布局整份 111 KB 文本。
- `ui/settings/SettingsScreen.kt`（本轮修改）：删除误写 `"Glasmio" … MIT` 的静态 `LicenseEntries`；`LicensesSheet` 改为**目录 → 全文阅读**两态，弹层不再用 `fitContent`（需要 `GlassSheet` 的 `weight(1f)` 有界槽），目录用 `LazyColumn`、正文用分块 `LazyColumn`，返回键先回目录。`ActionRow("开源许可")` 增加「MIT / Apache-2.0 / BSD，可离线阅读全文」摘要。**Glasmio 现显示 Apache-2.0。**
- 新增测试：`LicenseCatalogTest.kt`（5 项：assets 可读性/标注与正文一致/完整性/切块保序/边界输入）、`LicensesSheetTest.kt`（3 项 Compose 真实组合：Glasmio 行为 Apache-2.0 且不是 MIT、点击后读出 Glasmio 全文、返回先回目录）。连同既有 `RuntimeNoticesAssetTest` 的 5 项，本轮聚焦应用测试共 **13 项**，0 failures / 0 errors / 0 skipped。
- 实现注记：① 首轮 `LicenseCatalogTest` 断言 Glasmio 文本含字面 “Apache License” 而失败——上游 NOTICES 只写 `Apache-2.0` 并指向 `licenses/Apache-2.0.txt`，是**断言写错**，改为断言 `Apache-2.0` + 不得含 `MIT License`；② UI 测试暴露 `GlassSheet` 并不渲染 `title` 参数，目录页原本没有标题，已补「开源许可」标题行（阅读页本就有返回+标题行）。两处都是本步修正，不是产品缺陷遗留。

### 已核实的上游许可（来源精确，勿用 main/master 当前文本替代）
- ying：https://github.com/jiuxina/ying ；LICENSE https://github.com/jiuxina/ying/blob/main/LICENSE ；MIT，`Copyright (c) 2026 Gao Shuai`。
- Glasmio 0.1.3：https://github.com/NEORUAA/Glasmio ；LICENSE https://github.com/NEORUAA/Glasmio/blob/v0.1.3/LICENSE 为 **Apache-2.0**（原始 Glasmio 代码 Copyright 2026 NEORUAA），**不是 MIT**；以固定 v0.1.3 commit 为准。THIRD_PARTY_NOTICES.md 列 AndroidLiquidGlass/Backdrop/Capsule/Shapes/AndroidX/Reorderable/Miuix 来源，**指向 `licenses/Apache-2.0.txt`，不是在 NOTICES 内含完整 Apache 正文**；原始 NOTICES 已锁定保存到 assets。该附属全文含 Kyant 版权，不能把它的归属泛化给其他项目。
- MaterialKolor：实际标签是 **`4.1.1`，不是 `v4.1.1`**（`git ls-remote` 返回 `refs/tags/4.1.1` → `473e6c7a...`）。固定标签的根 LICENSE 是 MIT，`Copyright (c) 2025 Jordon de Hoog`，Git blob `614400dbd5d11ad779e1caa5694dc0efba75b984`；master 当前2026版权不可代替。`material-color-utilities/src/commonMain/kotlin/com/materialkolor/LICENSE` 是 Apache-2.0 完整原文，含 **Copyright 2021 Google LLC**，Git blob `c3df629aaa04efd6e16106db4ce95b18af5a0c72`。实际 4.1.1 AAR/classes.jar 没有许可原文，而 POM 只声明 MIT；三层证据不可混同，当前随包保留 MIT + MCU Apache 组成说明、两份全文，**不能按 artifact 名称改成纯 Apache**。
- 运行时实际解析（非版本目录请求版本）：Compose 1.12.1 / Material3 1.4.0 / icons 1.7.8、Activity 1.13.0、Lifecycle 2.10.0（目录请求 2.9.4）、Room 2.8.5、DataStore 1.3.0、Okio 3.17.0、Kotlin 2.4.21 / coroutines 1.10.2 / serialization-core 1.7.3 / atomicfu 0.28.0、Glasmio 六模块 0.1.3、Capsule 2.1.3、Shapes 1.2.1、Miuix 0.9.3、Reorderable 3.1.0、material-color-utilities-android 4.1.1、Poko 0.21.1、Gson 2.11.0、Error Prone 2.27.0、Guava listenablefuture 1.0、JSpecify 1.0.0、JetBrains annotations 23.0.0。

### Apple 原始图像：当前审计包不分发，正式发行包必须重验
- Glasmio v0.1.3 THIRD_PARTY_NOTICES.md 明确：`glasmio-ui` 的六个 `glasmio_tapback_*.webp`（经典 Tapback 帧，来源本地 iOS 27.0 Simulator `ChatKit.framework/Assets.car`）和 `glasmio-icons` 的 `glasmio_adaptive_audio.png`、三个 `glasmio_listening_*.png`（来源 `CoreGlyphsPrivate`/`HeadphoneConfigs` 的 `Assets.car`）为 **Apple 原始图像，Apple Inc. 保留版权，不受 Glasmio Apache-2.0 覆盖**；Apple 字体/壁纸不在库 AAR 中。
- **前一个单步（2026-11-18）已取得最终 APK 证据，不再只有中间产物；以下旧包快照已由后述本轮产物取代。** 最初发现已有 `app-release.apk` 是 11 月 16 日的历史包（5,110,088 B，SHA-256 `3932EF2C6321497D9BB6C933DE2BADA5B4CA3188FDC887FC2479C44B0F147CEA`），无许可 assets，不能代表当前源码。本步未改代码或版本，仅按现有配置执行 `:app:assembleRelease --rerun-tasks --console=plain`，54 个任务实际执行，`BUILD SUCCESSFUL`；编译、R8、资源裁剪、打包及 `lintVitalRelease` 均运行。全量单测/lintDebug/debug 未重跑，本次未做证书复核，不冒充 #20 完成。
- 输入 AAR 均从本机 Gradle 缓存的 `io.github.neoruaa.glasmio/<module>/0.1.3/` 精确读取：
  - `glasmio-ui/0.1.3/c99b0eb6495739d3e4281347a739335b859c02c0/glasmio-ui-0.1.3.aar`；AAR SHA-256 `3DE9A3BB2DD29CFEFE2F496D53C878A9DFFC7760952E01A0539BE0C070568DEA`。六项 `res/drawable-nodpi-v4/glasmio_tapback_{exclamation,heart,laugh,question,thumbsdown,thumbsup}.webp`，合计 2,209,820 B。
  - `glasmio-icons/0.1.3/78279e6740c4cdb586c1eefaa4a042ebacacd1b6/glasmio-icons-0.1.3.aar`；AAR SHA-256 `AB54E7D2BD72955E6013F9284FEA156AD95AC19AB117C5230F2CB8F83F81C323`。四项 `res/drawable-nodpi-v4/glasmio_adaptive_audio.png`、`glasmio_listening_{anc,off,transparency}.png`，合计 17,207 B。两份 AAR 的 `R.txt` 均登记相应符号；前一步仅检索顶层未发现 NOTICES，**本轮确认各自 classes.jar 内含 THIRD_PARTY_NOTICES.md**，与锁定上游原文同字节，已正确收集并显式保存。
- **前一步历史快照，当前路径已被本轮替换**：2026-11-18 09:10:57 的 `app-release.apk` 为5,187,031 B，SHA-256 `921150C6C5B6313C70999170350640ACA5B4BDDA6996D0BD471423F90B254AE1`；4.1.0 / 69、140个文件、16个res，CRC/目标文件名/原图摘要/PNG-WebP格式/资源表核验通过。此历史包不再代表当前文件；最新包见下方本轮证据。
- 核验方法不变：内存解包 AAR/APK，10项图像逐一计算 SHA-256，再比对 APK 所有解压文件；额外检查 PNG/WebP 魔数及嵌套归档，避免仅查文件名或原始摘要遗漏资源改名/PNG重压缩。SDK37的资源表检查与中间资源裁剪日志只是联合证据，不用中间产物单独放行。
- 前一步已核实 runtime.txt 928,540 B 与 ying.txt 1,187 B 打包一致，根MIT当时未打包。**本轮已修此缺陷：七份资产与源一致，runtime 现 111,134 B、根MIT为 morrow.txt，另加 apache-2.0/MaterialKolor/MCU 三份静态全文**；以下新证据覆盖旧快照。历史 feature-update APK仍保持 `3932EF2C6321497D9BB6C933DE2BADA5B4CA3188FDC887FC2479C44B0F147CEA`，未改动。
- **发行前约束仍生效**：完成后续许可修正和 #20 的 4.1.1 / 70 构建后，必须对最终待上传的那个 APK 重做上述核验并绑定其 SHA-256；不得以本次 69 审计包替代正式包放行，不得只补 Apache 声明就发布。
- 备注：前会话的 `Add-Type` 安全拦截未重试；本步使用既有归档读取能力，无动态加载 .NET 的解压操作。

### generateRuntimeNotices：生成器与正文覆盖均已闭合，完整性门槛已通过
- `app/build.gradle.kts` 使用 `RuntimeNoticePolicy` 精确 `group:module:version` 允许集，未知模块/版本立即失败；不再按 group 放行。protobuf **BSD-3-Clause** 由 1.3.0 POM 与原始 JAR 确认，1,434 B 原文保留，SHA-256 `A01B712FBB30F80B86851468B6FA58E80EAB68F69F325E88BBC7DDF47CF1B063`。MaterialKolor 4.1.1 的 POM 为 MIT，MCU 源码部分 Apache-2.0，保留组成说明而非整包改标纯 Apache。
- `RuntimeNoticeCollector` 仅扫描实际 release AAR/JAR；AAR 只递归 `classes.jar` / 平铺 `libs/*.jar`，普通 JAR 不再递归。文本名锚定且后缀白名单，保留 `THIRD_PARTY_NOTICES.md`；`lint.jar` / `CopyrightKt.class` 不再进入输出。候选原文的非 UTF-8、控制字符、空/纯空白、无效归档均明确失败，不能静默截断或替换乱码。
- 原文只按完全相同字节 SHA-256 分组，不归一化换行、BOM 或尾空白；记录每组长度与全部不同来源，并稳定排序坐标/artifact、原文组和来源。当前 **110 artifact / 109 坐标、81 份归档原文、8 个原文组、118 个不同来源**；同一物理来源重复解析不会虚增来源数。8 组构成为：Apache 静态副本（34 来源）、AndroidX 归档 Apache（67 来源）、MCU Apache（1）、Glasmio 附属 Apache 全文（6）、Glasmio NOTICES（7）、根 MIT（1）、MaterialKolor MIT（1）、protobuf BSD（1）。
- 显式保存 `app/src/main/assets/licenses/glasmio-third-party-notices.txt`，**5,151 B**，固定 `v0.1.3` commit `3332b385a4c1578d93366471195a882d1f0173e1`；Git blob `7fdc8a0334a67a65fe7ea7620f752ea77138e5c6`，SHA-256 `E7327A8F2829E07F92E4C582D80A03C439EE2C55BEA46A294490288B1EA820C1`。与六个模块 classes.jar 内原文同字节，去重后保留 6 个归档来源与 1 个锁定上游来源。**顶层 AAR 没找到 NOTICES 不等于嵌套 classes.jar 没有**，上一单步的顶层检索边界已修正。
- 根 `LICENSE` 原文生成到 `assets/licenses/morrow.txt`（**1,129 B**，SHA-256 `413D645C35F775A506D077C904B474ACE630C2A747A283F7CE0F18903CF9F580`），也保留于原文汇总。任务输入包含解析 artifact/坐标、策略版本 **3**、构建脚本、根 MIT 与静态 assets 目录，新增或改动静态原文会使生成重跑；无需构建时联网。
- `runtime.txt` 现 **111,134 B**（旧 928,540 B，减少 **88.03%**；上一轮 73,719 B 未含三条静态全文），SHA-256 **`A4BD743568C0BD3A53B9B0AC2B7082B0676709AFC0F649A22374D430A8837480`**；两轮强制生成字节摘要一致，全部 8 组原文长度/摘要已独立复核，无 lint.jar 来源或 CopyrightKt.class，无 U+FFFD。
- **聚焦验证，不是全量**：`verifyRuntimeNotices` 的 **26 项内存回归**通过（缺口名单必须为空、三份静态正文存在且含必需来源/版权标记、MaterialKolor 两份不得互相顶替），依赖于 generate/preBuild 的执行链；应用层聚焦测试共 **13 项**：`RuntimeNoticesAssetTest` 5 项、`LicenseCatalogTest` 5 项、`LicensesSheetTest` 3 项，**0 failures / 0 errors / 0 skipped**。当前 test-results 目录只有这 3 份 XML 的 13 项结果，旧 366 项全量报告已被替换；不能汇总当前目录声称 366 或 379 项全量通过。
- 运行 `:app:testDebugUnitTest --tests com.morrowviento.morrow.RuntimeNoticesAssetTest --rerun :app:assembleRelease` 成功（88 任务：8 执行 / 80 up-to-date），含同次 `lintVitalRelease`、`mergeReleaseAssets`、`packageRelease`；release 代码/R8/资源裁剪复用有效产物（UP-TO-DATE），**不宣称又强制跑了 R8 或全量 debug/lintDebug**。此前一次运行因新增断言的坐标正则漏了来源行前缀 `- ` 而 FAILED，修正断言后重跑通过——是测试写法错误，不是生成器或资产缺陷。
- **最新审计 APK**：`app/build/outputs/apk/release/app-release.apk`，仍 **4.1.0 / 69**，**5,196,693 B**，SHA-256 **`320E5E90984762BEF2177C3A6AE7814BD268FF7B8EF15EA0B6553520024D1DF9`**；ZIP CRC 通过，**145 个文件条目**。`assets/licenses/` 下 7 份资产（runtime / morrow / ying / glasmio-third-party-notices / apache-2.0 / materialkolor / material-color-utilities）全部与各自源字节一致；目标图像文件名、10 项 AAR 图像摘要、PNG/WebP 魔数、资源表匹配均 **0**，无嵌套归档；dex 内已含 `LicenseDocument` 与阅读页文案，确认不是旧包。**这才是当前构建输出；`FDB75BB6…72E8`、`3A0AE250…4B6`、`921150…4AE1` 均为已替换的历史包，不得用于正式 70 包放行**。
- **正文缺口已闭合为 0**：上一轮的 35 个精确坐标（34 个 Apache 声明 + MaterialKolor 混合声明）全部由新增静态全文覆盖，`expectedTextGaps` 已是空集；清单为每个缺文本坐标打印静态正文文件名 + SHA-256，并注明「归档无 NOTICE 时 §4(d) 无内容可转发」或「MIT 要求保留版权与许可声明」。补一份不足以覆盖 MaterialKolor（必须 MIT + MCU Apache 两份），构建期会以 2/2 计数判定。
- `verifyRuntimeNoticeCompleteness` 本轮**正向通过（退出 0、`TEXT_COVERAGE_GAPS: 0`、无 `NOT RELEASE READY`）**；上一轮已做负向验证（35 缺口时退出 1）。此任务**没有接到 assembleRelease/CI**，正式发行前必须单独通过它；生成成功不等于许可审核或发行完成——全量回归、4.1.1/70 与 #20/#21 仍未做。

### 许可改动后的全量回归（2026-11-18 完成，取代 366 旧基线）
- 命令（仓库根，工具链绝对路径；keystore 仍由用户级 `~/.gradle/gradle.properties` 外部注入，未写入仓库或日志）：
  `:app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug :app:assembleRelease --console=plain` → **`BUILD SUCCESSFUL in 3m 56s`，113 个任务：17 executed / 96 up-to-date**。
- 全量单测：**379 项，47 份 XML，0 failures / 0 errors / 0 skipped**（先前清空 `app/build/test-results/testDebugUnitTest/`，因此是本次重跑的真实结果，不是旧报告残留）。对账：366（许可改动前基线）+ 13（`RuntimeNoticesAssetTest` 5 + `LicenseCatalogTest` 5 + `LicensesSheetTest` 3）= 379，**无用例因新代码失效或跳过**。
- `lintDebug`：**0 errors / 15 warnings**。15 条与基线同一组——7 条版本提示（AGP/Gradle 9.9.1、lifecycle-compose、coroutines、gson、robolectric 等）、4 条 `UseKtx`、`DataExtractionRules` 1、`UseTomlInstead` 2；**许可/README 改动未引入新警告**，未 suppress、未借升级依赖消警告。报告：`app/build/reports/lint-results-debug.xml|.html`。
- debug：`app-debug.apk` **80,986,212 B**，SHA-256 `BEF60F9A55F93ABD07BC1B41DDC29867F85B6312ACEE3BE6108F82FD45316B96`（调试包，不能覆盖正式安装）。
- release：沿用外部注入原密钥，`packageRelease` 复用上一单步已强制重打的产物（UP-TO-DATE，产物与其源码一致），**`app-release.apk` 5,196,693 B / SHA-256 `320E5E90984762BEF2177C3A6AE7814BD268FF7B8EF15EA0B6553520024D1DF9`**，与上一单步审计包**逐字节相同**——说明该审计结论（145 条目、7 份资产与源一致、十项 Apple 图像 0 匹配）对这一份产物继续成立，但**仍不等于正式 70 包放行**。`aapt2 dump badging`：`com.morrowviento.morrow`，versionCode **69** / versionName **4.1.0**，minSdk 33 / targetSdk 37。
- 签名：`apksigner verify --print-certs` V2 通过，证书 `CN=Chaoxi, OU=Mobile, O=MorrowViento, C=CN`，SHA-256 **`8894605E2B84AEC593D95E9DFB8C315D18CD48D99FAC1E29F69CAA4B78B18D02`**，与正式 `morrow-68-4.0.0-release.apk` **完全一致**（同签名，可原位升级）；SHA-1 `5ce35e71…1b7`、MD5 `3c6d68aa…75b` 亦一致。
- 重复核验：本步再次执行 `:app:verifyRuntimeNoticeCompleteness` → **BUILD SUCCESSFUL，退出 0**，无 `NOT RELEASE READY`；release APK 内 `assets/licenses/` 7 份资产重新逐字节比对，`apache-2.0.txt` / `glasmio-third-party-notices.txt` / `material-color-utilities.txt` / `materialkolor.txt` / `ying.txt` 与源 **MATCH**，`runtime.txt` 111,134 B、`morrow.txt` 1,129 B 为生成物。
- 本步只跑验证与构建，**未改任何源码、版本、依赖、CI、签名或包名**；`docs/MANUAL-TEST.md` 未编辑，回读 SHA-256 仍为 `93FBCB61575E9B06AFED6BF0971BFC7818E6274FD5C2F41F712B29E3042C8A4A`。构建日志写在仓库外（`../build-regression-2026-11-18.log`），不进版本库。

### 4.1.1 / 70 正式候选包：已构建、已核验（2026-11-18，#20 完成）
- 改动面只有 `app/build.gradle.kts`：`versionCode 69→70`、`versionName "4.1.0"→"4.1.1"`。**未动**依赖、AGP/Kotlin、schema、包名、CI、签名配置、proguard；代码里没有硬编码版本断言（`SettingsViewModel`/`StartupTrace` 均读 `PackageInfo`），测试无用例依赖旧版本号。
- 命令：`:app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug :app:assembleRelease :app:verifyRuntimeNoticeCompleteness` → **`BUILD SUCCESSFUL in 8m31s`，114 个任务：53 executed / 61 up-to-date**（版本变化导致 KSP/Kotlin/R8/lint 全链重跑）。`verifyRuntimeNoticeCompleteness` 在本构建中执行并通过（整体退出 0，无缺口输出）。
- 回归：**379 项 / 47 份 XML / 0 failures·errors·skipped**（同样先清空旧 `test-results`）；`lintDebug` **0 errors / 15 warnings**，仍是版本提示 7 / `UseKtx` 4 / `DataExtractionRules` 1 / `UseTomlInstead` 2，**版本改动未引入新告警**。
- 元数据：`aapt2 dump badging` → `com.morrowviento.morrow`，**versionCode 70 / versionName 4.1.1**，minSdk 33 / targetSdk 37，compileSdk 37；`output-metadata.json` release 与 debug 均为 70 / 4.1.1。
- 签名：`apksigner verify --print-certs` V2 通过，证书 `CN=Chaoxi, OU=Mobile, O=MorrowViento, C=CN`，SHA-256 **`8894605E2B84AEC593D95E9DFB8C315D18CD48D99FAC1E29F69CAA4B78B18D02`**，与正式 `morrow-68-4.0.0-release.apk` **完全一致**（同签名、可原位升级；实际保数据升级仍待真机）。
- 包体：`app-release.apk`（4.1.1 / 70）**5,196,689 B**，SHA-256 **`7F92400A49F50C1BCA041D2E1125E25AC3160C32C637DA541AEC8770AA20D963`**，**145 个条目**、`testzip` CRC 通过。`assets/licenses/` 7 份：`ying.txt` / `glasmio-third-party-notices.txt` / `apache-2.0.txt` / `materialkolor.txt` / `material-color-utilities.txt` 与源 **MATCH**，`runtime.txt` 111,134 B、`morrow.txt` 1,129 B 为生成物。无 `.apk/.aar/.jar/.zip` 嵌套归档。
- Apple 素材不存在性（对最终 70 包重做）：包内**图像条目计数 0**，故无 PNG/WebP 可命中；文件名与资源表检索 `tapback` / `listening` / `adaptive_audio` **0 命中**（`aapt2 dump resources` 1,341 行全量检索），不存在改名或重压缩的绕过路径。dex 仅 `classes.dex`，含 `LicenseDocument` 标记，可确认不是旧包。
- debug：`app-debug.apk` **80,500,165 B**，SHA-256 `C048D03A7F88BA8724F6699D06E27C8DC07C05C45F86D528866C22453B043284`（调试包，不能覆盖正式安装）。
- 本地交付副本：`../morrow-70-4.1.1-release.apk`，5,196,689 B，SHA-256 与源包**逐字节一致**。**仅为本地副本，未上传到任何 Release、未改 manifest、未打 tag。**
- 与 69 审计包的关系：70 包不是 69 的字节复用（版本码/版本名变化，5,196,693→5,196,689 B），因此上述核验是在**最终待上传包**上重做的；69 的审计结论自此仅作历史记录。

### #21 提交、推送与正式发布实录（2026-11-18）
- 提交（显式路径暂存，未用 `git add -A`，`docs/MANUAL-TEST.md` 始终排除）：`505c822` `feat(ui): 完成 4.1.1 日历动画、小部件重构与开源许可页`（38 文件，+3,000/−474，含 LICENSE、5 份静态许可 assets、版本 70）；`fbc1a89` `chore(docs): 重写 README 并同步 4.1.1/70 发布前证据`。
- 推送：`git push origin main` → `63ecd6b..fbc1a89`；`git ls-remote origin refs/heads/main` 回读 `fbc1a89`，工作区随后仅剩受保护的 MANUAL-TEST 本地修改。
- 发布前核验（令牌取自本机 Git 凭据，未打印）：`/releases/latest` = `v4.0.0-68`；`/releases/tags/v4.1.1` 与 `/git/refs/tags/v4.1.1` 均 **404**，确认标签未被占用。
- 发布路径：**draft 优先**——建草稿 Release（id `449587411`，`tag_name=v4.1.1`、`target_commitish=main`、标题「朝夕 4.1.1」、`draft=true`、`prerelease=false`）→ 上传 `morrow-70-4.1.1-release.apk`（HTTP 201，`size=5,196,689`，`digest=sha256:7f92400a…d963`）→ **把已上传资产重新下载**并本地算摘要（`7F92400A…D963`，与本地包逐字节一致）→ `PATCH draft=false` 转正式（HTTP 200，`published_at` `2026-11-18T06:05:24Z`）。
- 发布后回读：`/releases/latest` = `v4.1.1` / 「朝夕 4.1.1」/ `prerelease=false` / 资产 `morrow-70-4.1.1-release.apk` 5,196,689 B / `sha256:7f92400a…d963` / 下载地址 `.../releases/download/v4.1.1/morrow-70-4.1.1-release.apk`；`git ls-remote --tags origin v4.1.1` = `fbc1a89`。
- `update/manifest.json` 用 `/releases/latest` 的真实字段回填（tag / name / body / published_at / prerelease / assets 的 name+url+size+digest），独立提交；未手抄摘要。
- **推送：先受阻、后已补齐并回读。** 代码与文档前两个提交 `505c822`、`fbc1a89` 当时推送成功；第三个提交（manifest + 发布文档）`git push` 连续失败——`github.com:443` 经 WorkBuddy 自带代理 `127.0.0.1:59466` 返回 `CONNECT tunnel failed, response 502`（同时段 `api.github.com` 正常 200，直连超时）。**改用本机 AgentRouter 链路的 HTTP 代理 `http://127.0.0.1:10809`（Xray，PID 11476，仅监听回环）后一次推送成功**：`fbc1a89..4a31259  main -> main`；`git ls-remote` 回读 `refs/heads/main=4a312592382a5ae6cf84a092a41c5849b9caa53e`，`raw.githubusercontent.com/.../update/manifest.json` 返回 200（2,415 B），按语义与本地完全一致（仅 CRLF/LF 差异，索引为 LF）。**清单确已同步，可宣称 #21 完成。** 该次推送用 `git -c http.proxy=...` 临时指定，未写入仓库或全局 git 配置。
- 命名决策：用户明确选择 **tag `v4.1.1` + 标题「朝夕 4.1.1」（不带 code）**，因此走手工 API 发布，**未修改 `scripts/release.sh`**（其默认仍为 `v<name>-<code>`）。资产名保持 `morrow-70-4.1.1-release.apk`，客户端从资产名取 code，缺 code 会检测不到更新。
- 未做：未改 CI/密钥/包名/schema，未覆盖旧 tag 或旧 Release，未纳入 MANUAL-TEST，未安装或卸载设备。

### 发布后修复：设置页状态栏割裂（2026-11-18，提交 `956461d`）
- 现象：进设置页后状态栏那一截与页面之间有一道横线。根因与日历页同一处——**在 `safeDrawingPadding()` 约束的区域内自绘壁纸**，位图从状态栏下沿才开始，渐变原点与照片 cover 裁剪比例都和系统栏那一截对不上。日历页在 4.1.1 已改成「背景由全窗口宿主提供」，设置页漏了。
- 修复：`SettingsScreen` 删掉 `GlassBackdrop()` 与 import，底色交回 `GlassBackdropHost`；由于设置页是**盖在仍在组合的首页之上**（日历是槽内互斥），额外让首页在设置页打开期间整块让位（`if (!calendarActive && !showSettings)`），否则标题与卡片会从壁纸上透出来变成两页重叠。裁剪页自身的不透明底、返回键处理、`safeDrawingPadding` 边界都没动。
- 验证：`testDebugUnitTest --rerun + lintDebug + assembleDebug` → `BUILD SUCCESSFUL in 3m22s`，**379 项 / 47 XML / 0 failures·errors·skipped**，lint **0 errors / 15 warnings（无新增）**。**没有真机/截图证据**：浅色深色、图片背景档、大字体下的观感仍待设备确认。因为只改界面层、不涉及许可与打包，未重跑 `assembleRelease`——**已发布的 70 包早于本提交，不再与源码逐字节对应**。
- 版本处置：源码仍是 4.1.1 / 70，但**不得用 70 号重发**（同 code 不同字节，已装用户收不到）。下次发行另起 **4.1.2 / 71**，按 #20/#21 的同一套核验走。

### 逐组件许可审阅（2026-11-18，`docs/LICENSE-AUDIT.md`）
- 做法：不采信生成器自述，按 109 个坐标回 `~/.gradle/caches/modules-2/files-2.1` 重开归档，独立重算每个正文的 SHA-256、重建「坐标 → 正文组」映射（109/109 定位成功）。
- 结论：**107 Apache-2.0 / 1 BSD-3-Clause / 1 MIT+MCU 混合**；正文载体 8 组（三份 Apache 均含 `END OF TERMS AND CONDITIONS` 与第 9 条，非摘要版）；35 个坐标归档内无任何许可文本，由静态 `apache-2.0.txt` 承担 §4(a)；§4(d) 仅 Glasmio 六模块的 `THIRD_PARTY_NOTICES.md` 需转发，已随包，其余 103 个归档内无 NOTICE。
- **两处发现**：
  1. **protobuf BSD-3 缺版权行**——`datastore-preferences-external-protobuf:1.3.0` 的 `LICENSE.txt`（1,434 B）从「Redistribution and use…」开始，无 `Copyright`/`Google`；BSD-3 第 1 条要求保留版权声明，原样转发不足以免责。**4.1.2 / 71 已闭合**（见下节「protobuf 归属」）。
  2. **清单重复记录**——`androidx.core:core:1.18.0` 在解析结果里出现两次，`runtime.txt` 因此多打一条相同记录、artifact 计数虚高 1。已在 `app/build.gradle.kts` 加 `distinctBy(坐标, 文件, classifier)`，`runtime.txt` 111,134 → **110,947 B / SHA-256 `4773AF4B…48A4`**，坐标 109 条且无重复。
- 仍不覆盖：源码文件头逐文件版权、R8 后类级残留、ying 派生边界逐文件判定、Apple 素材授权本身。`verifyRuntimeNoticeCompleteness` 退出 0 只证明「该有文本的坐标都有文本」，不等于归属审阅完成。

### 4.1.2 / 71：结构重构与 protobuf 归属（2026-11-18）
- **重构范围（无行为变更）**：把五个单文件巨物按职责拆开，只改可见性与文件级注释，正文用「行多重集比对」逐文件核对，丢行数均为 0。
  - `ui/settings/`：`SettingsScreen` 1,345 → `SettingsScreen`(461) + `SettingsRows`(363) + `SettingsGlyphs`(306) + `BackdropCropScreen`(128) + `LicensesSheet`(205)。行/弹层/图标/裁剪/许可各自成文件，设置页主体只留组合与状态编排。
  - `ui/glass/`：`GlassBottomBar` 989 → `GlassBottomBar`(674) + `GlassBottomBarParts`(366)（融合玻璃、选中块、三格图标、加号移出，底栏本体只算几何与串状态）；`GlassTheme` 690 → `GlassTheme`(475) + `GlassBackdrop`(236)（壁纸采样与宿主从主题材质里分开）。
  - `ui/home/`：`HomeScreen` 841 → `HomeScreen`(549) + `HomeSections`(328)（日历槽、大标题、排序胶囊、列表/空态）。
  - `ui/calendar/`：`CalendarScreen` 806 → `CalendarScreen`(357) + `CalendarViews`(477)（动画/几何与视图排版分开）。
  - 拆分工具：`../tools/kt_split.py`（仓库外，按声明移动 + 自动裁剪 import；`by` 委托的 `getValue/setValue` 需按写法判断，否则会被当成未使用删掉）。
- **protobuf 归属**：新增 `app/src/main/assets/licenses/protobuf-bsd-3.txt`（2,482 B，SHA-256 `835c9e42…5320`）= `Source:`/`License:` 头 + 上游 protobuf v3.25.5 完整 LICENSE（含 `Copyright 2008 Google Inc.  All rights reserved.` 与代码生成条款）。`RuntimeNoticePolicy.attributionFiles()` 与 `fallbackFiles()` 分开：前者**坐标命中就无条件追加**、文件缺失直接失败；后者仍只补缺口，所以归档模板消失依然是新增缺口。清单里 protobuf 一条同时给出「归档原文 1 项、静态归属 1 项」、两份 SHA-256 与归属说明；`runtime.txt` 110,947 → **116,447 B / `d1508af0…1449`**（9 个原字节组 / 119 个来源 / 80 份归档原文）。
- **许可目录**：`LicenseCatalog` 增加「Protocol Buffers / BSD-3-Clause」第 8 条，设置页可直接读到带版权行的上游全文；`RuntimeNoticesAssetTest` 与 `LicenseCatalogTest` 各补断言（归档模板与归属正文并存、不互相顶替）。
- **版本与验证**：`versionCode 71`、`versionName 4.1.2`（公开展示不附 code）。`:app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug :app:assembleRelease :app:verifyRuntimeNoticeCompleteness` → `BUILD SUCCESSFUL in 6m15s`（114 任务，63 executed）；**380 项 / 47 XML / 0 failures·errors·skipped**（379 + 1 项新的 protobuf 归属测试）；lint **0 errors / 15 warnings**（同基线，无新增）。
- **APK**：5,199,013 B / `601b3beb…73a6`，**146 条目**；`aapt2` **71 / 4.1.2** / minSdk 33 / targetSdk 37；`apksigner` 证书 `8894605E…8D02` 与正式 68 一致；**8 份** `assets/licenses/` 资产（6 份源文件 MATCH、`runtime.txt`/`morrow.txt` 为生成物）；图像条目 0、无嵌套归档、资源表 1,341 行检索 `tapback|listening|adaptive_audio` 0 匹配；`classes.dex` 含 `LicenseDocument` 与 `protobuf-bsd-3` 标记。debug 80,518,929 B / `4f51bf8c…b171`。本地副本 `../morrow-71-4.1.2-release.apk` 与源包逐字节一致。

### #22 4.1.2 / 71 提交、推送与正式发布实录（2026-11-18）
- 提交（显式路径暂存，`docs/MANUAL-TEST.md` 始终排除）：`5196f10` `refactor(ui): 按职责拆分五个超大 UI 文件`（13 文件，纯声明搬移）；`ba6d023` `chore(license): 补齐 protobuf 的 BSD-3 版权归属`（5 文件，含版本切 71 / 4.1.2）；`f9da8d8` `docs: 回填 4.1.2 / 71 的重构、许可与发布证据`（README + 4 份 docs）。
- 推送：`git push origin main` → `cfd745a..f9da8d8`，`pre-push` 钩子报告「127.0.0.1:10809 已在监听，无需唤醒」；`git ls-remote` 回读 `refs/heads/main = f9da8d8`。
- 发布前核验：`/releases/latest` = `v4.1.1`；`/releases/tags/v4.1.2` 与 `/git/refs/tags/v4.1.2` 均 **404**，标签未被占用。
- 发布路径（沿用 #21 的 draft 优先）：建草稿（id `449651404`、`tag_name=v4.1.2`、`target_commitish=main`、标题「朝夕 4.1.2」）→ 上传 `morrow-71-4.1.2-release.apk`（HTTP 201，`size=5,199,013`，`digest=sha256:601b3beb…73a6`，asset id `762396303`）→ **把已上传资产重新下载**（5,199,013 B）本地算摘要与 `cmp` **逐字节一致** → `PATCH draft=false` 转正式（HTTP 200，`published_at` `2026-11-18T07:32:07Z`）。
- 发布后回读：`/releases/latest` = `v4.1.2` /「朝夕 4.1.2」/ `prerelease=false` / 资产 5,199,013 B / `sha256:601b3beb…73a6` / `.../releases/download/v4.1.2/morrow-71-4.1.2-release.apk`；`git ls-remote --tags origin v4.1.2` = `f9da8d8`，即发布时刻的 main。
- `update/manifest.json` 用 `/releases/latest` 的真实字段整体重写（tag / name / body / published_at / prerelease / assets 的 name+url+size+digest），4,334 B，未手抄任何摘要；单独提交 `d3f3e93` 并推送，`raw.githubusercontent.com` 回读 200 且与本地**语义完全一致**。**因此 main 现为 `d3f3e93`，而 tag `v4.1.2` 仍指向 `f9da8d8`**（与 4.1.1 的 `fbc1a89` / `4a31259` 同例：清单回填必然是 tag 之后的提交）。
- 网络：`api.github.com` 与 `uploads.github.com` 经仓库级 `http.proxy=http://127.0.0.1:10809` 全程 200/201，**本批未再出现 59466 的 502**。
- 命名决策沿用用户 4.1.1 时的指定：tag 与标题**不带 code**，资产名保留 code；仍走手工 API，未改 `scripts/release.sh`。
- 未做：未改 CI/密钥/包名/schema/生产依赖，未覆盖旧 tag 或旧 Release，未纳入 MANUAL-TEST，未安装或卸载设备。

### #23 设置页透底修复、按钮统一、字体设置与节日皮肤（2026-11-18）
- **透底 BUG 的根因**：4.1.2 里为了让设置页不透出首页，写成了 `if (!calendarActive && !showSettings) 首页 else 日历`。设置页打开时 `calendarActive=false / showSettings=true` → 条件为假 → **掉进 else 渲染日历**，正好画在设置页底下，半透明底色一透就看见。改成三路互斥：`when { showSettings -> 不画; calendarActive -> 日历; else -> 首页 }`（`HomeScreen.kt` 内，注释写明了这个坑）。
- **按钮统一**：新增 `ui/component/PageChrome.kt` 作为跨页尺寸的单一来源（`CircleButtonSize 34dp`、`CircleGlyphSize 17sp`、`HeaderHorizontal 16dp`、`HeaderTop 12dp`、`HeaderBottom 4dp`、`ChipHeight 34dp`、`ActionButtonSize 56dp`）。首页「⋯」32→34、设置返回 32→34 且 `‹` 20→17sp、日历导航/关闭字形 15→17sp；三页头部内边距 22/18、12/16、14/14 全部改 16/12/4；日历列表与视图切换行 14→16dp；详情页 52dp 圆钮与 56dp 主按钮统一 56dp；裁剪页底部 48→56dp；日历「今天」28→34dp（`compact=false`）。
- **字体设置**：数据层 `data/settings/AppFont.kt`（`AppFont` + `FontStore` + `RECOMMENDED_FONTS`）。导入走 SAF，32 MB 上限，按 sfnt 魔数（`0x00010000` / `OTTO` / `true` / `ttcf`）校验，文件名 `font-<hash8>-<显示名>.<ext>`；`installed()` 列目录、`delete()` 只取最后一段防越权、`exists()` 供展示层回落。UI `ui/settings/FontSheet.kt`：每行**用自己的字体渲染名字**（就地预览 + 顺带证明文件能加载），点击即切换并关闭，删除当前字体自动回落系统默认；4 款 OFL 字体只给项目主页链接。
- **全局字体为什么不用 resolver**：`FontFamily.Resolver` 是 **sealed interface**，跨模块实现直接编译失败（已实测）。改用 `ui/component/AppText.kt` 提供与 material3 同签名的 `Text`，只对 `style.fontFamily == null` 的文本注入当前字体；24 个文件把 `import androidx.compose.material3.Text` 换成 `ui.component.Text`，调用点零改动。`AppFontTheme` 同时覆盖 `LocalTextStyle`，让 Glasmio 内部读默认样式的控件也跟随，并把字体加进 `GlassTheme` 的 `key(...)`（换字体重建子树，否则缓存的文本测量会按旧行高排版）。
- **节日皮肤**：`ui/glass/FestivalSkin.kt`（`FestivalSkin` + `festivalSkinOf(LocalDate)` + `LocalFestivalSkin`）。八个节日（春节/元宵/端午/七夕/中秋/重阳/腊八/除夕）各有祝语与一支主色；**中元有意不给皮肤**。柔光画在 `GlassBackdrop` 的 `onDrawBehind` 末尾（径向渐变，深色 0.22 / 浅色 0.16，色值淡到同色 alpha=0），因此也会进入玻璃采样——玻璃后面那层才是节日色。首页标题下加一行「节日 · 祝语」，折叠态隐藏。
- **数据与备份**：`LegacySettingsKeys` 新增 `APP_FONT` / `APP_FONT_NAME` / `FESTIVAL_SKIN`；`AppSettings` 增 `font` 与 `festivalSkin`（缺键按开）。`applySettings` **写节日皮肤但不写字体**（字体文件不随备份，写入只会得到指向不存在文件的偏好）；「清除全部数据」会一并 `setFont(SYSTEM)` 并清空 `filesDir/app_fonts/`。
- **验证（当时，debug 层）**：`testDebugUnitTest --rerun + lintDebug + assembleDebug` → `BUILD SUCCESSFUL in 3m51s`；**394 项 / 49 XML / 0 failures·errors·skipped**（380 + 11 `FontStoreTest` + 3 `FestivalSkinTest`）；lint **0 errors / 15 warnings**（同基线；`Uri.parse` 按 KTX 提示换成 `toUri`，避免新增第 16 条）；debug 包 80,856,944 B / `4348269A…CE89`。release 与发布见 #24。
- 测试里踩到的两个坑：`Uri.lastPathSegment` 在 Windows 风格路径上没有 `/` 可切，会把整条路径当文件名（改用 `File(uri.path).name`）；Robolectric 下 `file://` 的 `contentResolver.openInputStream` 不稳，`file` scheme 直接走 `File.inputStream()`。

### #24 4.1.3 / 72 构建、核验与发布实录（2026-11-18）
- **版本**：只改 `app/build.gradle.kts` 的 `versionCode 71→72` / `versionName 4.1.2→4.1.3`（公开展示不附 code）；依赖、AGP、Kotlin、schema、包名、CI、签名配置、proguard 均未动。
- **构建**：`testDebugUnitTest --rerun + lintRelease + assembleRelease` → `BUILD SUCCESSFUL in 8m 7s`（90 任务 / 47 执行）。
- **测试**：**394 项 / 49 份 XML / 0 failures·errors·skipped**。注意本工程只有一个单测变体 `testDebugUnitTest`（不存在 `testReleaseUnitTest`），跑 release 前的回归要指定它。
- **lint**：`lintRelease` 与 `lintDebug` 均 **0 errors / 15 warnings**（同一组，无新增）。
- **许可门槛**：`verifyRuntimeNoticeCompleteness` 退出 0，许可收集器 28 项内存回归通过；`runtime.txt` **116,447 B / `d1508af0daca478bccd9d29b62991dc5847f5adb3246a5692be7881d8f6d1449`**、9 组、109 个坐标条目，**与 4.1.2 完全相同**——字体不随包、未引入第三方正文，protobuf 的 `静态归属 1 项` + `归属正文 protobuf-bsd-3.txt SHA-256 835c9e42…5320` 仍在。8 份许可资产中 6 份源文件（`apache-2.0` / `glasmio-third-party-notices` / `material-color-utilities` / `materialkolor` / `protobuf-bsd-3` / `ying`）与 APK 内**逐字节 MATCH**。
- **产物**：`morrow-72-4.1.3-release.apk` **5,231,781 B**，SHA-256 `10258ec201690dcff580a405ff7d2e476c0a664d048e0f90734f6d9efcc9f39c`，146 条目；`aapt2 dump badging` = `versionCode 72` / `versionName 4.1.3` / minSdk 33 / targetSdk 37；`apksigner verify --print-certs` 证书 SHA-256 `8894605e2b84aec593d95e9dfb8c315d18cd48d99fac1e29f69caa4b78b18d02`（`CN=Chaoxi`），与 68/70/71 一致 → 可原位升级；图像条目 0、无嵌套归档；`aapt2 dump resources` 1,341 行、`apple|sf-symbol|San Francisco|Tapback|Cupertino` **0 匹配**；副本 `../morrow-72-4.1.3-release.apk` 与构建产物 `cmp` 一致。
- **提交**：`e47ed3e` `feat(ui): 字体设置与节日皮肤，修设置页透底并统一头部尺寸`（41 文件；因 `HomeSections`/`CalendarScreen`/`SettingsScreen` 同时承载按钮统一与字体 import 替换，无法在不交互暂存的前提下拆开）、`f5553f7` `docs: 回填 4.1.3 / 72 的构建核验证据`。`docs/MANUAL-TEST.md` 全程显式排除，摘要复核仍为 `93FBCB61…8A4A`。
- **推送**：`git push origin main` → `e99a1ef..f5553f7`，`pre-push` 钩子报告「127.0.0.1:10809 已在监听，无需唤醒」；`git ls-remote` 回读 `refs/heads/main = f5553f7`。
- **发布前置**：`/releases/tags/v4.1.3` 与 `/git/refs/tags/v4.1.3` 均 **404**，标签未被占用。
- **发布路径**（沿用 #22 的 draft 优先）：建草稿（id `449719768`、`tag_name=v4.1.3`、`target_commitish=main`、标题「朝夕 4.1.3」）→ 上传 `morrow-72-4.1.3-release.apk`（HTTP **201**，`size=5,231,781`，`digest=sha256:10258ec2…f39c`，asset id `762634465`）→ **把已上传资产重新下载**（5,231,781 B）算摘要与 `cmp` **逐字节一致** → `PATCH draft=false` 转正式（HTTP 200，`published_at` `2026-11-18T08:48:37Z`）。
- **发布后回读**：`/releases/latest` = `v4.1.3` /「朝夕 4.1.3」/ `draft=false` / `prerelease=false` / 资产 5,231,781 B / `sha256:10258ec2…f39c` / `.../releases/download/v4.1.3/morrow-72-4.1.3-release.apk`；`git ls-remote --tags origin v4.1.3` = `f5553f7`，与发布时刻的 main 同一提交。
- `update/manifest.json` 用 `/releases/latest` 的真实字段整体重写（3,307 B，未手抄摘要），单独提交并推送；因此 **main 前进到清单提交，而 tag `v4.1.3` 仍指向 `f5553f7`**（与 4.1.1 / 4.1.2 同例）。
- **网络**：`api.github.com` / `uploads.github.com` / 资产下载全程走仓库级 `http.proxy=http://127.0.0.1:10809`，**未出现 59466 的 502**。
- **未做**：未改 CI / 密钥 / 包名 / schema / 生产依赖；未覆盖旧 tag 或旧 Release；未纳入 MANUAL-TEST；未安装或卸载设备。

### 仍未完成 / 未验证
- `README.md` 与设置页许可 UI **本轮已完成**（见上），但**只在 Robolectric/Compose 测试与 release 构建层面验证**，真机未验：弹层滚动与长文本帧率、大字体、浅色主题下的可读性都还没有设备证据。
- `domain/time/LunarCalendar.kt`：自实现农历/压缩年表，未见明确外部版权；无证据表明复制了不许可来源，也未完成全部来源审核。
- `docs/RELEASE.md` 已随 4.1.2 发布同步到当前基线（`v4.1.2` / id `449651404` / 8 份许可资产 / 380 项）。脚本 `scripts/release.sh` 仍保留旧的 `v<name>-<code>` 命名，与手工发布流程不一致——下次若要脚本化发布需先改这一处。
- 仍缺的是**人工版权/NOTICE 归属审阅结论**（`apache-2.0.txt` 只是协议副本，逐组件源码层归属未逐一审阅）。随包许可文本这一层已闭合：4.1.2 起 protobuf 也有版权行，8 份资产与源一致。设置页展示不改变各组件自身的版权归属。

### 续接顺序（#19 → #20 → #21）
1. **已完成**当前69审计包的AAR/最终APK核验，十项Apple图像不分发；生成器修复与正文补齐后又对最新145文件的包重验，#20正式70包仍须重复核验。
2. **已完成生成器修复与26+5聚焦回归**：protobuf BSD、精确版本允许集、受控归档与文本、原字节去重/全来源、根MIT与Glasmio锁定NOTICES。
3. **已完成正文缺口补齐（35→0）**：新增 `apache-2.0.txt`、`materialkolor.txt`、`material-color-utilities.txt`，均带 Source/License 头、逐字节核对上游/Gradle 缓存原文；`expectedTextGaps` 收敛为空集，`verifyRuntimeNoticeCompleteness` 退出 0。仍不等于逐组件源码层版权归属审阅完成。
4. **已完成设置页可读许可**：新增 `LicenseCatalog.kt`，`LicensesSheet` 改为目录→全文（有界 `LazyColumn` + IO 读 assets + 返回键先回目录），Glasmio 已改为 Apache-2.0；3 项 Compose 真实组合测试通过。
5. **已完成 README 重写**：清除 WorkManager / 22 测试 / 仅年重复 / 旧 GlassPress / 旧里程碑 / 仓库外计划，补齐当前功能、构建门槛、许可边界，保留包名区别。
6. **已完成许可改动后的全量回归**：379 项单测（47 XML，0 失败/错误/跳过）、`lintDebug` 0 errors / 15 warnings（同基线）、debug 与原签名 release（含 `lintVitalRelease`）构建成功；release 5,196,693 B / `320E5E90…4D1DF9`，证书与 68 一致。**366+13=379 已对账，无因新代码回归**。
7. **已完成 #20 的 4.1.1 / 70**：版本字段只改 `app/build.gradle.kts`；四任务 + 完整性门槛一次跑通，379 项单测 / lint 0 errors·15 warnings / debug 与原签名 release 成功，`aapt2` 70 / 4.1.1，证书同 68，145 条目、7 份资产一致、Apple 素材 0 匹配，最终 **5,196,689 B / `7F92400A…D963`** 并复制到 `../morrow-70-4.1.1-release.apk`。
8. **已完成 #21 发布**：普通提交 `505c822` / `fbc1a89` / `4a31259` 全部推送 main 并回读（第三个提交改走本机 `127.0.0.1:10809` 代理后成功）；确认 `v4.1.1` 未被占用；draft→上传→回下载比对摘要→转正式；`/releases/latest` 与 `git ls-remote --tags` 双向回读一致；远端 `update/manifest.json` 200 且与本地语义一致。CHANGELOG / RELEASE / SIGNING 已同步。
9. **剩余**：人工逐组件版权/NOTICE 审阅；真机验收（视觉/帧率/系统提醒/SAF/G/H/J/L/M）与 **68→71 原位升级保数据**，按 TODO「真机与发布待办」执行。4.1.2 / 71 的提交、发行与清单回填见下节末尾。

### 本阶段环境与受保护文件
- `docs/MANUAL-TEST.md` 既有本地修改 SHA-256 回读 = `93FBCB61575E9B06AFED6BF0971BFC7818E6274FD5C2F41F712B29E3042C8A4A`（未变）。不编辑、不代勾、不提交；本批 diff 检查用 `git diff --check -- . ':!docs/MANUAL-TEST.md'`。
- 本批已有大量修改/新增文件，不能再说 MANUAL-TEST 是唯一工作区改动（详见 git status）。
- 源码 **4.1.3 / 72**；4.1.1 / 70 与 4.1.2 / 71 的 tag、Release 与 manifest 均保留未动，新版另起 tag `v4.1.3`。
- 4.1.3 已运行全量 **394 项**单测、`lintRelease`、原签名 `assembleRelease`（含 `lintVitalRelease`）、`verifyRuntimeNoticeCompleteness`，并复核证书同 68、**8 份**许可资产与源一致、图像与 Apple 素材 0 匹配、最终 SHA-256 已绑定。380 是 4.1.2 的基线、379 是 4.1.1 的基线，别混用。
- 未安装/卸载设备；未做真机视觉/FPS/启动器/升级保数据验收。

## 下一步：用户测试与反馈修复

1. **先导出备份并保留背景原图，再按签名选 feature-update 包**。正式 68 用户优先 release 原位升级；debug 用户使用同 debug 签名。debug↔release 不能直接覆盖，需要用户自行确认备份/卸载/安装/导入；不要默认卸载正式版。
2. 优先测试：详情图标/大按钮与类型动作；每月/月末/闰日推进和撤销；旧已完成重复事件恢复；提醒选中反馈；搜索输入/键盘/返回/详情保持/普通滚动恢复；年历窄屏/大字体/摘要；裁剪移动/缩放/重置/取消/取色/配置变化与保存失败保旧图。
3. 再按 MANUAL-TEST 适用的 AL/A–D/G/H/J/L/M/N 编号回归背景/玻璃/核心数据/系统提醒/SAF/权限/部件/升级。旧搜索浮层样例不代表当前 SearchBar，旧编号适用版本以文档顶部为准；不代勾。
4. N 更新链路须更新端 code 大于安装端，69 对清单 68 无更新正常；PackageInstaller 仍有系统确认。实际 68→69 Room 迁移和设置/提醒/部件保留须真机验证，不把模拟旧库测试等同真机升级。
5. 收到设备/版本/步骤/现象后复现、修复、补回归、重建并核验新包。性能目前仅代码与自动回归证据，无真机测量结论。
6. 本批发布授权已存在；当前 69 审计包已证明十项 Apple 素材不分发，README 与设置页许可 UI 也已完成。但 **#19 仍差许可改动后的全量回归与人工版权归属审阅，正式 70 包未核验，仍不得发行**。完成 4.1.1 全量验证、签名及最终包许可/素材核验后按 RELEASE 发布，披露真机未验项。发布核验成功前清单保持 68。

## 现行工程约束

- Glasmio 锁 **0.1.3**，业务不直接 import，仅 ui/glass 封装；查该版源码/AAR，不照 main/旧摘录写 API。不引入 NDK/CMake、WorkManager、navigation-compose。
- 真玻璃可采样：统一 Clear、glassFrost 默认 0.62；模糊 `0.5 + 25*t` dp、色纱 `0.26*t*t`。不恢复不透明 Regular/旧曲线；GlassAppearance 默认，减透明不交库关闭采样。
- LocalGlassDark/LocalAccentPalette 统一主题；浮层同页不切断 backdrop，禁止恢复自绘 GlassPress/Material 方形 ripple。壁纸后台渲染/上一帧/三槽缓存，丝缎首张同步。图片只在私有目录，备份含种子不含图片本体。
- 事件操作锁 → 撤销持久化锁；内部写入口不重复加锁/嵌套重入。撤销仅最近一次，立即落库+快照，前台 6 秒/后台暂停/重开 10 分钟内给满；编辑和分类修改清旧快照，保留 NonCancellable 提交边界。
- 分类名称为身份，重命名级联，删除占用分类先迁移不删事件。REPLACE Room 事务、MERGE 只写 incoming 行（仍读取全表供对账）；Room/DataStore 分开提交，不承诺跨存储原子性。ying 未知枚举/默认分类兼容不顺手改。
- 提醒临近 12 小时优先 AlarmClock，远期精确/无权限降级；广播 goAsync/finally finish；导入/清空在锁内取消消失 id 后重排。
- 部件 RemoteViews 非实时真玻璃；保留 notifyAppWidgetViewDataChanged + adapter data URI 重绑/可变模板 PendingIntent，删除实例清配置、默认样式不含 eventId，零点刷新，updatePeriodMillis=0；不做 ColorOS 私有卡片。
- API33 日期 Instant→atZone→toLocalDate，不用 API34 LocalDate.ofInstant；SAF 覆盖写 wt；RemoteViews 分隔线 ImageView，LazyColumn 嵌套有界。测试 DataStore 独立文件/受控 scope，取消并等待写任务结束后清理。
- MANUAL-TEST 编号/checkbox 由用户维护，提交逐次授权，禁 git add -A；已有本地修改不纳入本次提交。**用户已授权本批 commit/push、新版发行及成功后真实清单同步；未授权 CI/密钥/安装/卸载或 MANUAL-TEST 提交**；密码不进仓库/日志。
- 代理 502 先核对网络/凭据/远端，获授权推送才重试，不绕过检查；整文件差异先查 LF/CRLF；提交格式 feat/fix/chore(scope): 中文描述。
- **本机 GitHub 网络（2026-11-18 起自动化）**：WorkBuddy 默认注入 `https_proxy=http://127.0.0.1:59466`，对 `github.com:443` 曾持续 `CONNECT 502`（`api.github.com` 正常）。现固定走本机 AgentRouter 链路的 Xray 回环代理：
  - `git config --local http.proxy http://127.0.0.1:10809`（仓库级，覆盖环境变量；`--unset` 可回退）。
  - 计划任务 **`Morrow Xray Proxy`**：动作 `%USERPROFILE%\.workbuddy\tools\xray\xray.exe run -c ...\config.json`，登录自启 + 可 `/run` 唤醒，`ExecutionTimeLimit=PT0S`（常驻）、隐藏窗口、`IgnoreNew`。
  - 唤醒脚本 `%USERPROFILE%\.workbuddy\tools\xray\ensure-xray.ps1`：探 127.0.0.1:10809 → 未监听则 `Start-ScheduledTask` 拉起 → 轮询至就绪（默认 15 s），日志写同目录 `ensure-xray.log`；已在监听直接退 0。
  - `.git/hooks/pre-push` 调该脚本（钩子在 `.git` 内、不随仓库分发，重建克隆后需重放）；唤醒失败只告警不阻断推送。
  - 手工排查：`curl -x http://127.0.0.1:10809 --noproxy "" -o /dev/null -w '%{http_code}' https://github.com`；`netstat -ano | grep 127.0.0.1:10809`。端口已被占用时再起实例会失败退出（任务结果 `0xFFFFFFFF`），属预期噪音，不影响已有实例。

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

源码前缀 app/src/main/java/com/morrowviento/morrow/：类型/周期 domain/model、domain/time；迁移 data/db；表单/详情 ui/event；搜索 ui/search/SearchOverlay.kt；年历 ui/calendar；裁剪 data/backdrop/BackdropImage.kt 与 ui/settings/SettingsScreen.kt/SettingsViewModel.kt；提醒 reminder、data/reminder；部件 widget；更新 service/update、ui/update；许可构建逻辑 app/build.gradle.kts 的 generateRuntimeNotices；许可资源 app/src/main/assets/licenses/。
上级 .ref 仅 ying Dart 摘录，不是完整 Flutter 仓库；旧 Glasmio 摘录不是当前 API 基准。

[TODO](TODO.md) 当前待办；[MANUAL-TEST](MANUAL-TEST.md) 用户验收；[CHANGELOG](CHANGELOG.md) 历史；[LICENSE-AUDIT](LICENSE-AUDIT.md) 逐坐标许可与版权归属审阅；[SIGNING](SIGNING.md)/[RELEASE](RELEASE.md) 签名/发布。后续只更新短快照、对应待办与新增历史，区分代码完成、本地验证、真机通过、远端发布。#19 的实时进展/阻塞/续接顺序集中在 HANDOFF 的「#19 运行时许可与 README」章节，TODO 的 #19 子项同步勾选。

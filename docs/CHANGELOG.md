# CHANGELOG

> 倒序排列，最新在上。一行结论优先，细节看提交。

## [2026-11-11] 修复：日历与主界面重叠 + 点 + 号闪退

- 改动：`GlassBackdrop`（新增，渐变底色只保留这一份）——首页放进 `sampledContent`，日历页在根 `Box` 里重铺一层不透明同款渐变；`CalendarScreen` 根节点由裸 `Column` 改为 `Box`，并新增 `onCreate(LocalDate)` 回调与自己的 `CreateFab`（整页不透明后会盖住首页那颗按钮）；`CreateFab` 从 `HomeScreen` 的私有 `CreateButton` 提到 `ui/component/GlassControls.kt` 供两处共用，日历页用它按「选中日 → 今天」的顺序开表单；`WheelColumn` 的 `LazyColumn` 改为 `modifier.height(itemHeight * visibleRows)` 固定高度。
- 涉及文件：`ui/glass/GlassTheme.kt`、`ui/calendar/CalendarScreen.kt`、`ui/component/{WheelPicker,GlassControls}.kt`、`ui/home/HomeScreen.kt`
- 根因 1（重叠）：日历被当作"整页替换"实现，但 `CalendarScreen` 根节点没有背景，只有内部的玻璃面板；它叠在首页同一个 `Box` 里，于是首页的大标题、`排序·创建` 胶囊、卡片副标题全部从透明处穿出来。修复不是加遮罩 alpha，而是让日历页复用**同一份**渐变底色——玻璃采样的仍是 host 的 `LayerBackdrop`，底色与采样源同源才能让交叠处的材质颜色对得上。
- 根因 2（闪退）：`EventEditSheet` 的内容列是 `verticalScroll`，纵向约束为 infinity，而日期滚轮内部的 `LazyColumn` 直接吃这个约束，Compose 在测量阶段抛 `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`，点 + 即崩。修复是让滚轮自己定高（5 行 × 34dp = 170dp，与 `contentPadding` 的半侧内边距配套），而不是给表单换掉 `verticalScroll`。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；113 项单测 0 失败；lint 11 项与改前基线完全一致（全是依赖版本提示类）；debug APK 76 MB
- 状态：代码完成，待真机回归（本次两个问题都是"编译通过、运行崩/糊"，正是缺真机验收的典型）

## [2026-11-11] M4 提醒通知

- 改动：新增 `ReminderPlan`（纯函数排期：锚点 = 全天 09:00 / 否则目标时刻，每条提醒 = 锚点 − 提前分钟；年度重复按当次周年推导，单事件最多两次落点 × 5 条提醒 = 10 个闹钟上限，超过上限由「触发后立即重排」自转）；`ReminderScheduler` 接口 + `AlarmReminderScheduler`（槽位模型：`requestCode` = 槽位下标、`Intent.data` 带事件 id，因此取消只需扫 0..9、事件之间也不会互相覆盖；有精确闹钟权限走 `setExactAndAllowWhileIdle`，没有则退化 `setAndAllowWhileIdle`）；`ReminderReceiver`（触发投递 / 通知「完成」/「稍后 10 分钟」三 action 共用，`goAsync` + IO 读库避免 ANR，触发后立即 `sync` 补下一次落点）；`ReminderBootReceiver`（`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` 全量对账）；`ReminderNotifier`（`reminders` 高优先级渠道、通知日期取**当次落点**而非首次目标、通知里自带 self-check 权限守卫）；`ReminderPermissions`（通知 / 精确闹钟状态探测与设置页跳转，ROM 裁掉设置页时回落应用详情页）；`ui/reminder/`（诊断页：权限两行状态 + 排期列表 + 发测试通知 + 手动重排期，回前台自动重读权限）；`EventViewModel` 在保存/删除/完成/批量清理/撤销后同步闹钟；`YingApp` 冷启动全量对账；通知点击经 `singleTop` + `StateFlow` 直达详情页。
- 涉及文件：`domain/time/ReminderPlan.kt`（新建）、`data/reminder/{ReminderScheduler,AlarmReminderScheduler,ReminderPermissions}.kt`（新建）、`reminder/{ReminderNotifier,ReminderReceiver,ReminderBootReceiver}.kt`（新建）、`ui/reminder/{ReminderUiState,ReminderViewModel,ReminderSheet}.kt`（新建）、`res/drawable/ic_notification.xml`（新建）、`AndroidManifest.xml`、`YingApp.kt`、`MainActivity.kt`、`ui/event/EventViewModel.kt`、`ui/home/HomeScreen.kt`、`res/values/strings.xml`
- 决策：**不引入 `work-runtime-ktx`**。原计划用它兜底重建，但 WorkManager 本身也排不出精确闹钟，且强制停止后同样不跑；实际有效的自愈点是「冷启动 + 开机 + 时间/时区变更」三处对账，全部用 `AlarmManager` 就能覆盖，省掉一个依赖与一套 Worker 生命周期。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；113 项单测 0 失败（新增 12 项，全部是排期纯函数）；lint 与上一版完全一致无新增（过程中 lint 抓出 `LocalDate.ofInstant` 需 API 34 而 minSdk 33 的崩溃隐患，已改为 `Instant.atZone`）；debug APK 76 MB（零新依赖）
- 状态：代码完成，**必须真机验收**——锁屏准时、通知动作、重启重建、跨年重复落点都无法用单测证明

## [2026-11-11] M2d 日历三视图与农历

- 改动：新增 `LunarCalendar`（1900–2100 压缩年表，**自实现零依赖**：农历换算 / 月名日名 / 春节元宵端午七夕中元中秋重阳腊八 / 除夕按腊月实际天数判定 / 闰月不过节）；新增 `ui/calendar/` 三件套——月视图（固定 42 格 6 行、事件圆点、今日描边、选中高亮、节日优先的农历副标题）、年视图（12 个月卡片 + 事件数 + 标题样例，点卡片跳月视图）、按月分组列表；重复事件按当年周年落位（2 月 29 日平年夹到 28 日，且不早于首次目标日期）；`EventViewModel` 增日历状态流（独立的按天刷新 `dayTick`，刻意不复用每秒时间源）与 6 个动作；首页标题栏新增日历入口（Canvas 画图标，不依赖字体的日历字形），日历整页覆盖、与首页共用同一个 backdrop，返回键优先关日历。
- 涉及文件：`domain/time/LunarCalendar.kt`（新建）、`ui/calendar/{CalendarUiState,CalendarActions,CalendarScreen}.kt`（新建）、`ui/event/EventViewModel.kt`、`ui/home/HomeScreen.kt`、`MainActivity.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；101 项单测 0 失败（新增 29 项：农历换算 13 + 日历推导 16）；lint 与上一版完全一致，无新增问题；debug APK 75 MB（未引入任何依赖，包体无变化）
- 状态：代码完成，待真机验收（三视图视觉、格子密度、玻璃叠放）

## [2026-11-11] M2c 详情页与 6 秒撤销

- 改动：撤销模型 `UndoOperation`（5 类操作统一为「事件快照 + 绝对到期时刻」）+ `UndoCodec`（JSON 持久化，支持跨进程恢复）+ `UndoRepository`（操作立即落库、快照回填撤销、到期自动清理、启动按剩余时间恢复）；`EventViewModel` 增删除/完成/置顶/清空已完成/清空全部/撤销/`formFor`；新增 `EventDetailScreen`（同页浮层，全字段 + 完成/置顶/编辑/删除）、`UndoBar`（倒计时复用每秒时间源）、`EventActions` 回调集合；首页卡片可点开详情，标题右侧新增「⋯」菜单（清空已完成 / 清空全部，后者二次确认）；`EventCardModel` 增 `isCompleted`；`EventRepository` 增 `loadAll/deleteMany/clearAll/restore` + DAO `deleteByIds`。
- 涉及文件：`domain/model/{UndoOperation,CountdownEvent}.kt`、`data/undo/**`、`data/{EventRepository,db/EventDao}.kt`、`ui/event/{EventDetailScreen,UndoBar,EventActions,EventViewModel,HomeUiState}.kt`、`ui/home/{HomeScreen,EventCardView}.kt`、`MainActivity.kt`、`YingApp.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；72 项单测 0 失败（新增 10 项：编解码 7 + 时间窗口 3）；lint 无新增问题；debug APK 75 MB。撤销条的浮层叠放与跨进程恢复待真机验证
- 状态：代码完成，待真机验收

## [2026-11-11] M2b2 分类管理

- 改动：分类改为「名字即身份」——事件表继续存 `category` 字符串，重命名/删除迁移通过 SQL 级联更新事件行，避免为展示性字段升 Room schema；新增 `EventCategory`（预置 7 类 + 12 字上限）、`CategoryRules`（纯函数：归一化/合并孤儿/增删改校验/至少保留一项）、`CategoryCodec`（DataStore 存 JSON 数组）、`CategoryRepository`（合并「已保存 + 旧事件孤儿」，删除被占用分类强制选迁移目标）；Room 增 `observeCategoryUsage` / `countByCategory` / `renameCategory`；新增分类管理浮层 `CategorySheet`（列表、行内重命名、删除迁移确认）；`EventEditSheet` 分类 chips 改读仓库并加「管理分类…」入口；抽出 `ui/component/GlassControls.kt` 供两个浮层复用；删除 `EventFormState.DEFAULT_CATEGORIES`。
- 涉及文件：`domain/model/EventCategory.kt`、`data/category/**`、`data/db/{CategoryUsage,EventDao}.kt`、`data/EventRepository.kt`、`ui/category/**`、`ui/component/GlassControls.kt`、`ui/home/HomeScreen.kt`、`ui/event/EventEditSheet.kt`、`MainActivity.kt`、`YingApp.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；62 项单测 0 失败（新增 21 项：分类规则 15 + 编解码 6）；lint 无新增问题；debug APK 75 MB。分类浮层视觉与交互待真机验收
- 状态：代码完成，待真机验收

## [2026-11-11] T0 远程对齐与 M2a/M2b 入库

- 改动：新增 `.gitattributes`（`* text=auto eol=lf` + 二进制类型显式标注），消除 `core.autocrlf=true` 导致的 README 整文件换行符假差异；`git reset --mixed origin/main` 丢弃本地重复提交对象 `3e4333e`（内容已在远程，工作区零损失）；拆两次提交 `c02adc9`（chore 行尾统一）与 `548edad`（feat M2a+M2b+docs），`push -u origin main` 建立 upstream。
- 涉及文件：`.gitattributes`（新建）、`docs/TODO.md`、`docs/HANDOFF.md`
- 验证：本地 `assembleDebug + testDebugUnitTest + lintDebug` 通过，41 项单测 0 失败，debug APK 75 MB；CI run `42983525295`（commit `548edad`）全绿；`git status -sb` 为 `## main...origin/main`
- 状态：完成

## [2026-11-11] M2b 事件新建表单

- 改动：`EventFormState` 纯函数层（标题校验、落库字段映射、`safeDate` 日夹取、提醒增删/去重/上限 5、快捷日期 今天/明天/一周后/月底/年底、`fromEvent` 回填）；`WheelDateTimePicker`（年/月/日/时/分五列滚轮，`rememberSnapFlingBehavior` 吸附，全天隐藏时间列，换月自动夹取日期）；`EventEditSheet`（同页面浮层，不用 ModalBottomSheet 以保证 backdrop 采样不断；标题/滚轮/全天/方向/每年重复/快捷日期/分类 chips/提醒 chips/备注）；`EventViewModel.save`；首页 FAB 打开表单；`CalendarMath.dateClamped`。
- 涉及文件：`ui/event/{EventFormState,EventEditSheet,EventViewModel}.kt`、`ui/component/WheelPicker.kt`（新建）、`ui/home/HomeScreen.kt`、`domain/time/CalendarMath.kt`、`MainActivity.kt`、`app/src/test/java/com/jiuxina/ying/ui/event/EventFormStateTest.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；41 项单测 0 失败（新增 9 项）。滚轮吸附与视觉未真机校准
- 状态：代码完成，待真机验收；未提交（分类 CRUD 仍待 M2b2）

## [2026-11-11] M2a 首页事件列表

- 改动：新增 `SortMode`（映射旧 `distance/targetDate/createdAt`）；`HomeUiState` + `EventPresentation` 纯函数层（置顶优先、已过事件沉底且「刚过去」优先、每年重复按周年换算、进度环 0..1 夹取、今日剩余时分秒）；`EventViewModel`（Room Flow + 排序偏好 + 每秒时间源 combine，`WhileSubscribed(5s)` 停表）；`HomeScreen`（折叠大标题、排序 chip、进行中/已完成分区、空态/加载/错误态、玻璃悬浮新建）；`EventCardView`（天数徽章 + Canvas 进度环）；`ui/glass/` 增 `glassTextPrimary/glassTextSecondary` 与 `GlassCirclePanel`；`MainActivity` 接入 ViewModel。
- 涉及文件：`ui/event/{HomeUiState,EventViewModel}.kt`、`ui/home/{HomeScreen,EventCardView}.kt`、`domain/model/SortMode.kt`、`ui/glass/GlassTheme.kt`、`MainActivity.kt`、`app/src/test/java/com/jiuxina/ying/ui/event/EventPresentationTest.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；32 项单测 0 失败（新增 10 项）；debug APK 75 MB。**未做真机/模拟器视觉验证（本机无设备）**
- 状态：代码完成，待真机验收；未提交

## [2026-11-11] 范围二次调整（恢复缺口功能）

- 改动：单事件小部件、年视图、农历、提醒诊断页恢复保留；应用内更新恢复全量（测速选路 + 断点续传 + 静默安装）；分类体系恢复全量（预置 + 自定义增删改），并拆出独立任务 `M2b2`；仅保留原有的 6 项裁剪（配置台适度精简、壁纸/节日皮肤与字体库/相册背景延后、头像删除、赞助页取消）。估算回到 62–72 人日。
- 涉及文件：`docs/TODO.md`（范围表 + 任务 + 依赖图）、`docs/HANDOFF.md`
- 验证：无（决策记录）
- 状态：完成

## [2026-11-11] 移植范围裁剪

- 改动：逐模块过一遍旧功能并定稿裁剪——小部件配置台适度精简（保留预设/字号/配色/显隐）、壁纸取色与节日皮肤延后 v4.1、字体库与相册背景延后 v4.1、单事件小部件/年视图/农历/提醒诊断页砍掉、头像 Emoji 与单位文案删除、赞助页取消、应用内更新简化为「检查 + 跳浏览器」。预计省约 25 人日。
- 涉及文件：`docs/TODO.md`（新增「移植范围」表）、`docs/HANDOFF.md`
- 验证：无（决策记录）
- 状态：完成

## [2026-11-11] 协作文档体系（TODO / CHANGELOG / HANDOFF）

- 改动：建立跨会话交接文档，替代口头记忆；合并旧草稿 `TODO-Morrow.md` 的任务与依赖信息；补齐远程/本地 SHA 不一致、缺失依赖等风险项。
- 涉及文件：`docs/TODO.md`、`docs/CHANGELOG.md`、`docs/HANDOFF.md`
- 验证：仅文档，无代码改动，不触发重新构建
- 状态：完成

## [2026-11-11] M1 数据层（Room + DataStore + 旧数据迁移）

- 改动：领域模型落地（`CountdownEvent` / `EventReminder` / `CountDirection` / `RepeatType`，字段与 Dart 版一一对应）；`CalendarMath`（LocalDate 差值，规避夏令时）与 `RepeatRule`（每年重复，闰日回落 2/28）；Room `events` 表 + `EventDao` + `EventMapper`；DataStore `ying_settings` 沿用 Flutter 原始键名；`FlutterSharedPreferences` → Room/DataStore 幂等迁移器（`migration.legacy_v1_done`，单条损坏不阻断）；接入 KSP/Room 与 schema 导出。
- 涉及文件：`app/src/main/java/com/jiuxina/ying/domain/**`、`data/**`、`app/src/test/java/com/jiuxina/ying/**`、`app/schemas/**`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`
- 验证：本地 `assembleDebug + testDebugUnitTest` 通过（22 项 0 失败：日历 12 / 迁移 6 / 映射 4）
- 状态：完成（远程 CI 已跑通，但本地与远程 SHA 不同，见 HANDOFF 待确认项 1）

## [2026-11-11] M0 工程基建与 Glasmio 渲染验证

- 改动：Gradle 工程骨架（AGP 9.4.1 / Kotlin 2.4.21 内置 / Gradle 9.6.0 / compileSdk 37 / minSdk 33 / JDK 21）；`ui/glass/` 封装层（`GlassTheme` / `GlassBackdropHost` / `GlassPanel`，Glasmio 唯一入口）；M0 玻璃材质验证页；GitHub Actions 出 debug APK；移除 NDK/CMake（AAR 已内置四 ABI 预编译库）。
- 涉及文件：`settings.gradle.kts`、`build.gradle.kts`、`app/build.gradle.kts`、`gradle/**`、`app/src/main/java/com/jiuxina/ying/{MainActivity.kt,ui/glass/GlassTheme.kt}`、`.github/workflows/android-ci.yml`、`README.md`
- 验证：本地 `assembleDebug + testDebugUnitTest + lintDebug` 通过，产出约 74 MB debug APK；CI 全绿
- 状态：完成

## [2026-11-11] 仓库与 CI 通道打通

- 改动：创建 `MorrowViento/ying-native`；补齐 Token 的 Contents / Workflows / Actions 权限；CI 平台包名修正为 `platforms;android-37.0` 并固定 cmdline-tools；移除 AGP 9 已不允许的 `kotlin-android` 插件；按 Glasmio `v0.1.2` 标签源码对齐封装层签名。
- 涉及文件：`.github/workflows/android-ci.yml`、`build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、`app/src/main/java/com/jiuxina/ying/ui/glass/GlassTheme.kt`
- 验证：CI 五轮迭代后编译通过并产出 APK
- 状态：完成

## [2026-11-09] 重构方案与决策确认

- 改动：量化 Flutter 版现状（Dart 约 2.2–2.4 万行 / 102 文件，Android 原生小部件已是 Kotlin）；确认 Glasmio 与 Flutter 渲染层不兼容，定为 Kotlin 重写；拆出 M0–M7 八个里程碑与 10–14 周估算；确认决策 D1–D6。
- 涉及文件：上级目录 `ying-kotlin-refactor-plan.md`（方案文档，不在本仓库）
- 验证：无（方案阶段）
- 状态：完成

# CHANGELOG

> 倒序排列，最新在上。一行结论优先，细节看提交。

## [2026-11-11] 修提醒晚 4 分钟：补 USE_EXACT_ALARM，并让延迟可观测

- 现象（真机反馈，OnePlus PJZ110 / Android 17）：事件设「提前 10 分钟」，实际提前 6 分钟才响，晚 4 分钟；同一时刻应用在前台；设置页「发送测试通知」正常。
- 判断依据：测试通知有效 → 通知渠道与 `ReminderNotifier` 无问题，问题在「排期 → 闹钟 → 接收器」这条链。晚到而非不响 → 闹钟排上了但被系统延迟投递，即走了不精确分支。
- 根因：只声明了 `SCHEDULE_EXACT_ALARM`。该权限 Android 14 起对新装应用**默认拒绝**且需用户手动开开关，`canScheduleExactAlarms()` 返回 false，调度器退化为 `setAndAllowWhileIdle`——它本身就是不精确闹钟，系统允许推迟数分钟（前台也不例外，Doze/待机窗口同样适用）。诊断页当时已显示「未允许，系统可能把提醒推迟几分钟」，与实测完全吻合。
- 改动：
  - `AndroidManifest.xml` 增加 `USE_EXACT_ALARM`（API 33 引入的普通权限，安装即授予、不受用户开关影响，专供闹钟/日历类「必须准点」的应用）。两条同时声明时系统以 `USE_EXACT_ALARM` 为准。
  - `AlarmReminderScheduler`：降级分支补 `Log.w`（含事件、槽位、提前分钟），此前静默降级导致只能靠猜。
  - 延迟可观测：`fireIntent` 带上计划触发时刻 `EXTRA_TRIGGER_AT`，`ReminderReceiver` 投递后把「计划/实际」两个时刻写入设置，诊断页显示「上次提醒晚了 X 分 Y 秒，系统在推迟闹钟」或「上次提醒准时」。阈值 5 秒——几秒偏差用户无感，到了分钟级才是真问题。
- 涉及文件：`AndroidManifest.xml`、`data/reminder/AlarmReminderScheduler.kt`、`reminder/ReminderReceiver.kt`、`data/settings/LegacySettingsKeys.kt`、`ui/reminder/{ReminderViewModel,ReminderUiState,ReminderSheet}.kt`、`MainActivity.kt`
- 验证：175 项单测 0 失败、lint 11 项基线、`aapt2 dump permissions` 确认 `USE_EXACT_ALARM` 已入包。
- 注意（M7 发版前）：Google Play 对 `USE_EXACT_ALARM` 有用途审查（仅限闹钟/日历类核心功能），上架需在商店后台声明；若审核不通过，回退为仅 `SCHEDULE_EXACT_ALARM` + 保留诊断页引导开关。

## [2026-11-11] 修日期时间滚轮「滚哪里都弹回今天 09:00」

- 现象（真机反馈）：滚年月日或时分，选完立刻弹回 `2026.11.11 09:00`——正好是新建表单的初始值。
- 根因：`WheelColumn` 里 `LaunchedEffect(state) { snapshotFlow { … }.collect { … onSelect(index) } }` 的 `state` 永不变化，**effect 只在首次组合启动一次**，于是 `collect` 闭包捕获的是**第一帧**的 `onSelect`——而那一份回调闭包了第一帧的 `date` / `time`（今天 09:00）。后续每次滚动都在「用第一帧的日期时间去拼新值」：滚「时」→ 时变了、分取第一帧；再滚「分」→ 回调又把时写回第一帧的 09。日期同理，所以年月日也跟着回弹。`values` 也一并陈旧（换月后日列表长度变了仍用旧列表）。
- 改动：`values` 与 `onSelect` 都改经 `rememberUpdatedState`，collector 每次读最新一份。
- 涉及文件：`ui/component/WheelPicker.kt`
- 验证：全量单测 0 失败、lint 基线不变；滚轮本身是手势交互，JVM 无真实手势，需真机复验（清单 B6）。

## [2026-11-11] 修「保存事件后退回桌面」——DataStore 旧键类型错配

- 现象（真机 OnePlus PJZ110 / Android 17，debug 包）：填完表单点保存，应用**退回桌面**；重开记录还在（说明数据已落库，崩在保存之后）。
- 根因（取自 `crash-1794397428221.txt`）：`ClassCastException: java.lang.Boolean cannot be cast to java.lang.String`，栈为 `WidgetConfigStore.legacyDefaults(…:66)` ← `defaultStyle(…:37)` ← `WidgetRefresher.refresh(…:79)`。
  - DataStore 的 `Preferences.Key` 是 `data class Key<T>(name)`，**相等性只看 name、不校验类型**，内部实现 `preferencesMap[key] as T?`；于是「用 String 键读 Boolean 值」不会在读取时被挡住，而是在调用方做 `String` 赋值时抛 CCE。
  - 错配来源：M1 迁移用 `putRaw` **原样保留类型**搬运 Flutter 版偏好，而 Flutter 版的 `widget_show_progress` / `widget_show_category` / `widget_show_date` 是 **Boolean**；M5 的 `WidgetConfigStore` 按 String 读。
  - 为何是「退回桌面」而不是界面内报错：该刷新挂在 `EventViewModel` 的 `observeAll()` 上（`drop(1)+debounce(300)`），运行在 `viewModelScope`——**没有异常处理器**，未捕获异常直达默认 `UncaughtExceptionHandler` 直接杀进程。保存本身早已完成，所以重开记录还在。
- 改动：把「按名字 + 类型宽容」的读取收敛进 `SettingsRepository`——新增 `Preferences.stringOrNull` / `booleanOrNull`（`Any?` → 文本/布尔，认 `"true"/"1"/"yes"` 与数字），`themeMode` / `sortMode` / `reduceTransparency` / `reduceMotion` / `onboardingCompleted` / `snapshotSettings` / `rawString` / `stringOnce` 全部改走它们。全项目 DataStore 读取都经 `SettingsRepository`，一处收口即全覆盖。顺带修掉一个潜伏问题：迁移完成标记 `migration.legacy_v1_done` 也是 Boolean，此前 `rawString` 读它会抛异常（被 `warmStart` 的 `runCatching` 吞掉），导致**每次冷启动都重跑一遍旧数据迁移**。
- 另加固：`WidgetRefresher.refreshAll` / `refreshIds` 与 `EventViewModel` 的刷新 collector 各包一层 `runCatching`——小部件是附带能力，任何异常只留日志，绝不波及保存与启动。
- 另修复：`WidgetStyleCodec.fromLegacy` 此前读了 `widget_show_*` 三个键却**从不使用**（死读，且正是崩点）；现在按旧版显式开关覆盖预设默认，旧用户的小部件显隐能真正带过来。
- 涉及文件：`data/settings/SettingsRepository.kt`、`widget/WidgetStyle.kt`、`widget/WidgetRefresher.kt`、`ui/event/EventViewModel.kt`、`app/src/test/java/com/jiuxina/ying/data/settings/SettingsTypeSafetyTest.kt`（新建 5 例）
- 验证：全量单测 0 失败（新增 5 例：布尔/数字旧值读取不抛、缺键保持默认、`legacyDefaults()` 完整复现路径、无旧键时不崩）；lint 仍为基线

## [2026-11-11] 启动闪退结案：冷启动自愈链缺兜底

- 结论：m6 的「打开即闪退」根因是 `YingApp` 冷启动自愈链**没有任何异常兜底**。迁移 / 撤销恢复 / 提醒对账 / 小部件刷新整段塞在一个裸 `appScope.launch {}` 里，协程内未捕获异常直达系统默认 `UncaughtExceptionHandler` 直接杀进程——所以是「一闪即退、看不到任何画面」，而不是界面画到一半才挂。诊断包（把四步逐步 `runCatching` 隔离）装上后**用户反馈不再闪退**，与推断一致。这也解释了为何 JVM 侧测不出来：Robolectric 下 `Dispatchers.IO` 的调度时序与真机不同，异常没在同样的时机被抛出。
- 改动：`util/StartupTrace` 收敛为**仅 debug 包生效**——落盘与崩溃拦截都走 `ApplicationInfo.FLAG_DEBUGGABLE` 判定（不依赖 buildConfig 开关）；正式包只写 logcat、不接管 `UncaughtExceptionHandler`（接管会挡住系统与商店的崩溃上报）。启动轨迹与 `warmStart`/`step` 的逐步隔离保留，这是真修复。
- 涉及文件：`util/StartupTrace.kt`、`YingApp.kt`
- 验证：全量单测 0 失败；`assembleDebug` 通过
- 状态：闪退已解除。**被吞掉的异常本身尚未归因**——若能在装过诊断包的那台机器上取到 `Android/media/com.jiuxina.ying/startup.log`，看哪一步标了 `: XXXException`，即可确认是哪个自愈步骤在这台机器上失败（大概率是旧数据迁移或提醒对账），再决定要不要单独修。

## [2026-11-11] 诊断：m6 安装后启动即退（真机 Android 16）

- 现象：`ying-4.0.0-alpha1-debug-m6.apk` 在同一台 Android 16 机器上点开**一闪即退、看不到任何画面**；同机 `debug-m5.apk` 正常。故问题由 M6 引入，且落在启动早期。
- 本地复现结论：**代码逻辑层无问题，无法在 JVM 侧复现**。新增 `StartupSmokeTest`（Robolectric 4.16）把 `YingApp.onCreate` 与 `MainActivity` 拉到 `onResume`，并强制 `decorView.measure/layout` 走完首帧测量（上一轮「点 + 号闪退」正是测量期异常，只到 onResume 是测不到的），三例在 **API 33 与 34** 下全通过。另用 `javap` 核实两处外部假设：①`GlasmioTheme` 默认 `appearance` 即 `GlassAppearance()`（构造 mask=7 全默认），M6 显式传入的 `GlassAppearance(reduceTransparency = false)` 与之逐字段相等，**GlassTheme 改造可排除**；②`GlassAppearance` 默认值为 `liquidGlassEnabled=true / reduceTransparency=false / adaptiveColorScheme=false`。`preferencesDataStore` 全项目仅一处声明，无「同一文件多实例」陷阱；`Glasmio` 的 `libglasmio_liquid_menu_sdf.so` 只被 `LiquidMenuNativeField` 加载，不在启动路径上。
- 改动：新增 `util/StartupTrace`（**临时诊断设施，M7 发布前移除**）——`mark()` 把启动每一步追加进 `startup.log` 并立即落盘，即使崩溃是 native 层（SIGSEGV 不被 Java handler 捕获）也能看出卡在哪一步；`install()` 挂 `UncaughtExceptionHandler`，把 Java 堆栈连同末 40 行启动轨迹、设备与版本信息写成 `crash-*.txt`。落盘在 `Android/media/<包名>/`，Android 11+ 对该目录免权限且任何文件管理器可见，用户取日志不需要电脑或 root。`YingApp` 的启动自愈链拆成 `warmStart()` + `step()`：迁移 / 撤销恢复 / 提醒对账 / 小部件刷新逐步 `runCatching` 隔离——此前整段 `appScope.launch` 无任何兜底，其中任一步抛异常都会直达默认处理器**杀掉整个进程**，与「打开即闪退」的现象完全吻合。
- 涉及文件：`util/StartupTrace.kt`（新建）、`YingApp.kt`、`MainActivity.kt`、`app/src/test/java/com/jiuxina/ying/StartupSmokeTest.kt`（新建）、`app/build.gradle.kts`（`testImplementation(libs.robolectric)`，版本目录里本就预留 4.16）
- 验证：全量单测 0 失败（新增 3 例启动冒烟）；编译仅 1 条 `getExternalMediaDirs` 废弃警告（API 37 标注，行为不受影响）
- 状态：**等用户取回 `startup.log` / `crash-*.txt` 再定根因**。若日志停在 `main setContent returned` 之后，则锁定首帧绘制（渲染层）；若停在更早的位置，按最后一条 mark 逐步收窄。

## [2026-11-11] M6 设置中心与备份

- 改动：新增 `ui/settings/`——`SettingsScreen`（整页覆盖，自带 `GlassBackdrop` 与 `BackHandler`；外观 / 数据 / 提醒 / 关于四组玻璃卡片，全部由一个 LazyColumn 承载）+ `SettingsViewModel`（持久设置走 DataStore Flow、瞬时状态走本地 StateFlow，两者 combine 成一个 UI 状态）+ `SettingsUiState`/`SettingsActions`（含 `ImportReport.summaryText` 文案）+ 自绘 `GlassSwitch`（跟随 `reduceMotion` 用 `snap()` 关动画）；`GlassTheme` 新增 `darkTheme`/`reduceTransparency`/`reduceMotion` 三参数并暴露 `LocalGlassDark`/`LocalReduceMotion`，`GlassBackdrop` 与 `glassTextPrimary/Secondary` 改为读 `LocalGlassDark`（此前只认系统深浅，用户强制浅色会底色与文字分裂）；新增 `data/settings/AppSettings`（`ThemeMode` + `isDark(systemDark)` 纯函数）；新增 `data/backup/`——`BackupDocument`/`BackupCodec`（逐字段容错、时间双格式、坏条目跳过计数、版本过高拒绝）+ `BackupMapper`（领域 ↔ 可读文本）+ `BackupMerge`（按 id 合并）+ `BackupRepository`（导出全量 / 导入 REPLACE|MERGE / 清空全部）+ `BackupFiles`（SAF 读写 + 建议文件名）；`WidgetConfigStore` 增 `widget.default_style` 作为「widgetId 查不到」时的兜底。
- 涉及文件：`data/settings/{AppSettings.kt（新建）,SettingsRepository.kt}`、`data/backup/{BackupDocument,BackupRepository,BackupFiles}.kt`（新建）、`ui/settings/{SettingsScreen,SettingsViewModel,SettingsUiState}.kt`（新建）、`ui/glass/GlassTheme.kt`、`ui/home/HomeScreen.kt`（更多菜单加「设置」+ 整页覆盖 + 主题下传）、`MainActivity.kt`、`YingApp.kt`、`widget/WidgetConfigStore.kt`、`app/src/test/java/com/jiuxina/ying/data/backup/{BackupCodecTest,BackupMergeTest}.kt`（新建）
- 决策：①**主题与减透明走 Glasmio 原生参数**（`GlasmioTheme(isDark = …)` + `GlassAppearance(reduceTransparency = …)`），不在外面叠遮罩——遮罩挡得住背景挡不住折射与高光，等于假开关；②**设置行自绘，不用 `glasmio-preference`**：其 `GlasmioListPreference` 选中要弹二级对话框，而主题只有三个选项，横排 chips 少一次点击；且它的行组件与现有 `SectionTitle`/`ChoiceChip` 是两套间距与字号，混用会看出接缝；③**备份存 ISO-8601 本地时刻字符串**而不是毫秒：与 Flutter 版 `toIso8601String()` 同语义、用户能直接打开看，解码时兼容毫秒（`parseTime` 两种都收）；④**导入分「合并 / 覆盖」两档**：合并只碰事件与分类（外观与本机小部件样式不动，因为用户通常只想补另一台设备的事件），覆盖才整套替换；⑤**小部件样式按 widgetId 存，靠 `widget.default_style` 兜底换机场景**——widgetId 由桌面宿主分配，换机必变，只存按 id 的配置等于换机后每加一个都要重设。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；**167 项单测 0 失败**（新增 25：编解码往返/容错/时间双格式 17 + 合并与设置模型 8）；lint 11 项与基线完全一致（过程中新引入的 `UseOfNonLambdaOffsetOverload` 已改掉）；debug APK 76 MB（零新依赖）
- 状态：代码完成，**待真机验收**——导出到云盘/本地、导入两种模式的数据落点、清空后提醒与小部件是否同步清干净、浅色/深色/跟随系统三态切换是否连底色与文字一起变、减弱透明在真实玻璃上的观感

## [2026-11-11] M5 桌面小部件

- 改动：新增 `data/widget/WidgetSnapshot`（纯函数：未完成事件 → 小部件行，置顶优先 → 未来按临近升序 → 已过排队尾，徽章「今天 / N 天 / 已过 N」与首页同规则，进度百分比只在非重复事件上算）+ `WidgetRefreshTrigger`（接口，给 ViewModel 用，避免 ui 层依赖 widget 包）；新增 `widget/` 包——`WidgetStyle`（预设 1/3/5 行 × 字号三档 × 配色四套（跟随系统/深色/浅色/品牌色）× 分类/日期/进度三开关，`colors()` 按 `uiMode` 在 ARGB 层判明暗）+ `WidgetStyleCodec`（逐字段容错解码 + 旧键尽力映射）+ `WidgetConfigStore`（`widget.<id>.style` 单键 JSON，复用 `ying_settings` 同一个 DataStore）+ `WidgetRefresher`（**唯一刷新入口**：一次性查 Room → 拼 RemoteViews → 统一套背景/文字色/字号/显隐/进度 tint，两个小部件共用）+ `YingWidgetProvider` / `YingDetailWidgetProvider`（onUpdate / onAppWidgetOptionsChanged / onDeleted 清配置 / onEnabled 排闹钟）+ `MidnightRefreshScheduler` + `WidgetMidnightReceiver`（本地零点刷一次并排下一次；有精确权限走 `setExactAndAllowWhileIdle`，否则 `setAndAllowWhileIdle`）+ `WidgetRefreshRunner`（`goAsync` + IO 协程，超时必 `finish`）+ `WidgetConfigActivity` / `DetailWidgetConfigActivity`（玻璃风配置台：实时预览 + 预设/字号/配色/显隐 + 单事件绑定，整页 LazyColumn 承载）；资源新增 `widget_overview.xml`（5 组写死行）、`widget_detail.xml`、两个 appwidget-provider、三套圆角渐变底、`WidgetText` 文字样式。
- 涉及文件：`data/widget/{WidgetSnapshot,WidgetRefreshTrigger}.kt`（新建）、`widget/{WidgetStyle,WidgetConfigStore,WidgetRefresher,WidgetRefreshRunner,YingWidgetProvider,MidnightRefreshScheduler,WidgetMidnightReceiver,WidgetConfigActivity}.kt`（新建）、`res/layout/widget_{overview,detail}.xml`（新建）、`res/xml/widget_{overview,detail}_info.xml`（新建）、`res/drawable/widget_bg_{dark,light,accent}.xml`（新建）、`res/values/{colors,widget_styles}.xml`（新建）、`res/values/strings.xml`、`AndroidManifest.xml`、`YingApp.kt`、`MainActivity.kt`、`ui/event/EventViewModel.kt`、`reminder/ReminderBootReceiver.kt`、`data/settings/SettingsRepository.kt`
- 决策：按用户确认走**只读快照 + 复用 M4 对账链路**。① 数据源直读 Room 一次性查询，不订阅 `observeAll()`——RemoteViews 刷完即弃，挂 Flow 只会在进程回收前白占协程与观察者；② 刷新由「系统 onUpdate + 数据变更（ViewModel 在 `observeAll` 上 `drop(1)+debounce(300)` 统一触发，比逐个写路径加更不易漏）+ 午夜闹钟 + 开机/改时间对账」四路驱动，`updatePeriodMillis` 置 0（系统周期最短 30 分钟且对齐上次刷新，跨天必然错）；③ 布局用 5 组写死的行，不为 1~5 行引入 `RemoteViewsService` 集合；④ 配色在代码里按 `uiMode` 判明暗，`values-night` 对 `setBackgroundResource` 不生效。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；**142 项单测 0 失败**（新增 29：快照排序/徽章/元信息/进度 17 + 样式预设/配色/编解码/旧键映射 12）；lint 11 项与基线完全一致（过程中新引入的 `DisableBaselineAlignment`/`SmallSp`/`UseKtx`/`UnusedResources` 共 21 项已全部消除）；debug APK 76 MB（零新依赖）
- 状态：代码完成，**待真机验收**——小部件添加/尺寸裁切、跨零点天数翻转、点行直达详情、长按重配置、四种配色在深浅桌面上的观感都必须上机看

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

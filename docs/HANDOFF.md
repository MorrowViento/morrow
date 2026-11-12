# HANDOFF — 朝夕 · Kotlin 重构

## 任务背景

把「朝夕」（Morrow）倒数日应用从 Flutter 重写为 Kotlin/Compose + Glasmio Liquid Glass（Android 13+）。2026-11-12 品牌重塑后 `applicationId` 为 `com.morrowviento.morrow`，**不再**沿用 Flutter 版的 `com.jiuxina.ying`，升级方式见 `docs/SIGNING.md`。

## 当前进度快照（2026-11-12 20:40，交接以此为准）

| 项 | 状态 |
|---|---|
| 阶段 | **M7 应用内更新与发布**；M0–M6 代码完成，**M2a–M6 真机验收基本通过**（用户 2026-11-12 确认） |
| 品牌 | 应用名「**朝夕**」；项目 / Gradle 根名 / 目录 / 包名后缀均为 `morrow`，`applicationId` = `com.morrowviento.morrow`；图标为自绘「晨昏日轮」自适应图标 |
| 版本 / 分支 | `4.0.0-alpha5 (44)` / `main`（按压交回 Glasmio + 底栏跨页面常驻，本地已构建）；线上最新 Release 仍是 `v4.0.0-alpha2-41` |
| Release | GitHub `v4.0.0-alpha2-41`（**正式** Release：`/releases/latest` 不返回预发布，标成 prerelease 应用就检测不到）；资产 `morrow-41-4.0.0-alpha2-debug.apk`，79,389,680 B，SHA-256 `04d81bdc…1fd83ed6` |
| 验证 | 228 项单测全绿；lint **0 errors / 10 warnings**（`abortOnError = true`，7 条为依赖有新版本的提示类）；`aapt2` 确认 `versionCode=44`、`versionName=4.0.0-alpha5`、`label=朝夕` |
| 交付 | 上级目录 `morrow-43-4.0.0-alpha4-debug-press-halo-calendar-bar.apk`（79,638,704 B，`sha256:5209ae96…2218a2`）为最新待验包；`morrow-41-4.0.0-alpha2-debug.apk` 仍是线上 Release 那一份。本机无 adb/模拟器，真机由用户在 OnePlus PJZ110 / Android 17 验收 |
| 工作区 | 仅 `docs/MANUAL-TEST.md` 有用户本地勾选的 40 项验收记录，**按用户要求暂不提交、不推送** |
| 下一步 | 真机验底栏（P 组）/ 按压光效 → N1–N9（检查→下载→安装→升级）→ 自有密钥出正式包 → Android 13/14/15/16 全量回归 |

> `a525f2a` 的推送在代理 502 下后台重试第 24 次才成功；推送不稳定是环境常态，失败时挂后台重试即可，代码本身无需改动。

## 已完成清单

> 第 1–14 条是各里程碑完成时的原始记录，其中的「未真机验收」指**当时**状态；M2a–M6 已于 2026-11-12 真机验收基本通过，后续修复与当前状态见第 15–18 条及开头快照。

1. **M0 工程基建**：AGP 9.4.1 / Kotlin 2.4.21 / Gradle 9.6.0 / compileSdk 37 / minSdk 33 / JDK 21；Glasmio 0.1.2 双模块依赖；`ui/glass/` 封装层；无 NDK/CMake。
2. **CI**：`.github/workflows/android-ci.yml`，`assembleDebug + testDebugUnitTest + lintDebug` 出 debug APK 工件。
3. **M1 数据层**：Room `events`（schema 已导出）+ DataStore `morrow_settings` + 幂等旧数据迁移 + 22 项单测全绿。
4. **应用容器**：`MorrowApp` 手写依赖装配（DB / EventRepository / SettingsRepository），启动即跑迁移（失败不阻断）。
5. **M2a 首页列表**：`EventViewModel` + `EventPresentation`（纯函数排序/进度）+ `HomeScreen` + `EventCardView`；玻璃封装层增文字色与圆形面板。
6. **M2b 新建表单**：`EventFormState` + `WheelDateTimePicker`（五列滚轮）+ `EventEditSheet`（同页浮层）+ FAB 落库；41 项单测全绿。**未真机校准。**
7. **T0 远程对齐**：根因是 `core.autocrlf=true` 导致 README 整文件换行符差异；加 `.gitattributes`（`eol=lf`）→ `reset --mixed origin/main` → 两次提交（`c02adc9` 行尾 / `548edad` M2a+M2b+docs）→ `push -u origin main`。本地与远程齐平于 `548edad`，CI 全绿。
8. **M2b2 分类管理**：`EventCategory`（预置 7 类、12 字上限）+ `CategoryRules`（纯函数规则）+ `CategoryCodec`（DataStore `category_list_v1` JSON）+ `CategoryRepository`（存储列表 ∪ 旧事件孤儿分类，删除需迁移目标）+ `CategoryViewModel` + `CategorySheet` 浮层；Room 增分类统计/级联更新三条查询；`EventEditSheet` 改读仓库并加「管理分类…」；抽出 `GlassControls`。62 项单测全绿。**未真机验收。**
9. **文档**：`README.md`（决策表 + 包结构 + Glasmio 版本差异）、`docs/TODO.md`、`docs/CHANGELOG.md`、本文件。
10. **M2c 详情页 + 6 秒撤销**：`UndoOperation`/`UndoCodec`/`UndoRepository`（立即落库 + 快照回填 + `expiresAt` 持久化 + 启动按剩余时间恢复）+ `EventDetailScreen` + `UndoBar` + `EventActions`；首页卡片可点开详情，标题右侧「⋯」菜单含清空已完成/清空全部（后者二次确认）。72 项单测全绿。**未真机验收。**
11. **M2d 日历三视图**：`LunarCalendar`（自实现 1900–2100 压缩年表，零依赖：换算 / 月名日名 / 农历节日 / 除夕）+ `CalendarPresentation`（纯函数：42 格月网格、重复事件按当年周年落位、年视图 12 月汇总、按月分组）+ `CalendarScreen`（月/年/列表三视图、分段切换、`‹ ›` 翻页、回到今天）+ `CalendarNav`/`CalendarActions`；`EventViewModel` 增按天刷新的 `calendar` 状态流；首页标题栏新增日历入口（Canvas 图标），日历整页覆盖并与首页共用 backdrop，`BackHandler` 优先关日历。101 项单测全绿。**未真机验收。**
12. **M4 提醒通知**：`ReminderPlan`（纯函数：锚点 = 全天 09:00 / 否则目标时刻；每条提醒 = 锚点 − 提前分钟；年度重复按当次周年；单事件最多 2 次落点 × 5 条 = 10 个闹钟上限，靠触发后立即重排自转）+ `AlarmReminderScheduler`（槽位模型：`requestCode` = 槽位下标、`Intent.data` 带事件 id；有精确权限走 `setExactAndAllowWhileIdle`，无则退化 `setAndAllowWhileIdle`）+ `ReminderReceiver`（触发 / 完成 / 稍后 10 分钟三 action 共用，`goAsync` + IO 读库）+ `ReminderBootReceiver`（开机、覆盖安装、时间/时区变更全量对账）+ `ReminderNotifier`（高优先级渠道，日期取当次落点）+ `ReminderPermissions` + `ui/reminder/` 诊断页（权限状态 / 排期列表 / 测试通知 / 手动重排期）；`EventViewModel` 在保存、删除、完成、批量清理、撤销后同步闹钟；冷启动对账；通知点击经 `singleTop` + `StateFlow` 直达详情页。113 项单测全绿。**未真机验收——这是唯一必须真机才能验证的里程碑。**
13. **M5 桌面小部件**：`WidgetSnapshotBuilder`（纯函数：未完成事件 → 小部件行，置顶优先 / 未来按临近升序 / 已过排队尾，徽章文案「今天 / N 天 / 已过 N」与首页一致）+ `WidgetStyle`/`WidgetStyleCodec`（预设 1/3/5 行 × 字号三档 × 配色四套 × 分类/日期/进度三开关，逐字段容错解码 + 旧键尽力映射）+ `WidgetConfigStore`（`widget.<id>.style` 单键 JSON，复用同一 DataStore）+ `WidgetRefresher`（唯一刷新入口：一次性查 Room → 拼 RemoteViews → setTextColor/setTextViewTextSize/setBackgroundResource 全套用）+ `MorrowWidgetProvider`/`MorrowDetailWidgetProvider` + `MidnightRefreshScheduler`/`WidgetMidnightReceiver`（本地零点刷一次再排下一次，有精确权限走 `setExactAndAllowWhileIdle` 否则降级）+ `WidgetConfigActivity`（玻璃风配置台，LazyColumn 承载，含实时预览与事件绑定）。142 项单测全绿（新增 29：快照 17 + 样式 12）。**未真机验收。**
14. **M6 设置中心与备份**：`AppSettings`/`ThemeMode`（`isDark(systemDark)` 纯函数）+ `SettingsRepository.appSettings`（四条 Flow combine 成一个快照，另有 `snapshotSettings`/`applySettings` 供备份一次性读写）+ `GlassTheme(darkTheme, reduceTransparency, reduceMotion)`（`GlasmioTheme(isDark=)` + `GlassAppearance(reduceTransparency=)`，并暴露 `LocalGlassDark`/`LocalReduceMotion`）+ `data/backup/`：`BackupDocument`/`BackupCodec`（逐字段读、逐条事件解析、坏条目跳过计数、版本过高拒绝、时间兼收 ISO 与毫秒）+ `BackupMapper` + `BackupMerge` + `BackupRepository`（导出全量 / 导入 REPLACE|MERGE / 清空全部）+ `BackupFiles`（SAF 读写）+ `ui/settings/`：`SettingsScreen`（整页覆盖，外观/数据/提醒/关于四组，导入二次确认，一次性提示条）+ `SettingsViewModel`（导入后按 id 差集取消残留闹钟再整体重排、刷新小部件）；`WidgetConfigStore` 增 `widget.default_style` 兜底换机场景。167 项单测全绿（新增 25：编解码 17 + 合并与设置 8）。**未真机验收。**

15. **真机验收修复链（2026-11-11 ~ 11-12）**：撤销条到期只清内存不删盘 + `pushedAtMillis` 10 分钟内 restore 给满 6 秒、restore 提前到 `MainActivity.onCreate`；新建默认时间改为「此刻」（秒截断到分钟）；分类改名后新旧并存的别名映射；日历翻页改瞬时切页（与箭头同路径）、整页 `pointerInput` 遮罩防穿透、`clickable` 放到 `padding` 前；测试通知补 `contentIntent`、两入口 `EXTRA_FROM_NOTIFICATION` 收浮层落首页、真实提醒按 `EXTRA_EVENT_ID` 开详情；提醒落点 12h 内走 `setAlarmClock`、远期走 exact，新增 `ScheduleTrace` 自证排期与诊断页「最近一次排期」。决策：**保持 12h 窗口不缩**，缩窗会削弱 ROM 省电策略下的准点免疫。
16. **小部件二次打磨**：修正 `android:configure` 错指导致单事件无法选事件、默认样式串号（`KEY_DEFAULT_STYLE` 存/取都剥掉 `eventId`）；总览由静态 5 行改为 `ListView` + `RemoteViewsFactory` 集合视图（可滑动、行数随高度、点行直达详情）；刷新补 `notifyAppWidgetViewDataChanged` + adapter `data` URI 自增序号强制重绑；长按应用图标 → 先进配置台选样式/事件 → 再 `requestPinAppWidget`（`savePending`/`adoptPending` 认领）；设置页把多个同类实例合并成一行（`EXTRA_WIDGET_IDS` 整组保存刷新）。**ColorOS「服务卡片」是 OPPO 私有框架，标准 Android API 无法接入，已明确不做；pin 也不能指定桌面坐标。**
17. **减弱透明与年份范围**：`Glasmio` 内建 `reduceTransparency` 会让 `usesBackdrop=false`，把玻璃降级成纯黑/纯白色块；改为始终传默认 `GlassAppearance()`，只在 `GlassPanel` 内侧叠 0.55 alpha 柔和色纱，彩色渐变背景在开关两种状态下完全一致。日期滚轮年份从 `thisYear-1..+20` 改为 `CalendarMath.SELECTABLE_YEARS` 的 1900–2100，与农历年表 / 日历翻页边界共用，并修掉首帧 `snapshotFlow` 把越界旧数据改写成边界年的问题。
18. **M7 第一批（应用内更新）**：零新增依赖（`HttpURLConnection` + 现有 Gson）；`service/update/` 纯逻辑层 + 三路并发探测（**版本判定取版本号最大的一份**，测速只决定下载线路）+ Range 断点续传与 SHA-256 校验 + `PackageInstaller` 会话安装（系统仍会弹确认）/ FileProvider 兜底；设置 → 关于 → 检查更新，底部玻璃面板手动触发；`INTERNET` / `REQUEST_INSTALL_PACKAGES` / FileProvider、`lint.abortOnError=true`、可选 release 签名与 ABI 拆分（默认关闭）、`update/manifest.json` 模板与 CI JSON 校验。228 项单测全绿。**细节见 `docs/CHANGELOG.md` 同名条目。**
19. **品牌重塑（2026-11-12）**：应用名「萤」→「**朝夕**」；Gradle 根名 `ying`→`morrow`，本地仓库目录 `ying-native`→`morrow`；`applicationId` / `namespace` / Kotlin 包 `com.jiuxina.ying`→`com.morrowviento.morrow`；`YingApp`/`YingDatabase`/`YingWidgetProvider`/`Theme.Ying` 等改为 `Morrow*`；内部标识同步为 `morrow.db`、`morrow_settings`、`morrow://` scheme、`com.morrowviento.morrow.action.*`、`MORROW_*` 签名环境变量，Room schema 目录随包名迁移。新增自适应图标（晨昏渐变底 + 日轮 / 地平线 / 倒影 + Android 13 单色版）——此前 `AndroidManifest` 里**根本没有** `android:icon`。签名改为自有密钥方案：`scripts/create-keystore.sh` + `docs/SIGNING.md`，支持环境变量与 `~/.gradle/gradle.properties` 双通道。**破坏性变更**：旧「萤」不能覆盖升级，需先导出备份、卸载、装新包后导入。

20. **底栏改版 + Glasmio 0.1.3（2026-11-12）**：新增 `ui/component/HomeBottomBar.kt`（62dp 胶囊玻璃 + 左排序 / 中＋ / 右日历），首页标题区只留「⋯」，首页 `CreateFab` 移除（日历页保留）。**修掉「点排序冒出方形高亮」**：根因是 `Modifier.clickable` 的 Material ripple 只按布局边界裁剪、不认玻璃圆角；新增 `ui/glass/GlassPress.kt`（`rememberGlassPress` / `glassPressable` / `glassPressFeedback`）改用手势 + Glasmio `InteractiveHighlight` 液体光斑，事件卡片与日历页 `＋` 同步切换。Glasmio `0.1.2→0.1.3`（`GlassSurface` 自带 `onClick` 按压光学），封装层扩展 `GlassPanel(capsule=, tint=)` / `GlassCirclePanel(tint=)` / 公开 `glassPanelShape()`；`mipmap-anydpi-v26|v33` 合并为 `mipmap-anydpi` 并补 `<monochrome>`。**API 36 以下按压光效由库内跳过**，封装层用 0.10 alpha 白提亮兜底。`versionCode 41→42`、`versionName 4.0.0-alpha3`。228 单测全绿、lint 0/10。

## 未完成清单（按顺序，可直接执行）

1. ~~**M7 发版配合**~~ **已完成（2026-11-12 15:20）**：仓库改名 `MorrowViento/morrow`，已发布
   **`v4.0.0-alpha2-41`**（正式 Release，资产 `morrow-41-4.0.0-alpha2-debug.apk`，
   79,389,680 B，`sha256:04d81bdc…1fd83ed6`），`update/manifest.json` 已回填；
   流程固化为 `scripts/release.sh`，用法与三条硬规则见 **`docs/RELEASE.md`**。
   **仍待用户侧**：正式签名密钥（`~/.morrow` 目前为空），构建时用 `MORROW_KEYSTORE` /
   `MORROW_KEYSTORE_PASSWORD` / `MORROW_KEY_ALIAS` / `MORROW_KEY_PASSWORD` 注入
   （或写 `~/.gradle/gradle.properties` 的 `morrow.*`），生成与验证见 **`docs/SIGNING.md`**。
2. **M7 真机链路**：按 `docs/MANUAL-TEST.md` 的 N1–N9 走完「检查 → 下载 → 安装 → 升级」，含断网、无更新、镜像缓存落后、下载中断续传、安装确认弹窗、升级后数据与小部件无感迁移。
3. **全量回归**：Android 13/14/15/16 覆盖，以及 `MANUAL-TEST.md` 里仍未勾选的细项（跨零点刷新、备份云盘读写、闹钟图标归属与远期提醒、撤销跨进程重启、低端机玻璃帧率）。
4. **M0 遗留（可选）**：ktlint/detekt 静态检查接入 CI，不阻断当前代码。

验收标准与涉及文件见 `docs/TODO.md`，此处不重复。

## 关键决策及原因

| 编号 | 决策 | 原因 |
|---|---|---|
| D1 | 最低 Android 13（API 33） | Glasmio 强制要求，低于此版本无法渲染玻璃材质 |
| D2 | **不并行维护 Flutter 主线**（对原计划的修订） | 单人产能有限；双线并行会拖慢重构，且旧版无必须持续发版的诉求 |
| D3 | Glasmio 锁 `0.1.2`，仅 `ui/glass/` 可 import | 0.1.2 API 未稳定，封装层是唯一改动面；升级只改封装层 + 版本目录 |
| D4 | 不用 SF Symbols | Apple 字体许可风险，改用 Material / 自绘矢量图标 |
| D5 | Compose + ViewModel + Room + DataStore + WorkManager | 与 Glasmio 官方样例工程对齐，减少版本冲突 |
| D6 | **不需要 NDK/CMake**（对原计划的修订） | Glasmio AAR 已内置四 ABI 预编译 `.so`，本工程无原生源码；省 3–5 分钟构建并少一个失败点 |
| — | ~~`applicationId` 沿用 Flutter 版以便覆盖升级~~ **已废弃（2026-11-12 品牌重塑）**：`applicationId` = `com.morrowviento.morrow`，`versionCode 40` | 更名「朝夕」后不再与旧「萤」共存；代价是必须先导出备份、卸载旧包、装新包后导入。旧包名无法覆盖升级是有意取舍，不是遗漏；签名流程见 `docs/SIGNING.md` |
| — | DataStore **键名沿用 Flutter 原始键**（见 `LegacySettingsKeys`） | 迁移后无需二次映射，M4/M5/M6 可直接按旧键读取 |
| — | 时间存本地时区毫秒、提醒列表存 JSON | 避免 Room 的 `java.time` 适配器；提醒上限 5 条，独立建表不划算 |
| — | 旧数据单调对齐 Dart 的 `fromJson` 兜底 | 缺 `category` 默认「生活」、未知枚举回落默认值、无 `reminders` 读旧字段 `reminderMinutes` |
| — | 排序语义：置顶 > `distance`（未来升序，已过沉底且「刚过去」优先）> `targetDate` 升序 > `createdAt` 倒序，同序按标题 | 已过事件堆在最前会让首页变成"回忆录"；`distance` 排序下旧版行为如此 |
| — | 秒级刷新用**单一时间源**驱动整页重算（`combine` + 1s ticker） | 每个卡片各起协程会放大帧率抖动；单源还能顺带解决跨午夜天数翻转 |
| — | 新建走 FAB → 同页表单浮层；点卡片 → 详情浮层；日历从标题栏图标进入（整页覆盖） | 所有层都在同一个 backdrop 内，独立窗口/独立 Destination 会让玻璃材质失效 |
| — | 进度环 = `createdAt → target` 的已完成比例，重复事件不画 | 重复事件的 target 每年前移，进度无稳定语义 |
| — | 表单做成同页面浮层，不用 `ModalBottomSheet`/`Dialog` | 玻璃材质靠 `GlassBackdropHost` 采样，独立窗口会切断 backdrop，材质直接失效 |
| — | 提醒用 `AlarmManager` 槽位模型（`requestCode` = 槽位下标，`Intent.data` 带事件 id） | 取消无需额外持久化索引，扫 0..9 即可全清；事件之间即使槽位号相同，`filterEquals` 因 data 不同也不会互相覆盖 |
| — | **不引入 `work-runtime-ktx`**（对原计划的修订） | WorkManager 同样排不出精确闹钟，强制停止后也不跑；重建靠「冷启动 + 开机 + 时间/时区变更」三处对账已足够 |
| — | 无精确闹钟权限时降级为 `setAndAllowWhileIdle`，不拒绝排期 | 宁可迟到几分钟也别完全不提醒；降级状态在诊断页明说，不静默 |
| — | 单事件最多 2 次落点 × 5 条提醒 = 10 个闹钟，触发后立即重排下一次 | 年度重复若一次排满会被闹钟数量拖垮；「触发即重排」让排期自转，且系统里始终只有少量闹钟 |
| — | 滚轮自行实现（五列 + snap fling） | Glasmio 0.1.2 无此组件，Material3 无 iOS 式滚轮；日期日数随年月动态变化 |
| — | 分类以**名字**为唯一标识，重命名级联 `UPDATE events SET category` | 避免为一个展示性字段升 Room schema 并回填旧数据；用户可见效果与 id 引用一致 |
| — | 分类列表 = 存储列表 ∪ 事件里出现过的「孤儿分类」 | 旧数据里的自定义分类不能凭空消失，否则用户会觉得"分类丢了" |
| — | 删除被占用分类必须先选迁移目标，禁止静默删事件 | 数据安全优先；候选为空时提示先新建一个分类 |
| — | 分类浮层同样做同页浮层，叠在表单之上 | 与表单同一个 backdrop 约束；独立窗口会让玻璃材质失效 |
| — | 撤销＝**立即落库 + 快照回填**，不做延迟删除 | 延迟删除在进程被杀后无法重建意图；`expiresAt` 可持久化，快照回填还能让删除与状态切换共用一条回滚路径 |
| — | 撤销只保留**最近一次**，新操作顶替旧的 | 旧操作效果已落库；多重撤销需要栈式回滚，复杂度远高于收益 |
| — | `UndoBar` 最后绘制（盖在详情/表单浮层之上） | 浮层开着时 6 秒窗口照样在走，被遮住等于白给 |
| — | 保存表单**不进**撤销队列 | 保存是用户主动确认的动作，且可用「编辑」改回；撤销只服务删除/批量清理这类难复原操作 |
| — | 农历**自实现**（1900–2100 压缩年表，201 个 int，约 1.6 KB） | `android.icu.util.ChineseCalendar` 在纯 JVM 单测里跑不了；第三方库会为这点功能带整套干支/八字表。自实现可纯 JVM 测，且零包体增量 |
| — | 日历用**整页覆盖**（`AnimatedVisibility` + `BackHandler`），不引 `navigation-compose` | 必须留在同一个 `GlassBackdropHost` 内，独立 Destination/窗口会切断采样源；数据本就共用一个 ViewModel，导航图收益为零 |
| — | 日历状态按天刷新（独立 `dayTick`），不复用每秒 ticker | 42 格加三个月落点映射没必要每秒重算 |
| — | 月视图整月只套一个 `GlassPanel`，格子状态靠文字色/浅色块 | 42 个独立玻璃面在中低端机大概率掉帧，材质表现力并不因此提升 |
| — | 小部件数据源走**进程内只读快照**，不订阅 `EventRepository.observeAll()` | RemoteViews 拼完即弃，挂着的 Flow 只会在进程回收前白占一个协程与一个 Room 观察者 |
| — | 小部件刷新复用 M4 的对账链路 + 午夜闹钟，`updatePeriodMillis=0` | 系统周期更新最短 30 分钟且对齐上次刷新时刻，跨天那一刻必然错；主动推送才能让「明天→今天」当场翻转 |
| — | ~~小部件用 5 组写死的行~~ **已废弃（M5 真机反馈）**：总览改为 `ListView` + `RemoteViewsFactory` 集合视图 | 静态行放大后下方留白、不能滑动；集合视图让行数随高度、可上下滚。条目点击走 `setPendingIntentTemplate`（模板必须 `FLAG_MUTABLE`，否则丢 fill-in）；刷新必须 `notifyAppWidgetViewDataChanged`，且 adapter Intent 的 `data` URI 带自增序号才能强制重绑（extras 不参与 Intent 相等性） |
| — | 配色在代码里按 `uiMode` 判明暗，不用 `values-night` | RemoteViews 读不到主题属性，`values-night` 对 `setBackgroundResource` 不生效 |
| — | ~~减透明走 `GlassAppearance(reduceTransparency)`~~ **已废弃**：`GlasmioTheme` 一律传默认 `GlassAppearance()`，减透明由 `LocalReduceTransparency` 在 `GlassPanel` 内侧叠 0.55 alpha 色纱实现 | 反编译 0.1.2 AAR 确认该开关让 `usesBackdrop`/`usesRefraction` 一起变 false，玻璃直接退化成纯黑/纯白色块；底色渐变必须两种模式完全一致，只压面板透过率才符合「减弱透明」语义 |
| — | 新增 `LocalGlassDark`，`GlassBackdrop` 与 `glassTextPrimary/Secondary` 一律读它 | 用户可强制浅色/深色，若仍按 `isSystemInDarkTheme()` 取色，会出现「底按系统画、字按设置画」的分裂 |
| — | 设置行**自绘**，不引 `glasmio-preference` | `GlasmioListPreference` 要弹二级对话框，而主题只有三档，横排 chips 少一次点击；两套行的间距与字号不一致会看出接缝 |
| — | 备份时间存 **ISO-8601 本地时刻字符串**，解码兼容毫秒 | 与 Flutter 版 `toIso8601String()` 同语义、用户能直接打开看；兼容毫秒是为了将来改存时间戳时旧文件仍可读 |
| — | 导入分「合并 / 覆盖」，**合并不碰外观与小部件样式** | 用户合并通常是补另一台设备的事件，顺手改掉外观属于意外行为；覆盖才是整套替换 |
| — | 小部件样式额外存一份 `widget.default_style` | widgetId 由桌面宿主分配，换机必变；没有这份兜底，换机后每加一个小部件都要重设一遍 |

## 关键文件路径与约定

**仓库根**：`C:\Users\yunbo.wang\WorkBuddy\2026-11-09-15-57-01\morrow`（上级目录放方案文档与 Glasmio 源码快照，不在仓库内）

```
app/src/main/java/com/morrowviento/morrow/
  MainActivity.kt              # 入口：装配 EventViewModel + CategoryViewModel + ReminderViewModel + HomeScreen；通知点击经单例 intent 直达详情
  MorrowApp.kt                   # 手写 DI 容器 + 启动迁移 + 提醒渠道与冷启动排期对账
  ui/glass/GlassTheme.kt       # 唯一允许 import Glasmio 的地方（GlassTheme/GlassBackdropHost/GlassPanel/GlassCirclePanel/glassTextPrimary/glassTextSecondary）
  ui/home/                     # HomeScreen（折叠标题/排序/分区/空态）+ EventCardView（徽章与进度环）
  ui/event/                    # HomeUiState + EventPresentation（纯函数层，可单测）+ EventViewModel
  ui/calendar/                 # CalendarScreen + CalendarUiState/CalendarPresentation（纯函数）+ CalendarActions
  ui/category/                 # CategorySheet + CategoryViewModel
  ui/component/                # GlassControls（浮层共用控件）/ WheelPicker（五列滚轮，年份 1900–2100）
  ui/reminder/                 # ReminderSheet（诊断页）+ ReminderUiState + ReminderViewModel
  ui/settings/                 # SettingsScreen（整页覆盖）+ SettingsUiState/SettingsActions + SettingsViewModel（设置读写/备份/清空）
  data/backup/                 # BackupDocument/Codec/Mapper/Merge/Repository/Files（备份格式与 SAF 读写）
  data/settings/               # SettingsRepository + AppSettings/ThemeMode + LegacySettingsKeys
  data/EventRepository.kt      # 对外只暴露领域模型 + Flow
  data/db/                     # EventEntity / EventDao / EventMapper / MorrowDatabase / CategoryUsage
  data/legacy/                 # LegacyDataMigrator / LegacyEventParser / LegacyPrefsSplitter
  data/settings/               # SettingsRepository / LegacySettingsKeys
  data/category/               # CategoryRepository / CategoryRules / CategoryCodec
  data/undo/                   # UndoRepository / UndoCodec
  data/reminder/               # ReminderScheduler（接口）/ AlarmReminderScheduler / ReminderPermissions
  reminder/                    # ReminderNotifier / ReminderReceiver（投递 + 通知动作）/ ReminderBootReceiver
  domain/model/                # CountdownEvent / EventReminder / CountDirection / RepeatType / SortMode / EventCategory / UndoOperation
  domain/time/                 # CalendarMath / RepeatRule / LunarCalendar（1900–2100 农历年表）/ ReminderPlan（排期纯函数）
  widget/                      # Provider / Refresher / 配置台 / WidgetConfigStore / 设置页分组
  service/update/              # M7：ReleaseJson / UpdatePolicy / UpdateEndpoints / UpdateRepository / ApkDownloader / ApkInstaller
  ui/update/                   # M7：UpdateUiState / UpdateSheet（设置页底部更新面板）
```

**图标与签名（不在上面的 Java 树里）**：`app/src/main/res/drawable/ic_launcher_{background,foreground,monochrome}.xml`、`app/src/main/res/mipmap-anydpi-{v26,v33}/ic_launcher{,_round}.xml`、`scripts/create-keystore.sh`、`docs/SIGNING.md`、`update/manifest.json`。

**外部参考（务必按此为准）**
- 上一级 `ying-kotlin-refactor-plan.md`：里程碑与工作量基线（D6 的 NDK 部分已作废）。
- 上一级 `gs.kt`（glass 模块）、`gc.kt`（ui 模块）、`sample_v012.kt`：Glasmio **v0.1.2** 源码快照，查 API 用它们，不要查 main 分支。
- 上一级 `.ref/`：Flutter 版 `countdown_event` / `event_reminder` / `event_repeat` / `event_sort_mode` / `undo_operation` / `storage_service` 原始 Dart 源码，移植时逐字段对照。
- `app/schemas/.../1.json`：Room 基线，改表必须升版本并写迁移。

**约定**
- 品牌：应用名「**朝夕**」，工程名 `morrow`。新增代码、资源、数据键**不要再引入 `ying` 前缀**；唯一保留旧名的是外部计划文档《ying-kotlin-refactor-plan.md》。
- 业务代码禁止 import `io.github.neoruaa.glasmio.*`，一律走 `ui/glass/`。
- 排序语义：`distance`（距今天数）/ `targetDate`（目标日期）/ `createdAt`，置顶恒在最前，已完成折叠。
- **构建（唯一可行方式，2026-11-11 实测）**：`./gradlew` 会尝试重下 Gradle 并因 PKIX 证书校验失败（`~/.gradle/wrapper/dists/gradle-9.6.0-bin` 未被识别为完整分发）；shell 默认 `JAVA_HOME` 未设置且 `java` 是 1.8。必须显式指定工具链：
  ```bash
  export JAVA_HOME="/c/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/.toolchain/jdk/jdk-21.0.13+9"
  /c/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/.toolchain/gradle-9.6.0/bin/gradle \
      :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
  ```
  单测结果看 `app/build/test-results/testDebugUnitTest/*.xml`（`tests`/`failures`/`errors` 属性）。
- 提交信息用 `feat/fix/chore(scope): 中文描述`；每个里程碑结束产出可安装 APK，并同步更新 `docs/` 三件套（`HANDOFF.md` / `TODO.md` / `CHANGELOG.md`）。`docs/MANUAL-TEST.md` 是用户真机验收清单，勾选由用户维护，**是否提交由用户逐次决定**，不要顺手 `git add -A` 带走。

## 已知坑 / 待确认项

1. ~~**[阻塞级] 远程与本地分叉**~~ **已解决（T0）**：根因不是内容差异，而是 Windows `core.autocrlf=true` 让本地提交把 README 存成了 CRLF。`.gitattributes` 落地后差异自动消失，本地与远程同点于 `548edad`。**后续新增文件若再出现"整文件被改"，先查行尾，不要逐行比对。**
2. **Glasmio 的 main 分支 ≠ 0.1.2**：`GlasmioBackdropHost` 在 main 上多一个 `(sampledColor, modifier, overlayContent)` 重载，v0.1.2 没有。照 main 写会编译失败。
3. ~~**`lint.abortOnError = false`**~~ **已收紧（M7）**：现为 `true`，基线 0 errors / 11 warnings，构建不阻断。新增资源 / 时间 API 前先跑 lint。
4. **依赖缺口**：`work-runtime-ktx` **经 M4 评估确认不需要**（见决策表）；`navigation-compose` **已不需要**——首页/日历用同页整页覆盖 + `BackHandler` 实现，多一个导航图反而要额外处理 backdrop 采样源。M2a 也刻意绕开了 `material-icons-extended`，图标用 Canvas/文字自绘。
5. **Robolectric 已启用**（`WidgetPendingStyleTest` 等 DataStore / Context 测试）：纯 JVM + `runBlocking` 下 DataStore 1.3 的 Okio 在 Windows 会报 `Unable to rename settings.preferences_pb.tmp`，还可能误导成 "multiple DataStores"；必须 `@RunWith(RobolectricTestRunner)` + `runTest` + 每个用例独立文件名。
6. **迁移只在首启跑一次**：靠 `migration.legacy_v1_done` 幂等；调试迁移逻辑必须清应用数据，否则看不到任何动作。
7. ~~**撤销窗口跨进程重启的策略未定**~~ **已定（M2c）**：按 `expiresAt` 持久化恢复，重启后 6 秒窗口内仍可撤销；代价是必须「立即落库 + 快照回填」，不能延迟删除。
8. **低端机玻璃性能未实测**：Glasmio 有 FPS Counter，发布前需在低端机验证。「减弱透明」现为自己实现的面板色纱（不再走 Glasmio 降级开关），它在真实低端机上的功耗/帧率收益尚未测量。
9. **分类默认值「生活」、未知枚举回落默认值是刻意行为**：为与 Dart 侧一致，改动会导致旧数据语义漂移，勿"优化"。
10. **Flutter 原始源码不在本机**：只有上一级 `.ref/` 的 6 个 Dart 文件（模型 + storage service）可对照，其余页面行为需以计划文档的文件清单为线索。逐模块裁剪决策见 `docs/TODO.md` 的「移植范围」章节。
11. **`LocalDate.ofInstant` 在 Android 上要 API 34**（lint `NewApi` 抓出，minSdk 只有 33，真机会崩）。统一写 `Instant.ofEpochMilli(x).atZone(zone).toLocalDate()`；`LocalDateTime.ofInstant` 是 API 26，可放心用。新增时间 API 前先跑 lint 看 `NewApi`。
12. **PendingIntent 的身份 = action + data + component + requestCode，不含 extras**：同一事件的多条通知若 requestCode 相同又不设 data，会被系统合并成同一个，后写的覆盖先写的。因此 data 里必须带事件 id；重排时必须用 `FLAG_UPDATE_CURRENT`，否则新 extras 不生效、闹钟响了还是旧日期。
13. **通知渠道重要性建完即固定**：`reminders` 渠道是 HIGH。日后要调重要性必须换新 channelId 并做迁移，改代码对已安装用户无效。
14. **精确闹钟默认被拒**：Android 14+ 新装应用 `SCHEDULE_EXACT_ALARM` 默认拒绝，`canScheduleExactAlarms()` 为 false 时直接调 `setExact*` 会抛 `SecurityException`。已做 try/catch + 降级，并在诊断页引导开启。
15. **`goAsync()` 只给广播约 10 秒**：接收器里读 Room 必须走 IO 协程，并在 `finally` 里 `pending.finish()`，否则系统判超时杀进程。
16. **孤儿分类会被任意一次分类编辑「收编」**：`add/rename/delete` 都以「存储列表 ∪ 孤儿」的合并结果为基准并整体落盘，所以旧分类会被写进 `category_list_v1`。这是预期行为——否则重名校验会失效。
17. **分类浮层沿用 `WhileSubscribed(5s)` 状态流**：冷启动瞬间 `categories` 为空列表，此时立刻点 + 打开表单，分类 chips 只会显示当前值；数据流毫秒级就会补齐。属已知瞬态，不影响落库。
18. **`UndoBar` 的 z-order 是硬约束**：它必须留在 `HomeScreen` 最外层最后绘制。详情页占 0.88 高、表单占 0.94 高，一旦把撤销条放回它们之前，撤销窗口会在浮层下白白流逝。调整浮层顺序时先看这条。
19. **撤销的跨进程恢复依赖启动时序**：`MorrowApp.onCreate` 在迁移之后调用 `undoRepository.restore()`。若把它挪到 `MainActivity` 里，首帧会先闪一次"没有撤销条"，随后才出现；视觉上是可感知的抖动。
20. **带偏移量的时间戳按绝对时刻换算**：`UndoCodec` 对 `Z` / `+08:00` 用 `atZoneSameInstant` 落到本地时区。这与 `LegacyEventParser`（`OffsetDateTime.toLocalDateTime()` 丢偏移）**不一致**，但后者是旧数据兼容的既有实现，改动会改变旧事件日期解释，**不要顺手统一**。
21. **撤销不是"编辑回滚"**：`save()` 有意不入队；若日后要给编辑加撤销，需要把 `UndoKind` 扩到编辑语义，并注意"撤销编辑"与"撤销删除"同时存在时的顶替关系。
22. **农历年表覆盖的是「农历年」1900–2100，不是公历年**：公历上界一直延伸到 2101 年初（`2101-01-01` = 农历 2100 腊月初二，合法可算），真正的越界点是 2101 年春节之后。写测试别拿 `2101-01-01` 当越界样本。
23. **`LunarCalendar.cellLabel` 节日优先于月名**：正月初一显示「春节」而不是「正月」，八月十五显示「中秋」而不是「十五」——这是日历格子的既定展示规则，不是 bug。
24. **日历状态按天刷新，不要接回每秒时间源**：`EventViewModel.calendar` 用独立 `dayTick`（30 秒轮询 + `distinctUntilChanged` 到 `LocalDate`）。若改成 `ticker`，42 格加三个月的落点映射会每秒重算并整页重组。
25. **日历条目刻意不复用 `EventCardModel`**：卡片带 `remainingToday`/`progress` 这类依赖 `now` 的字段，放进按天刷新的状态里要么立刻变味、要么每秒触发重组。`CalendarEntry` 只存「哪天、差几天」。
26. **月视图整月只套一个 `GlassPanel`**：42 个独立玻璃面在中低端机大概率掉帧，格子状态用文字色/浅色块表达即可。若确要"每格玻璃"，先在真机量帧再改，别直接动结构。
27. **年视图/列表视图共用 `CalendarNav.month` 作锚点**：切换视图不新建位置状态，避免三套游标互相不同步；翻页一律走 `CalendarNav.clamp`，防止用户翻到没有农历数据的年份。
28. **整页覆盖的视图必须自带 `GlassBackdrop`**：日历叠在首页同一个 `Box` 上，若根节点没有不透明底色，下层首页的标题/胶囊/卡片会整片透出来，看起来像"两页重叠"。底色务必用 `ui/glass/GlassBackdrop`（渐变只有这一份）——玻璃面板采样的是 host 的 `LayerBackdrop`，只有底色与采样源同源，交叠处材质颜色才对得上。**加浮层不需要底色（本来就该透），整页替换必须加。**
29. **`verticalScroll` 里不能放 `LazyColumn`**：纵向约束是 infinity，Lazy 组件在测量阶段直接抛 `IllegalStateException: ... infinity maximum height constraints`。M2b 的点 + 号必崩就是这么来的（表单是 `verticalScroll`，日期滚轮是 `LazyColumn`）。现在 `WheelColumn` 自己定高（`itemHeight * visibleRows`），新增任何可滚列表进浮层前先确认高度有界。
30. **日历页的 FAB 不能复用首页那颗**：日历不透明后会把首页 FAB 整个盖住，所以 `CalendarScreen` 有自己的 `CreateFab`（新建日期 = 选中日，没选中就今天）。两处共用 `ui/component` 里的 `CreateFab`，别再各写一份。
31. **切视图/新增整页后先想「有没有第二层」**：目前是首页 / 日历 / 设置三层。后两者都在根节点铺 `GlassBackdrop` 并自带 `BackHandler`，新增整页界面必须照做，否则会复现「两页重叠」。
32. **`BroadcastReceiver` 没有 `context` 属性**：`WidgetRefreshRunner.run()` 必须把 `onReceive`/`onUpdate` 的 context 显式传进来，想从 receiver 上取 `receiver.context` 会编译不过（`Unresolved reference: context`）。
33. ~~**RemoteViews 不滚动、塞不下就裁掉**~~ **已被集合视图取代**：总览用 `ListView` 后行数随高度、可上下滑，但 `widget_overview_info.xml` 的 `minHeight=150dp/targetCellHeight=3` 仍按标准 3 行定；样式预设的 1/3/5 行现在控制字号与密度，改默认值前仍要在真机看一眼首屏。`ListView` 内不要再嵌可滚容器。
34. **`updatePeriodMillis=0` 才是对的**：小部件的跨天刷新由 `MidnightRefreshScheduler` 主动驱动。别为了「省事」把它改回 1800000——系统周期最短 30 分钟并对齐上次刷新时刻，零点那一次必然滞后。
35. **小部件配色不能用 `values-night`**：RemoteViews 拿不到主题属性，明暗必须在 `WidgetStyle.colors()` 里按 `Configuration.UI_MODE_NIGHT_*` 判断，再用 `setInt(root, "setBackgroundResource", …)` 换 drawable。
36. **别给配置 Activity 加 `APPWIDGET_CONFIGURE` 的 intent-filter**：两个小部件共用两个配置页，加了 filter 会让启动器在配置时弹出二选一。入口只由 `appwidget-provider` 的 `android:configure` 指定。**ColorOS 真机长按只给「拖动大小」，声明 `widgetFeatures="reconfigurable"` 也不能保证出现「编辑」；应用内设置 → 桌面小部件是唯一可靠入口。**
37. **`widgetId` 会被系统回收复用**：`onDeleted` 必须删掉对应配置，否则新添加的小部件会继承上一任的样式与绑定事件。
38. **旧版小部件样式键名无法确认**：`.ref/` 只有 6 个 Dart 模型/存储文件，没有 widget 代码。`WidgetStyleCodec.fromLegacy` 是「尽力映射 + 回落默认」，键名候选集中在 `WidgetStyle.LEGACY_KEY_CANDIDATES`；将来拿到真实旧键名只改那张表，不要动样式模型。旧配置按 widgetId 对不上，因此只当默认值用，不写回新键。
39. **`GlasmioTheme` 的明暗参数叫 `isDark`，不是 `darkTheme`**：写错会报 `No parameter with name 'darkTheme' found`。完整签名是 `GlasmioTheme(isDark, colorScheme, typography, colors, appearance, dimensions, shapes, spacing, dynamicTypeSize, accentColor, content)`，只传 `isDark` 与 `appearance` 即可，其余有默认值。
40. **`reduceTransparency` 只能从 `GlassAppearance` 传**：`GlassAppearance(liquidGlassEnabled, reduceTransparency, adaptiveColorScheme)`，它的 `usesRefraction`/`usesBackdrop` 是派生属性，不要去构造。**此条只记 API 形状**：业务侧已不再使用该开关，原因见决策表与坑 8。
41. **整页覆盖视图的底色与文字色必须读 `LocalGlassDark`**：日历、设置页都踩过——直接 `isSystemInDarkTheme()` 在用户强制主题时会与 `GlassTheme` 的决定相反。
42. **`RemoteViews` 的 widgetId 换机后必然不同**：备份里按 id 存的样式在新设备上一个都对不上，所以 `WidgetConfigStore.save` 同时写一份 `widget.default_style`，`load` 的回落链是「本 id → default_style → 旧键 → 默认」。`loadAll()` 靠 `widgetIdOf()` 把非数字前缀滤掉，别改成直接 `toInt()`。
43. **`ReminderScheduler.syncAll` 不清残留闹钟**：它只对传入的事件逐个 `sync`，被删掉的 id 的闹钟会一直留着。导入 / 清空之后必须按「导入前的 id 集合 − 导入后的 id 集合」逐个 `cancel`，再 `syncAll`（见 `SettingsViewModel.reconcile`）。
44. **SAF 写文件要用 `openOutputStream(uri, "wt")`**：默认模式不截断，覆盖导出时若新内容比旧内容短，文件尾部会残留上一份的字节，JSON 直接解析失败。
45. **`Modifier.padding(horizontal = x, top = y)` 是非法组合**：只有 `(start,top,end,bottom)` 与 `(horizontal,vertical)` 两种重载，混用编译报 `None of the following candidates is applicable`，报错行指向调用处但不说明原因。
46. **`JsonObject.getAsJsonArray` 类型不符会直接抛**：备份文件可能被用户手工改坏，一律走 `get(key)?.takeIf { it.isJsonArray }?.asJsonArray` 这类空安全入口。
47. **坏事件要在解码阶段就跳过并计数**：留到 `toDomain` 才发现时间解析不了，那条会被 `mapNotNull` 静默丢弃，用户只看到「导入了 N 个」却对不上账。
48. **本机无设备也无模拟器**：`adb devices` 为空、`.toolchain/sdk` 未装 emulator；开发机只能保证编译 / 单测 / lint。真机验收由用户在 OnePlus PJZ110 / Android 17 上按 `docs/MANUAL-TEST.md` 执行，反馈按编号报。
49. ~~**M2a/M2b 尚未提交**~~ **已解决（T0）**：`548edad` 已推送，upstream 已建立。后续每个里程碑结束各自提交，避免再次堆积。
50. **滚轮吸附未实机校准**：`WheelColumn` 依赖 `itemHeight`(34dp) 与实际行高一致才能居中吸附，`rememberSnapFlingBehavior` 在停止滚动后回写下标；真机上若出现偏移一行的现象，调 `itemHeight` 或改用 `firstVisibleItemIndex + 偏移判断`。
51. **分类改名不是跨库事务**：Room 与 DataStore 无法同事务，写入顺序固定「先改事件、再改列表」。若第二步失败，结果是事件已改名而列表仍是旧名，旧名会以孤儿分类形式重新出现——数据不丢、可再操作。反之先写列表会出现「列表有、事件挂着已消失的旧名」的隐性不一致，故不采用。**不要**为此改回 id 引用：那要升 Room 版本 + 回填旧数据，收益不足。
52. **表单打开期间改名/删除其所属分类**：表单持有的是旧名字符串，保存后事件会挂回旧名、重新变成孤儿分类（管理页显示「旧数据中的分类」）。不阻断、不丢数据，属刻意取舍；若后续觉得刺眼，需把表单的 category 改成跟随仓库解析。
53. **推送常因代理 502 失败**：`git push` 报 `CONNECT tunnel failed, 502` 时不要改代码或换远端，挂后台按间隔重试即可（`a525f2a` 第 24 次成功，`b00696e` 一次成功）。提交本身是本地完成的，不阻塞交付。


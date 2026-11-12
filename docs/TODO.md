# TODO — 萤 · Kotlin 重构

## 项目概述

用 Kotlin + Jetpack Compose + Glasmio（Liquid Glass）重写倒数日应用「萤」（Android 13+），取代 Flutter 主线。

## 当前阶段

M0 工程基建、M1 数据层、M2a 首页列表、M2b 新建表单、M2b2 分类管理、M2c 详情与撤销、M2d 日历三视图、M4 提醒通知、M5 桌面小部件、M6 设置中心与备份已完成，**M2a–M6 真机验收基本通过（2026-11-12 用户确认）**。本地 `assembleDebug + testDebugUnitTest + lintDebug` 通过：**228 项单测全绿、lint 0 errors / 11 warnings**（`abortOnError=true`），debug APK 约 77 MB。当前处于 **M7 应用内更新与发布**，第一批已落地。

真机首轮反馈的两个运行时问题已修：日历整页覆盖缺底色导致与首页重叠、日期滚轮所在的 `verticalScroll` 表单里 `LazyColumn` 无界高导致点 + 号闪退（详见 `docs/CHANGELOG.md` 同名条目）。

**m6 的「打开即闪退」已结案**：根因是 `YingApp` 冷启动自愈链无异常兜底，协程内未捕获异常直接杀进程；现改为 `warmStart()` + 逐步 `step()` 隔离，真机确认不再闪退。诊断设施 `util/StartupTrace` 已收敛为仅 debug 包落盘、正式包不接管崩溃处理器。**遗留**：被吞掉的具体异常尚未归因，若取得 `Android/media/com.jiuxina.ying/startup.log` 可确认是哪个自愈步骤失败。

仓库 `https://github.com/MorrowViento/ying-native` · 分支 `main` · 已对齐 `origin/main`（HEAD `a525f2a`，代理 502 下后台重试第 24 次推送成功）· 版本 `4.0.0-alpha1 (40)`

M7 第一批（检查 / 下载 / 安装 / UI / 工程配置）已落地，见 `docs/CHANGELOG.md`。**尚未完成**：发布首个 GitHub Release、正式签名密钥注入、真机走完「检查→下载→安装→升级」全流程、Android 13/14/15/16 全量回归。其中 M4 与小部件的「跨零点刷新」、备份的云盘读写、M7 的 N 组仍必须真机才能验证。**逐项清单见 `docs/MANUAL-TEST.md`**（按 P0/P1/P2 分级，反馈时按编号报）；该文件目前有用户勾选的 40 项真机验收记录，**按用户要求暂不提交、不推送**，提交其他文档时不要 `git add -A`。

## 移植范围（2026-11-11 二次定稿）

只保留下面这些裁剪，**未列出的模块一律按原计划全量实现**。

| 模块 | 结论 |
|---|---|
| 小部件应用内配置台 | **适度精简**：保留样式预设 + 字号 + 配色 + 元素显隐；逐元素对齐/壁纸取色联动不做 |
| 壁纸取色 / 节日皮肤 | 延后 v4.1 |
| 字体库（在线 + 本地导入） | 延后 v4.1，本期用系统字体 |
| 相册背景服务 | 延后 v4.1 |
| 头像 / Emoji / 单位文案 | 删除，不做 |
| 赞助页 | 取消（关于页保留） |
| 单事件小部件 / 年视图 / 农历 / 提醒诊断页 | **保留**（不砍） |
| 应用内更新 | **全量**：Releases 检查 + 镜像测速 + 断点续传 + PackageInstaller 静默安装 |
| 分类体系 | **全量**：预置分类 + 自定义增删改，不做「只用固定分类」的简化 |
| 滚轮日期选择器 / 搜索筛选排序栏 / 已完成折叠 / 6 秒撤销 | 保留（按原计划） |

> 估算随之回到约 62–72 人日（12–14 周）。

## 待办任务

按执行顺序排列；`[ ]` 前的编号即依赖顺序，`→` 表示串行前置。

- [x] **T0 推送一致性核对** —— 已完成（2026-11-11）：复核确认差异仅为 README 换行符（`git diff --ignore-all-space` 为空），根因是 `core.autocrlf=true`；新增 `.gitattributes`（`* text=auto eol=lf`）后 `git reset --mixed origin/main`（工作区零损失），拆两次提交（`c02adc9` 行尾统一 / `548edad` M2a+M2b+docs）并 `push -u origin main`。验收结果：`git status -sb` 为 `## main...origin/main`（无 ahead/behind），CI run 42983525295 全绿。
- [x] **M2a 首页事件列表** —— 已实现：`EventViewModel`（Room Flow + 排序偏好 + 每秒时间源 combine）、`EventPresentation`（纯函数排序：置顶优先 / 距离（已过沉底且刚过优先）/ 日期 / 创建时间倒序）、`HomeScreen`（折叠标题、排序 chip、进行中与已完成分区、空态/加载/错误态）、`EventCardView`（天数徽章 + 进度环 + 今日剩余 `HH:mm:ss`）。验收：三种排序与置顶单测通过（10 项），`assembleDebug + testDebugUnitTest + lintDebug` 通过；**真机视觉待验**（见下条）。
- [x] **M2a/M2b 真机验收** —— 2026-11-12 用户确认基本通过（逐项见 `docs/MANUAL-TEST.md`）。原验收要求：安装 `app/build/outputs/apk/debug/app-debug.apk`（本机无设备/模拟器，未经实机确认）；验收：① 卡片玻璃材质正常、倒计时逐秒跳动、今日事件显示剩余时分秒、排序 chip 循环三档且杀进程后保持、已完成折叠、空库空态；② 点 + 打开表单，滚轮年/月/日/时/分能选中并吸附居中（`itemHeight` 34dp 与实际行高需实机校准），空标题点保存有红字提示，保存后列表立即可见，杀进程重启仍在，全天事件提醒锚点为 09:00。
- [x] **M2b 事件新建/编辑表单** —— 已实现：`EventFormState`（纯函数：校验/落库映射/日期夹取/提醒增删去重上限/快捷日期）、`WheelDateTimePicker`（年/月/日/时/分五列滚轮 + snap fling，全天隐藏时间列）、`EventEditSheet`（同页面浮层而非 ModalBottomSheet，避免 backdrop 断裂导致玻璃失效；标题/滚轮/全天/方向/每年重复/快捷日期/分类 chips/提醒 chips/备注）、`EventViewModel.save`、首页 FAB 接入。验收：9 项表单单测 + 41 项总计全绿，`assembleDebug + lintDebug` 通过；真机交互待上条验收。
- [x] **M2b2 分类管理** —— 已实现：`EventCategory`（预置 7 类）+ `CategoryRules` 纯函数（归一化/合并孤儿/增删改校验/至少保留一项/12 字上限）+ `CategoryCodec`（DataStore `category_list_v1` 存 JSON）+ `CategoryRepository` + `CategorySheet` 浮层（列表、行内重命名、删除迁移确认）；表单 chips 改读仓库并新增「管理分类…」入口。**策略已定**：分类以名字为身份，重命名级联更新事件（历史事件显示新名）；删除被占用分类必须先选迁移目标，不做静默丢弃。验收：规则层 15 项 + 编解码 6 项单测通过；`assembleDebug + lintDebug` 通过；**浮层交互待真机**。
- [x] **M2c 详情页与 6 秒撤销** —— 已实现：`UndoOperation` + `UndoCodec` + `UndoRepository`（5 类操作统一为事件快照，`expiresAt` 持久化，重启后按剩余时间恢复）；`EventDetailScreen` 浮层（全字段 + 完成/置顶/编辑/删除）；`UndoBar` 倒计时复用每秒时间源；「⋯」菜单提供清空已完成/清空全部（后者二次确认）。**与原文的偏差**：改为「立即落库 + 快照回填」，而非「窗口内不真正落库」——延迟删除在进程被杀后无法重建意图，而快照 + 到期时刻可以持久化，且删除与状态切换能共用同一条回滚路径。验收：72 项单测 0 失败；删除/批量清理/状态切换均入队，新操作顶替旧操作不叠加；`assembleDebug + lintDebug` 通过；**真机交互待验**。
- [x] **M2d 日历视图** —— 已实现：`LunarCalendar`（自实现 1900–2100 压缩年表，零依赖零包体增量）+ `ui/calendar/`（月视图 42 格 / 年视图 12 月卡片 / 按月分组列表）；农历与星期副标题、节日优先显示；首页标题栏新增日历入口，日历整页覆盖且与首页共用同一个 `EventViewModel` 与 backdrop。**与原文的偏差**：农历不引库而自实现——`android.icu.util.ChineseCalendar` 无法在纯 JVM 单测里跑，第三方库为这点功能要带上整套干支表；年表仅 201 个 int，并已用 2019–2031 春节日期与 6 个闰月年份交叉校验。验收：101 项单测 0 失败（农历 13 + 日历 16）；重复事件按当年周年落位（2 月 29 日平年夹到 28 日、不早于首次目标）已单测锁定；**农历与系统日历的人工比对、三视图视觉待真机**。
- [x] **M4 提醒通知** —— 已完成。`ReminderPlan` 纯函数排期（全天锚点 09:00；年度重复按当次周年；单事件 2 次落点 × 5 条提醒 = 10 个闹钟上限）+ `AlarmReminderScheduler`（槽位 requestCode + `Intent.data` 区分事件，`setExactAndAllowWhileIdle`，无精确权限时退化不精确）+ `ReminderReceiver`（触发 / 完成 / 稍后 10 分钟）+ `ReminderBootReceiver`（开机、覆盖安装、时间与时区变更全量对账）+ `ReminderNotifier`（高优先级渠道、日期取当次落点）+ `ReminderPermissions`（两项权限探测与跳转）+ 提醒诊断页（权限状态 / 排期列表 / 测试通知 / 手动重排期）；`EventViewModel` 在保存、删除、完成、批量清理、撤销后同步闹钟；冷启动全量对账。**未真机验收。**
  剩余验收项（只能在真机做）：锁屏与 Doze 下准时触发、通知「完成」「稍后 10 分钟」动作生效、重启后闹钟自动重建、跨年重复事件落点正确、时间/时区变更后不漂移、诊断页条目与实际闹钟一致。
- [x] **M5 桌面小部件** —— 已完成（未迁移旧 Kotlin 实现，按裁剪范围重写）：总览 + 单事件两个 provider、`MidnightRefreshScheduler` 跨天刷新、进程内只读快照替代 method channel；配置台保留样式预设 + 字号 + 配色 + 元素显隐 + 单事件绑定。验收项（显示/跨天刷新/点击跳转/旧样式映射）待真机确认。
- [x] **M6 设置中心** —— 已完成：外观（主题三态 / 减弱透明 / 减弱动效，键名沿用 Flutter 原始键）+ 数据（导出/导入备份、清空全部，SAF 免权限）+ 提醒（通知与精确闹钟状态跳转）+ 关于（版本 / 开源许可 / 项目主页）。设置行自绘而非 `glasmio-preference`（理由见 HANDOFF 决策表）。备份为单文件 JSON，含事件/分类/外观/小部件样式，导入分「合并 / 覆盖」两档。验收项待真机确认。
- [ ] **M7 更新与发布（进行中）** —— 已完成：GitHub Releases 检查（三路并发 + 可选镜像测速）、断点续传下载、`PackageInstaller` 会话安装 + 系统安装页兜底、更新面板与进度 UI、`INTERNET`/安装权限/FileProvider、`lint.abortOnError = true`、可选 release 签名与 ABI 拆分（默认关闭）、`update/manifest.json` 清单模板、`UpdatePolicyTest`/`ReleaseJsonTest`。**不引 WorkManager**（更新由用户主动发起且全程可见，协程取消即停、靠 `.part` 续传即可）。  
  仍待：发布首个带 APK 资产的 GitHub Release、注入正式签名密钥、真机走完 N1–N9、Android 13/14/15/16 全量回归、旧用户升级后数据与小部件无感迁移确认。
- [ ] **M0 遗留（可选）** —— 计划里承诺的 ktlint/detekt 尚未接入；做法：加 `spotless` 或 `ktlint` Gradle 插件并接入 CI；验收：CI 增一步静态检查且不阻断当前代码。建议在 M2 结束、代码量上来之前做。

## 涉及文件

| 任务 | 主要文件 / 目录 |
|---|---|
| T0 | 仅 git 操作，无文件改动 |
| M2a | `ui/event/{HomeUiState,EventViewModel}.kt`、`ui/home/{HomeScreen,EventCardView}.kt`、`domain/model/SortMode.kt`、`ui/glass/GlassTheme.kt`、`MainActivity.kt` |
| M2b | `ui/event/EventEditSheet.kt`、`ui/component/WheelDateTimePicker.kt`（新建）、`data/EventRepository.kt` |
| M2b2 | `ui/category/{CategorySheet,CategoryViewModel}.kt`（新建）、`data/category/{CategoryRepository,CategoryRules,CategoryCodec}.kt`（新建）、`domain/model/EventCategory.kt`（新建）、`ui/component/GlassControls.kt`（新建，两个浮层共用）、`data/db/{EventDao,CategoryUsage}.kt`、`ui/event/EventEditSheet.kt` |
| M2c | `ui/event/{EventDetailScreen,UndoBar,EventActions,EventViewModel,HomeUiState}.kt`、`data/undo/{UndoRepository,UndoCodec}.kt`（新建）、`domain/model/UndoOperation.kt`（新建）、`ui/home/{HomeScreen,EventCardView}.kt` |
| M2d | `ui/calendar/{CalendarUiState,CalendarActions,CalendarScreen}.kt`（新建）、`domain/time/LunarCalendar.kt`（新建）、`ui/event/EventViewModel.kt`、`ui/home/HomeScreen.kt`、`MainActivity.kt` |
| M4 | `domain/time/ReminderPlan.kt`、`data/reminder/{ReminderScheduler,AlarmReminderScheduler,ReminderPermissions}.kt`、`reminder/{ReminderNotifier,ReminderReceiver,ReminderBootReceiver}.kt`、`ui/reminder/{ReminderUiState,ReminderViewModel,ReminderSheet}.kt`、`AndroidManifest.xml`（权限 + 两个 receiver）、`res/drawable/ic_notification.xml`、`YingApp.kt`、`MainActivity.kt`、`ui/event/EventViewModel.kt`、`ui/home/HomeScreen.kt` |
| M5 | `widget/`、`service/widget/`（均由旧工程迁移） |
| M6 | `ui/settings/`、`data/settings/SettingsRepository.kt`、`service/backup/` |
| M7 | `service/update/`、`ui/update/`、`ui/settings/`、`ui/component/WheelPicker.kt`、`domain/time/CalendarMath.kt`、`app/build.gradle.kts`、`AndroidManifest.xml`、`res/xml/file_paths.xml`、`update/manifest.json` |
| 全局 | `gradle/libs.versions.toml`（每步先补依赖）、`.github/workflows/android-ci.yml` |

## 优先级与依赖

```
T0 → M2a → M2b → M2b2 → M2c → M2d
                       ↘ M4（依赖 M2b 的提醒字段）
                       ↘ M5（可与 M4 并行，数据源同为 M1）
                       ↘ M6（可与 M4/M5 并行）
                                 ↘ M7（最后，需全量回归）
```

- 阻塞：`M2a` 是后续所有页面的地基；`M2b2` 依赖 `M2b` 的表单分类入口；`M4` 依赖 `M2b` 产生的提醒数据；`M7` 必须最后。
- 可并行：`M5` 与 `M6` 互不依赖（均只依赖已完成的 M1）。
- 顺序铁律：**先补依赖再写页面**（`androidx.navigation:navigation-compose`、`material-icons-extended` 目前均未引入）。`work-runtime-ktx` 经 M4 评估后确认**不需要**：WorkManager 同样排不出精确闹钟，重建改由「冷启动 + 开机 + 时间/时区变更」三处对账承担。

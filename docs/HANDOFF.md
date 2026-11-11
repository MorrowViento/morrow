# HANDOFF — 萤 · Kotlin 重构

## 任务背景

把「萤」倒数日应用从 Flutter 重写为 Kotlin/Compose + Glasmio Liquid Glass（Android 13+），包名与 Flutter 版保持一致以便覆盖升级。

## 已完成清单

1. **M0 工程基建**：AGP 9.4.1 / Kotlin 2.4.21 / Gradle 9.6.0 / compileSdk 37 / minSdk 33 / JDK 21；Glasmio 0.1.2 双模块依赖；`ui/glass/` 封装层；无 NDK/CMake。
2. **CI**：`.github/workflows/android-ci.yml`，`assembleDebug + testDebugUnitTest + lintDebug` 出 debug APK 工件。
3. **M1 数据层**：Room `events`（schema 已导出）+ DataStore `ying_settings` + 幂等旧数据迁移 + 22 项单测全绿。
4. **应用容器**：`YingApp` 手写依赖装配（DB / EventRepository / SettingsRepository），启动即跑迁移（失败不阻断）。
5. **M2a 首页列表**：`EventViewModel` + `EventPresentation`（纯函数排序/进度）+ `HomeScreen` + `EventCardView`；玻璃封装层增文字色与圆形面板。
6. **M2b 新建表单**：`EventFormState` + `WheelDateTimePicker`（五列滚轮）+ `EventEditSheet`（同页浮层）+ FAB 落库；41 项单测全绿（新增 9 项）。**未真机校准、未提交。**
7. **文档**：`README.md`（决策表 + 包结构 + Glasmio 版本差异）、`docs/TODO.md`、`docs/CHANGELOG.md`、本文件。

## 未完成清单（按顺序，可直接执行）

1. `M2a/M2b` 真机验收：安装 `app/build/outputs/apk/debug/app-debug.apk`，看玻璃/秒跳/排序/空态，以及表单滚轮吸附与保存落库（本机无设备，纯视觉与交互项全压在这）。
2. `T0` 远程对齐：fetch 已完成、差异已确认只有 README 空白；待你点头后 `.gitattributes` + `reset --mixed origin/main` + 单次提交 + `push -u`。
3. `M2b2` 分类管理：预置 + 自定义增删改（DataStore 新键，兼容旧事件里出现过的分类）；顺手把表单里的 `DEFAULT_CATEGORIES` 常量换成仓库读取。
4. `M2c` 详情页 + 6 秒撤销（四类操作：delete / toggleCompleted / clearCompleted / clearAll）。
5. `M2d` 日历三视图（月/年/按月分组列表）+ 农历星期副标题，与首页共享 ViewModel。
6. `M4` 提醒通知（AlarmManager + 通知动作 + 权限引导 + 重启重建 + 提醒诊断页）。**先补 `work-runtime-ktx`。**
7. `M5` 桌面小部件（总览 + 单事件；配置台保留预设/字号/配色/显隐）。
8. `M6` 设置中心（glasmio-preference + 备份导入导出 + 关于页）。
9. `M7` 应用内更新全链路（测速选路 + 断点续传 + 静默安装）+ 全量回归 + 签名发版 + 收紧 lint。

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
| — | `applicationId` 保持 `com.jiuxina.ying`，`versionCode 40` | 让 Flutter 老用户能直接覆盖升级，数据留在同一沙盒 |
| — | DataStore **键名沿用 Flutter 原始键**（见 `LegacySettingsKeys`） | 迁移后无需二次映射，M4/M5/M6 可直接按旧键读取 |
| — | 时间存本地时区毫秒、提醒列表存 JSON | 避免 Room 的 `java.time` 适配器；提醒上限 5 条，独立建表不划算 |
| — | 旧数据单调对齐 Dart 的 `fromJson` 兜底 | 缺 `category` 默认「生活」、未知枚举回落默认值、无 `reminders` 读旧字段 `reminderMinutes` |
| — | 排序语义：置顶 > `distance`（未来升序，已过沉底且「刚过去」优先）> `targetDate` 升序 > `createdAt` 倒序，同序按标题 | 已过事件堆在最前会让首页变成"回忆录"；`distance` 排序下旧版行为如此 |
| — | 秒级刷新用**单一时间源**驱动整页重算（`combine` + 1s ticker） | 每个卡片各起协程会放大帧率抖动；单源还能顺带解决跨午夜天数翻转 |
| — | 新建走 FAB → 同页表单浮层；卡片点击暂不响应 | 表单已可用；详情页属 M2c，先不给假入口 |
| — | 进度环 = `createdAt → target` 的已完成比例，重复事件不画 | 重复事件的 target 每年前移，进度无稳定语义 |
| — | 表单做成同页面浮层，不用 `ModalBottomSheet`/`Dialog` | 玻璃材质靠 `GlassBackdropHost` 采样，独立窗口会切断 backdrop，材质直接失效 |
| — | 滚轮自行实现（五列 + snap fling） | Glasmio 0.1.2 无此组件，Material3 无 iOS 式滚轮；日期日数随年月动态变化 |

## 关键文件路径与约定

**仓库根**：`C:\Users\yunbo.wang\WorkBuddy\2026-11-09-15-57-01\ying-native`（上级目录放方案文档与 Glasmio 源码快照，不在仓库内）

```
app/src/main/java/com/jiuxina/ying/
  MainActivity.kt              # 入口：装配 EventViewModel + HomeScreen，FAB 目前只弹提示
  YingApp.kt                   # 手写 DI 容器 + 启动迁移
  ui/glass/GlassTheme.kt       # 唯一允许 import Glasmio 的地方（GlassTheme/GlassBackdropHost/GlassPanel/GlassCirclePanel/glassTextPrimary/glassTextSecondary）
  ui/home/                     # HomeScreen（折叠标题/排序/分区/空态）+ EventCardView（徽章与进度环）
  ui/event/                    # HomeUiState + EventPresentation（纯函数层，可单测）+ EventViewModel
  ui/calendar/  ui/settings/   # 待建
  data/EventRepository.kt      # 对外只暴露领域模型 + Flow
  data/db/                     # EventEntity / EventDao / EventMapper / YingDatabase
  data/legacy/                 # LegacyDataMigrator / LegacyEventParser / LegacyPrefsSplitter
  data/settings/               # SettingsRepository / LegacySettingsKeys
  domain/model/                # CountdownEvent / EventReminder / CountDirection / RepeatType / SortMode
  domain/time/                 # CalendarMath / RepeatRule
  service/  widget/            # M4/M5/M6/M7 待建
```

**外部参考（务必按此为准）**
- 上一级 `ying-kotlin-refactor-plan.md`：里程碑与工作量基线（D6 的 NDK 部分已作废）。
- 上一级 `gs.kt`（glass 模块）、`gc.kt`（ui 模块）、`sample_v012.kt`：Glasmio **v0.1.2** 源码快照，查 API 用它们，不要查 main 分支。
- 上一级 `.ref/`：Flutter 版 `countdown_event` / `event_reminder` / `event_repeat` / `event_sort_mode` / `undo_operation` / `storage_service` 原始 Dart 源码，移植时逐字段对照。
- `app/schemas/.../1.json`：Room 基线，改表必须升版本并写迁移。

**约定**
- 业务代码禁止 import `io.github.neoruaa.glasmio.*`，一律走 `ui/glass/`。
- 排序语义：`distance`（距今天数）/ `targetDate`（目标日期）/ `createdAt`，置顶恒在最前，已完成折叠。
- 提交信息用 `feat/fix/chore(scope): 中文描述`；每个里程碑结束产出可安装 APK。
- 构建：本地 `gradle :app:assembleDebug`（wrapper 可能拉不到 `services.gradle.org`，用本机 Gradle 9.6.0）。

## 已知坑 / 待确认项

1. **[阻塞级] 远程与本地分叉**：已 `git fetch` 核对过——本地 `3e4333e` 与远程 `68866a6` 的差异**只有 README 的空白差异**（`git diff --ignore-all-space` 为空），内容一致；本地 `branch.main` 仍无 upstream。建议处置：加 `.gitattributes`（`* text=auto eol=lf`）→ `git reset --mixed origin/main`（保留工作区）→ 一次提交 M2a+M2b+docs → `git push -u origin main`。**执行前需你确认**，因为会丢弃本地那个 M1 提交对象（内容已在远程）。
2. **Glasmio 的 main 分支 ≠ 0.1.2**：`GlasmioBackdropHost` 在 main 上多一个 `(sampledColor, modifier, overlayContent)` 重载，v0.1.2 没有。照 main 写会编译失败。
3. **`lint.abortOnError = false`**：M0 阶段有意放宽，M7 前必须改回 `true`，否则发布包可能带 lint 错误。
4. **依赖缺口**：`navigation-compose`、`work-runtime-ktx` 未引入（M2a 刻意绕开了 `material-icons-extended`，图标用 Canvas/文字自绘）；M2b/M2c 先补 `navigation-compose`，M4 先补 `work-runtime-ktx`。
5. **`robolectric` 已在 version catalog 声明但从未启用**：当前单测是纯 JVM（迁移测试直接测解析器，不碰 Context）。需要跑 DAO/Context 测试时再加 `testImplementation(libs.robolectric)` 与 `@RunWith`。
6. **迁移只在首启跑一次**：靠 `migration.legacy_v1_done` 幂等；调试迁移逻辑必须清应用数据，否则看不到任何动作。
7. **撤销窗口跨进程重启的策略未定**：待确认是「重启即失效」还是「按 `expiresAt` 恢复」；`UndoOperation` 带 `expiresAt`，倾向后者，但需在 M2c 明确并写入 TODO 验收。
8. **低端机玻璃性能未实测**：Glasmio 有 FPS Counter，M7 前需在低端机验证，并确认「关闭玻璃特效」降级开关是否要做（会影响 M6 设置项设计）。
9. **分类默认值「生活」、未知枚举回落默认值是刻意行为**：为与 Dart 侧一致，改动会导致旧数据语义漂移，勿"优化"。
10. **Flutter 原始源码不在本机**：只有上一级 `.ref/` 的 6 个 Dart 文件（模型 + storage service）可对照，其余页面行为需以计划文档的文件清单为线索。逐模块裁剪决策见 `docs/TODO.md` 的「移植范围」章节。
11. **本机无设备也无模拟器**（`adb devices` 为空，`.toolchain/sdk` 未装 emulator）：所有视觉/交互验收只能等真机，构建链只保证编译、单测、lint 通过。
12. **M2a/M2b 尚未提交**：工作区有未跟踪/未提交的新文件，首次提交前先做 `T0`（远程对齐），否则容易出现非快进拒绝。
13. **滚轮吸附未实机校准**：`WheelColumn` 依赖 `itemHeight`(34dp) 与实际行高一致才能居中吸附，`rememberSnapFlingBehavior` 在停止滚动后回写下标；真机上若出现偏移一行的现象，调 `itemHeight` 或改用 `firstVisibleItemIndex + 偏移判断`。

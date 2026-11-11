# HANDOFF — 萤 · Kotlin 重构

## 任务背景

把「萤」倒数日应用从 Flutter 重写为 Kotlin/Compose + Glasmio Liquid Glass（Android 13+），包名与 Flutter 版保持一致以便覆盖升级。

## 已完成清单

1. **M0 工程基建**：AGP 9.4.1 / Kotlin 2.4.21 / Gradle 9.6.0 / compileSdk 37 / minSdk 33 / JDK 21；Glasmio 0.1.2 双模块依赖；`ui/glass/` 封装层；无 NDK/CMake。
2. **CI**：`.github/workflows/android-ci.yml`，`assembleDebug + testDebugUnitTest + lintDebug` 出 debug APK 工件。
3. **M1 数据层**：Room `events`（schema 已导出）+ DataStore `ying_settings` + 幂等旧数据迁移 + 22 项单测全绿。
4. **应用容器**：`YingApp` 手写依赖装配（DB / EventRepository / SettingsRepository），启动即跑迁移（失败不阻断）。
5. **M2a 首页列表**：`EventViewModel` + `EventPresentation`（纯函数排序/进度）+ `HomeScreen` + `EventCardView`；玻璃封装层增文字色与圆形面板。
6. **M2b 新建表单**：`EventFormState` + `WheelDateTimePicker`（五列滚轮）+ `EventEditSheet`（同页浮层）+ FAB 落库；41 项单测全绿。**未真机校准。**
7. **T0 远程对齐**：根因是 `core.autocrlf=true` 导致 README 整文件换行符差异；加 `.gitattributes`（`eol=lf`）→ `reset --mixed origin/main` → 两次提交（`c02adc9` 行尾 / `548edad` M2a+M2b+docs）→ `push -u origin main`。本地与远程齐平于 `548edad`，CI 全绿。
8. **M2b2 分类管理**：`EventCategory`（预置 7 类、12 字上限）+ `CategoryRules`（纯函数规则）+ `CategoryCodec`（DataStore `category_list_v1` JSON）+ `CategoryRepository`（存储列表 ∪ 旧事件孤儿分类，删除需迁移目标）+ `CategoryViewModel` + `CategorySheet` 浮层；Room 增分类统计/级联更新三条查询；`EventEditSheet` 改读仓库并加「管理分类…」；抽出 `GlassControls`。62 项单测全绿。**未真机验收。**
9. **文档**：`README.md`（决策表 + 包结构 + Glasmio 版本差异）、`docs/TODO.md`、`docs/CHANGELOG.md`、本文件。
10. **M2c 详情页 + 6 秒撤销**：`UndoOperation`/`UndoCodec`/`UndoRepository`（立即落库 + 快照回填 + `expiresAt` 持久化 + 启动按剩余时间恢复）+ `EventDetailScreen` + `UndoBar` + `EventActions`；首页卡片可点开详情，标题右侧「⋯」菜单含清空已完成/清空全部（后者二次确认）。72 项单测全绿。**未真机验收。**

## 未完成清单（按顺序，可直接执行）

1. `M2a/M2b/M2b2/M2c` 真机验收：安装 `app/build/outputs/apk/debug/app-debug.apk`，看玻璃/秒跳/排序/空态、表单滚轮吸附与落库、分类增删改与删除迁移弹层、详情页、6 秒撤销（含杀进程重启后窗口内仍可撤销）。
2. `M2d` 日历三视图（月/年/按月分组列表）+ 农历星期副标题，与首页共享 ViewModel。
3. `M4` 提醒通知（AlarmManager + 通知动作 + 权限引导 + 重启重建 + 提醒诊断页）。**先补 `work-runtime-ktx`。**
4. `M5` 桌面小部件（总览 + 单事件；配置台保留预设/字号/配色/显隐）。
5. `M6` 设置中心（glasmio-preference + 备份导入导出 + 关于页）。
6. `M7` 应用内更新全链路（测速选路 + 断点续传 + 静默安装）+ 全量回归 + 签名发版 + 收紧 lint。

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
| — | 分类以**名字**为唯一标识，重命名级联 `UPDATE events SET category` | 避免为一个展示性字段升 Room schema 并回填旧数据；用户可见效果与 id 引用一致 |
| — | 分类列表 = 存储列表 ∪ 事件里出现过的「孤儿分类」 | 旧数据里的自定义分类不能凭空消失，否则用户会觉得"分类丢了" |
| — | 删除被占用分类必须先选迁移目标，禁止静默删事件 | 数据安全优先；候选为空时提示先新建一个分类 |
| — | 分类浮层同样做同页浮层，叠在表单之上 | 与表单同一个 backdrop 约束；独立窗口会让玻璃材质失效 |
| — | 撤销＝**立即落库 + 快照回填**，不做延迟删除 | 延迟删除在进程被杀后无法重建意图；`expiresAt` 可持久化，快照回填还能让删除与状态切换共用一条回滚路径 |
| — | 撤销只保留**最近一次**，新操作顶替旧的 | 旧操作效果已落库；多重撤销需要栈式回滚，复杂度远高于收益 |
| — | `UndoBar` 最后绘制（盖在详情/表单浮层之上） | 浮层开着时 6 秒窗口照样在走，被遮住等于白给 |
| — | 保存表单**不进**撤销队列 | 保存是用户主动确认的动作，且可用「编辑」改回；撤销只服务删除/批量清理这类难复原操作 |

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
- **构建（唯一可行方式，2026-11-11 实测）**：`./gradlew` 会尝试重下 Gradle 并因 PKIX 证书校验失败（`~/.gradle/wrapper/dists/gradle-9.6.0-bin` 未被识别为完整分发）；shell 默认 `JAVA_HOME` 未设置且 `java` 是 1.8。必须显式指定工具链：
  ```bash
  export JAVA_HOME="/c/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/.toolchain/jdk/jdk-21.0.13+9"
  /c/Users/yunbo.wang/WorkBuddy/2026-11-09-15-57-01/.toolchain/gradle-9.6.0/bin/gradle \
      :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
  ```
  单测结果看 `app/build/test-results/testDebugUnitTest/*.xml`（`tests`/`failures`/`errors` 属性）。
- 提交信息用 `feat/fix/chore(scope): 中文描述`；每个里程碑结束产出可安装 APK，并同步更新 `docs/` 三件套。

## 已知坑 / 待确认项

1. ~~**[阻塞级] 远程与本地分叉**~~ **已解决（T0）**：根因不是内容差异，而是 Windows `core.autocrlf=true` 让本地提交把 README 存成了 CRLF。`.gitattributes` 落地后差异自动消失，本地与远程同点于 `548edad`。**后续新增文件若再出现"整文件被改"，先查行尾，不要逐行比对。**
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
12. ~~**M2a/M2b 尚未提交**~~ **已解决（T0）**：`548edad` 已推送，upstream 已建立。后续每个里程碑结束各自提交，避免再次堆积。
13. **滚轮吸附未实机校准**：`WheelColumn` 依赖 `itemHeight`(34dp) 与实际行高一致才能居中吸附，`rememberSnapFlingBehavior` 在停止滚动后回写下标；真机上若出现偏移一行的现象，调 `itemHeight` 或改用 `firstVisibleItemIndex + 偏移判断`。
14. **分类改名不是跨库事务**：Room 与 DataStore 无法同事务，写入顺序固定「先改事件、再改列表」。若第二步失败，结果是事件已改名而列表仍是旧名，旧名会以孤儿分类形式重新出现——数据不丢、可再操作。反之先写列表会出现「列表有、事件挂着已消失的旧名」的隐性不一致，故不采用。**不要**为此改回 id 引用：那要升 Room 版本 + 回填旧数据，收益不足。
15. **表单打开期间改名/删除其所属分类**：表单持有的是旧名字符串，保存后事件会挂回旧名、重新变成孤儿分类（管理页显示「旧数据中的分类」）。不阻断、不丢数据，属刻意取舍；若后续觉得刺眼，需把表单的 category 改成跟随仓库解析。
16. **孤儿分类会被任意一次分类编辑「收编」**：`add/rename/delete` 都以「存储列表 ∪ 孤儿」的合并结果为基准并整体落盘，所以旧分类会被写进 `category_list_v1`。这是预期行为——否则重名校验会失效。
17. **分类浮层沿用 `WhileSubscribed(5s)` 状态流**：冷启动瞬间 `categories` 为空列表，此时立刻点 + 打开表单，分类 chips 只会显示当前值；数据流毫秒级就会补齐。属已知瞬态，不影响落库。
18. **`UndoBar` 的 z-order 是硬约束**：它必须留在 `HomeScreen` 最外层最后绘制。详情页占 0.88 高、表单占 0.94 高，一旦把撤销条放回它们之前，撤销窗口会在浮层下白白流逝。调整浮层顺序时先看这条。
19. **撤销的跨进程恢复依赖启动时序**：`YingApp.onCreate` 在迁移之后调用 `undoRepository.restore()`。若把它挪到 `MainActivity` 里，首帧会先闪一次"没有撤销条"，随后才出现；视觉上是可感知的抖动。
20. **带偏移量的时间戳按绝对时刻换算**：`UndoCodec` 对 `Z` / `+08:00` 用 `atZoneSameInstant` 落到本地时区。这与 `LegacyEventParser`（`OffsetDateTime.toLocalDateTime()` 丢偏移）**不一致**，但后者是旧数据兼容的既有实现，改动会改变旧事件日期解释，**不要顺手统一**。
21. **撤销不是"编辑回滚"**：`save()` 有意不入队；若日后要给编辑加撤销，需要把 `UndoKind` 扩到编辑语义，并注意"撤销编辑"与"撤销删除"同时存在时的顶替关系。

# TODO — 朝夕 · 当前待办

> 更新：2026-11-19。续接先读 [HANDOFF](HANDOFF.md)（「#27」本批实现说明与「#26」上一批实况），再读本文件；历史实现见 CHANGELOG。
> **当前状态：4.3.1 / 75 已正式发布（Release `450807520`，资产 `459e88a8…c53e`）**。补丁版，修三件事：日历列表视图闪退真因（重复事件撞 LazyColumn key）、年视图改每行两个月、首页顶栏按钮与日历/设置页对齐。
> 上一版 4.3.0 / 74（tag `v4.3.0` / Release `450435913` / `376dba9c…7455b`）已正式发布。

## 4.3.1 / 75：列表视图闪退 + 年视图两列 + 顶栏对齐（已发布）

- [x] **日历列表视图闪退（真因）**：`MonthListView` 的 LazyColumn key 由 `"e-<月>-<事件id>"` 改为 `"e-<月>-<日期>-<事件id>"`。每天 / 每周事件在同一月有多条同 id 条目，跨多天持续事件同理——key 撞了 Compose 抛 `IllegalArgumentException` 直接杀进程。`MonthView` 选中日那一栏的 key 也带上日期兜底。
- [x] **年视图每行两个月**：`YearView` → `GridCells.Fixed(2)`；`MonthCard` 内边距 10→8dp、格子 18→16dp、字号下压一档。
- [x] **顶栏按钮对齐**：`HomeHeader` 改顶对齐，右侧「排序胶囊 + ⋯」按 `PageChrome.ChipHeight`(34dp) 兜住居中，与日历 / 设置页按钮同基线。
- [x] **回归**：`CalendarRepeatRenderTest` 6 项（四视图 × 重复事件渲染）、`RepeatTimedEventRenderTest` 10 项（每天/每周 × 全天/有时刻 × 时长/持续 × 表单往返 × 列表/详情/新建面板）；先回退 key 复现 3 条失败再恢复确认全绿。
- [ ] **真机验收**：列表视图不再闪退（每天/每周、持续事件）、年视图两列观感、顶栏齐平、74→75 原位保数据升级。

## 4.3.0 / 74：修复打磨 + 多天事件结束时刻 + 图标重绘（已发布）

- [x] **修「添加有时长的事件后点列表闪退」**：`durationMinutes` 全链路（领域模型→Room→备份/撤销→详情渲染）核对安全，`durationLabelOf` 无除零、详情页有 null 兜底、时间块 `blockMinutes` 有 `coerceAtMost`；新增 `EventDetailScreenTest` 组合回归钉住「有时长事件打开详情不崩」。**真机日志未复现，待真机确认**。
- [x] **归档按钮统一**：详情页「归档/恢复」从 34dp 文字按钮改成 56dp 圆钮 `DetailArchiveIcon`（抽屉 + 上/下箭头），与置顶/删除同尺寸同材质。
- [x] **日历年视图缩小**：`MonthCard` 内边距/格子高度（24→18dp）/字号整体下压，突出「预览」。
- [x] **顶栏按钮高度统一**：`GlassSegmentedControl` 默认高度 32→34dp，与首页排序胶囊、日历「今天」、各页圆钮对齐。
- [x] **多天事件滚轮对齐**：结束日滚轮 `isAllDay=form.isAllDay`、`time=form.endTime?:form.time`，上面能选时分下面也能选、上面全天下面全天；新增 `CountdownEvent.endTime`，Room schema **4→5**、备份/撤销同步，只对「多天+非全天」有意义。
- [x] **图标重绘**：`ic_launcher_foreground/background/monochrome` 去渐变改纯色几何（日轮+八道光芒+地平线+倒影），线条分明。
- [x] **验证**：**443 项单测 / 54 份 XML / 0 failures·errors·skipped**；lint release **0 errors / 15 warnings**；`assembleRelease` 成功；`verifyRuntimeNoticeCompleteness` 退出 0、`runtime.txt` 116,447 B 未变。APK 核验：5,313,569 B / `376dba9c…7455b`、aapt2 74/4.3.0、证书 `8894605E…8D02` 同 68、8 份许可资产与源一致、图像条目 0、Apple 素材 0 匹配。
- [ ] **发布（进行中）**：`versionCode 73→74`、`versionName 4.2.0→4.3.0`，原签名 release + 全量回归 + APK 核验 + 上传，按 4.1.x/4.2.0 同一套流程。
- [ ] **真机验收**：有时长事件点列表不再闪退；归档圆钮与三钮观感一致；年视图板块预览观感；顶栏各按钮高度齐平；多天事件结束日滚轮与开始日一致；新图标在启动器/圆形/方圆遮罩下的观感；以及 **73→新版原位保数据升级**（含 schema 4→5 迁移）。

## 4.2.0 / 73：重复与时间跨度 + 时间块 / 标记 / 计算器 / 归档（2026-11-19 已发布）

- [x] **修「节日皮肤没有效果」**：根因是当天不是农历节日、柔光又只有 0.22/0.16 且只有顶上一团。浓度提到 0.34/0.26 + 底部第二团；设置页新增「预览」当场换背景（`cycleFestivalPreview` / `clearFestivalPreview`，不落盘，离开设置页结束）；说明行按「预览中 / 已关闭 / 今天是节日 / 下一个节日+日期」四种状态。
- [x] **完整重复规则**：NONE/DAILY/WEEKLY/MONTHLY/YEARLY + `repeatInterval`(1..99) + `repeatWeekdays`(ISO 1..7 多选) + `repeatUntil`（含当天，无次数上限）。`RepeatRule` 四档惰性序列重写，月档夹月末且不漂移；新增 `EventPrimaryAction.NEXT_OCCURRENCE`。
- [x] **农历生日 / 农历年循环**：`LunarCalendar.toSolar()`；年循环「按农历」逐年回算公历，月日以锚点换算为准（`repeatMonth/Day` 仅兜底），闰月按平月过、三十遇小月取廿九。
- [x] **多点提醒**：保留 5 条上限与预设，新增自定义分钟输入 + 「已设」摘要 + 重复/超限提示。
- [x] **持续事件**：`endDate`；`spanDays/isMultiDay/covers()`；月视图格子与选中日走 `coverageBetween`（年/列表仍按起点）；首页徽章改「还剩 N 天」、目标取本次起点；详情页加「持续」行；提醒仍按第一天。
- [x] **文案统一**：`RepeatText.short/of/span`，首页 / 日历 / 详情 / 小部件共用，删掉四处各自的 `when (repeatType)`。
- [x] **数据兼容**：Room schema 2→3→4（3 加重复/星期/农历/结束日/持续区间 5 列，4 加 `tags` / `archived_at` / `duration_minutes` 3 列，全带默认值）；备份 format 2→3→4（新字段可选、旧备份可读、旧 App 拒绝新备份）；`UndoCodec` 同步新字段。
- [x] **时间块视图（日历第四视图）**：`TimelineLayout.kt`（纯函数：全天条 + 分钟定位 + 重叠分列）+ `TimelineView.kt`（渲染 + 当前时刻线 + 滚到最早事件前半小时）；分段控件扩四档；`CalendarActions.shiftDay` 翻日。
- [x] **多维标记**：`EventTags.kt` 归一化（去 #、逗号换空格、去重、上限 5/个最长 8）；编辑面板输入 + 已有标签点选；卡片/详情展示；搜索命中标签。
- [x] **日期计算器**：`DateMath.kt`（纯函数）+ `DateCalculatorSheet.kt`，入口在首页菜单；有符号差 / 含首尾 / 周天 / 年月日 / 加减 / 农历 / 周岁；不做工作日（需维护调休表）。
- [x] **归档（第三条状态线）**：`archivedAt`；与「已完成」分开；首页/日历/提醒/小部件排除归档；`ArchiveSheet`（恢复/删除/清空）；`UndoKind` 加 `TOGGLE_ARCHIVED` / `CLEAR_ARCHIVED`。
- [x] **验证**：**436 项单测 / 53 份 XML / 0 failures·errors·skipped**（394 + 上一批 18 + 本批 `DateMathTest` / `TimelineLayoutTest` / `EventTagsTest` 24）；lint release **0 errors / 15 warnings**（本批 `TimelineView` 一条 offset 告警已改 lambda 收掉）；`assembleDebug` / `assembleRelease` 成功。`BackupRepositoryTest` 已覆盖 1→3→4 迁移。
- [ ] **发布（进行中）**：`versionCode 72→73`、`versionName 4.1.3→4.2.0`，原签名 release + 全量回归 + APK 核验 + 上传，按 4.1.x 同一套流程。
- [ ] **真机验收**：表单滚轮在长表单里的滚动与手感；时间块视图重叠分列观感与当前时刻线；进行中卡片「还剩 N 天」观感；农历生日跨年展示；多选星期在窄屏横向滚动；归档箱交互；以及 **72→新版原位升级保数据**。

## 4.1.3 / 72：M6 剩余项与界面修正（2026-11-18 已发布）

- [x] **修设置页透出日历**：根因是 `if (!calendarActive && !showSettings) 首页 else 日历`——设置页打开时条件为假掉进 else，把日历画在设置页底下。改成 `when { showSettings -> 不画; calendarActive -> 日历; else -> 首页 }`。
- [x] **按钮尺寸与位置统一**：新增 `ui/component/PageChrome.kt`（圆钮 34dp / 字形 17sp / 头部 16·12·4 / 胶囊 34dp / 主操作 56dp）。首页「⋯」与设置返回 32→34，三页头部内边距统一，日历列表 14→16dp，详情页 52/56 统一 56dp，裁剪页 48→56dp，日历「今天」28→34dp。
- [x] **字体设置**：导入 TTF/OTF/TTC（SAF、32 MB 上限、sfnt 魔数校验）、就地预览、删除、恢复默认；4 款 OFL 字体的下载入口不内置。`FontFamily.Resolver` 是 sealed 接口实现不了，改用同签名 `Text` 包装 + 24 个文件替换 import。
- [x] **节日皮肤**：八个农历节日加背景柔光与标题祝语，中元不给皮肤；设置页可关。
- [x] **版本 4.1.3 / 72 与全量验证**：`testDebugUnitTest --rerun + lintRelease + assembleRelease` → `BUILD SUCCESSFUL in 8m 7s`（90 任务 / 47 执行）；**394 项 / 49 XML / 0 failures·errors·skipped**；lint **0 errors / 15 warnings**（同基线）；`verifyRuntimeNoticeCompleteness` 退出 0；`runtime.txt` 116,447 B / `d1508af0…1449` 与上一版相同（字体不随包）。
- [x] **APK 核验**：**5,231,781 B / `10258ec201690dcff580a405ff7d2e476c0a664d048e0f90734f6d9efcc9f39c`**，146 条目；`aapt2` **72 / 4.1.3** / minSdk 33 / targetSdk 37；证书 `8894605E…8D02` 同 68/70/71；8 份许可资产（6 源文件 MATCH）；图像条目 0、无嵌套归档、资源表 1,341 行 Apple 关键词 0 匹配；副本 `../morrow-72-4.1.3-release.apk` 逐字节一致。
- [x] **发布（2026-11-18，#24）**：`e47ed3e`（代码 + 版本）、`f5553f7`（文档）推送 main；draft `449719768` → 上传 asset `762634465` → 回下载 `cmp` 逐字节一致 → 转正式 `08:48:37Z`；tag `v4.1.3` = `f5553f7`；`/releases/latest` = `v4.1.3` 且 `update/manifest.json` 已按真实字段回填（3,307 B）；全程走 `127.0.0.1:10809`，未出现 59466 的 502。
- [ ] **真机验收（未验）**：字体切换后的行高与截断、大字体下的列表、节日柔光在浅色主题的浓淡、设置页透底是否彻底、各页头部观感是否一致，以及 **68→72 原位升级保数据**。

## 4.1.2 / 71：结构重构 + protobuf 许可归属（2026-11-18 已发布）

- [x] **五个超大 UI 文件拆分（无行为变更）**：只改可见性与文件级注释，逐文件用「行多重集比对」确认 0 丢行。
  - `SettingsScreen` 1,345 → `SettingsScreen`(461) + `SettingsRows`(363) + `SettingsGlyphs`(306) + `BackdropCropScreen`(128) + `LicensesSheet`(205)
  - `GlassBottomBar` 989 → `GlassBottomBar`(674) + `GlassBottomBarParts`(366)；`GlassTheme` 690 → `GlassTheme`(475) + `GlassBackdrop`(236)
  - `HomeScreen` 841 → `HomeScreen`(549) + `HomeSections`(328)；`CalendarScreen` 806 → `CalendarScreen`(357) + `CalendarViews`(477)
  - 工具：仓库外 `../tools/kt_split.py`（按声明移动 + 自动裁剪 import；`by` 委托的 `getValue/setValue` 必须按写法保留）。
- [x] **版本 4.1.2 / 71 与全量验证**：`testDebugUnitTest --rerun + lintDebug + assembleDebug + assembleRelease + verifyRuntimeNoticeCompleteness` → `BUILD SUCCESSFUL in 6m15s`（114 任务，63 executed）；**380 项 / 47 XML / 0 failures·errors·skipped**；lint **0 errors / 15 warnings**（无新增）。
- [x] **APK 核验**：**5,199,013 B / `601b3beb…73a6`**，146 条目；`aapt2` **71 / 4.1.2** / minSdk 33 / targetSdk 37；证书 `8894605E…8D02` 同正式 68；**8 份**许可资产（6 源文件 MATCH）；图像条目 0、无嵌套归档、资源表 Apple 关键词 0 匹配；本地副本 `../morrow-71-4.1.2-release.apk` 逐字节一致。
- [x] **发布（2026-11-18，#22）**：三个提交 `5196f10` / `ba6d023` / `f9da8d8` 推送 main；draft → 上传 → 回下载 `cmp` 逐字节一致 → 转正式；tag `v4.1.2` = `f9da8d8`、Release `449651404`（`07:32:07Z`）、`/releases/latest` 与 `update/manifest.json` 均已回读一致；`api.github.com`/`uploads.github.com` 全程走 `127.0.0.1:10809`，未再出现 59466 的 502。
- [ ] **真机验收（沿用下方清单）**：视觉/帧率/小部件/许可长文滚动，以及 **68→71 原位升级保数据**。

## 当前：4.1.1 视觉与开源发布（已批准）

- [x] **#16 控件与背景**：ChoiceChip 容器/行盒垂直居中；首页/日历互斥活动内容采样槽，日历复用全窗口背景。4 项 Compose Native 布局 + 5 项手势聚焦回归通过；真机视觉未验。
- [x] **#17 日历动画**：完整不可变快照、共享标题/日期过渡、模式淡入淡出/周期定向滑动、固定 viewport、稳定数字和边界交互；7 项动画/真实大字体日历及 7 项手势/周期身份测试通过。阶段全量 351 项（42 XML，0 failures/errors/skipped）、lint 0 errors / 15 warnings、debug 构建成功；真机帧率未验。
- [x] **#18 小部件**：应用丝缎/私有图片同源、后台预乘 alpha 软件模糊；外背景连续圆角、原生轻量圆角行卡片；实际 SizeF 变体均分 262,144 总像素、384 工作图长边、8 MiB 结构化 LRU；后台测量尺寸预览、列表外观快照、保存成功后刷新。新增 15 项回归，最新全量 366 项（44 XML，0 failures/errors/skipped）、lint 0 errors / 15 warnings、debug 构建成功。启动器视觉/帧率仍未验；不实时采样桌面或滚动行。
- [x] **#19 README/许可（工程项已完成，2026-11-18）**：首页准确说明 ying 独立 Kotlin 重构和 Glasmio UI；自身 MIT 已选定，Glasmio 实际 Apache-2.0；许可正文随包、设置页可离线阅读；**仅剩人工逐组件源码层版权/NOTICE 审阅**。详见 HANDOFF「#19 运行时许可与 README」章节。
  - [x] 新增根 `LICENSE`（朝夕 MIT）、`app/src/main/assets/licenses/ying.txt`（ying 完整 MIT，Copyright (c) 2026 Gao Shuai）。
  - [x] **初版实现（历史）**：`generateRuntimeNotices` 按实际releaseRuntimeClasspath收集110个artifact；修复AGP延迟provider/assets.directories.add。初版输出16,271行/928,540 B不是当前文件；当前生成物 111,134 B（含三份静态全文），已补源字节/正文缺口校验。
  - [x] 核实上游：ying MIT；Glasmio 0.1.3 为 **Apache-2.0 非 MIT**、以 v0.1.3 标签为准；MaterialKolor 实际标签 **`4.1.1` 非 `v4.1.1`**、MIT `Copyright (c) 2025 Jordon de Hoog`、material-color-utilities 模块 Apache-2.0。
  - [x] **当前审计包 Apple 素材不分发证据**：前一步强制 R8/资源裁剪构建取得原始证据；正文补齐与许可 UI 完成后再次核验最新 69 release 的 **145 个文件条目**，目标文件名/10项AAR图像原始SHA/PNG-WebP魔数/资源表均0匹配，无嵌套归档。最新 APK **5,196,693 B**，SHA-256 `320E5E90984762BEF2177C3A6AE7814BD268FF7B8EF15EA0B6553520024D1DF9`。七份许可资产与源字节一致；不是 Apple 图像授权或正式70包放行，完整新旧证据见HANDOFF。
  - [ ] **正式发行前硬门槛**：#20 生成最终 4.1.1 / 70 待上传 APK 后，重复文件内容/资源表/图像摘要及格式核验并记录最终 SHA-256；任一目标图像进入包即不得发行，不以本次 69 审计包替代，不以 Apache 声明覆盖 Apple 原图。
  - [x] 修 `generateRuntimeNotices`：精确坐标/版本允许集；protobuf **BSD-3-Clause** 与原文保留；只收 AAR classes.jar/平铺 libs/*.jar，排除 lint.jar/CopyrightKt.class；严格 UTF-8/控制字符与文本名；原字节去重保留全部来源。新增固定 v0.1.3 Glasmio NOTICES 源文件、根 MIT→morrow.txt。**26 项内存回归 + 5 项实际 assets 回归**通过（当轮为 24+4，补正文后扩为 26+5）；110 artifact/109坐标、81份归档原文→**8组/118来源**，runtime.txt **111,134 B**，强制复跑摘要相同。七份许可资产在最新69审计APK中与源字节一致；详见 HANDOFF。
  - [x] **补齐正文与来源归属（2026-11-18 完成）**：新增三份静态全文——`apache-2.0.txt`（10,775 B，正文取自 androidx.core:core:1.18.0 AAR 的 `META-INF/androidx/core/core/LICENSE.txt`，该全文 SHA-256 `809fa1ed…9c0` 未改动）、`materialkolor.txt`（1,631 B，MIT `Copyright (c) 2025 Jordon de Hoog`，与上游 `4.1.1` Git blob `614400db…b984` 一致）、`material-color-utilities.txt`（11,195 B，Apache-2.0 + `Copyright 2021 Google LLC`，与上游 Git blob `3879bcb2…c555` 一致）。均带 `Source:`/`License:` 头；MaterialKolor 混合声明必须两份齐全，不接受只补一份。`expectedTextGaps` 收敛为**空集**（新增缺口会直接失败），清单为每个缺文本坐标打印静态正文文件名与 SHA-256 及 §4(a)/§4(d) 或 MIT 归属说明。**`:app:verifyRuntimeNoticeCompleteness` 已通过（退出 0、缺口 0）**；生成物 111,134 B / 8 个原字节组 / 118 个来源，强制复跑摘要一致。`apache-2.0.txt` 只是协议副本，不代替各组件源码层版权审阅。
  - [x] **README 与设置页可读许可（2026-11-18 完成）**：README 重写，清除 WorkManager / 22 测试 / 仅年重复 / 旧 GlassPress / 旧里程碑 / 仓库外计划必读，补当前功能、构建与完整性门槛、Glasmio 封装约定、许可边界，保留包名区别段。设置页新增 `ui/settings/LicenseCatalog.kt`（7 份文档，指向已随包 assets），`LicensesSheet` 改为目录→全文：不使用 `fitContent`，靠 `GlassSheet` 的 `weight(1f)` 有界槽 + `LazyColumn` 分块（`licenseBlocks` 2,000 字符/块）+ IO 读 assets，返回键先回目录；静态 `LicenseEntries` 删除，**Glasmio 由误写 MIT 改为 Apache-2.0**。新增 `LicenseCatalogTest`（5 项）与 `LicensesSheetTest`（3 项 Compose 真实组合），连同 `RuntimeNoticesAssetTest` 共 **13 项聚焦测试通过**。新 69 审计 APK 5,196,693 B / SHA-256 `320E5E90984762BEF2177C3A6AE7814BD268FF7B8EF15EA0B6553520024D1DF9`，七份资产与源一致、十项 Apple 图像仍 0 匹配。真机长文本滚动/大字体/浅色主题未验。
  - [x] **许可改动后的全量回归（2026-11-18 完成）**：`:app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebug :app:assembleRelease`，`BUILD SUCCESSFUL in 3m56s`（113 任务，17 executed）。先清空旧 XML 再跑，**379 项 / 47 份 XML / 0 failures·errors·skipped**（366 基线 + 13 新用例对账一致）；`lintDebug` **0 errors / 15 warnings**，同为版本提示/UseKtx/DataExtractionRules/UseTomlInstead，**无新增警告**；debug 80,986,212 B（`BEF60F9A…6B96`）、release 5,196,693 B（`320E5E90…4D1DF9`，与上一审计包逐字节相同）均成功，`aapt2` 元数据 69 / 4.1.0 / minSdk33 / targetSdk37，`apksigner` 证书 SHA-256 `8894605E…8D02` **与正式 68 一致**；`verifyRuntimeNoticeCompleteness` 再跑退出 0，7 份资产与源一致。未改源码/版本/依赖/CI/签名，MANUAL-TEST 未动。
  - [x] **4.1.1 / 70 版本改动与构建（2026-11-18 完成）**：仅改 `app/build.gradle.kts` 的 `versionCode 69→70`、`versionName 4.1.0→4.1.1`（公开版本展示不附 code），未动依赖/schema/CI/签名/包名。`testDebugUnitTest --rerun :lintDebug :assembleDebug :assembleRelease :verifyRuntimeNoticeCompleteness` → **`BUILD SUCCESSFUL in 8m31s`（114 任务，53 executed）**；**379 项 / 47 XML / 0 failures·errors·skipped**；lint **0 errors / 15 warnings**；`aapt2`：`com.morrowviento.morrow` **70 / 4.1.1**、minSdk 33 / targetSdk 37；`apksigner` 证书 SHA-256 `8894605E…8D02` **与正式 68 完全一致**。最终 release **5,196,689 B / SHA-256 `7F92400A49F50C1BCA041D2E1125E25AC3160C32C637DA541AEC8770AA20D963`**，145 条目、CRC 通过、7 份许可资产与源 MATCH（`runtime.txt` 111,134 B / `morrow.txt` 1,129 B 为生成物）、无嵌套归档、**图像条目 0 且资源表 `tapback|listening|adaptive_audio` 匹配 0**，dex 含 `LicenseDocument` 可确认非旧包。debug 80,500,165 B（`C048D03A…3284`）。本地交付副本 `../morrow-70-4.1.1-release.apk` 与源包逐字节一致；**未上传、未发行**。
  - [x] **发布记录（2026-11-18）**：两个提交 `505c822` / `fbc1a89` 已推送 main；draft → 上传 → 回下载比对 → 转正式；tag `v4.1.1`、Release `449587411`、`update/manifest.json` 均已回读一致。细节见 HANDOFF「#21 提交、推送与正式发布实录」。
  - [ ] 人工版权/NOTICE 归属审阅：`apache-2.0.txt` 是协议副本，逐组件源码层版权与 NOTICE 未逐一审阅；设置页展示不改变该结论。
- [x] **#20 版本/验证（2026-11-18 完成）**：`:app:verifyRuntimeNoticeCompleteness` 在 4.1.1/70 构建中通过（退出 0、缺口 0）；版本改 4.1.1 / 70，公开版本展示不附 code；全量 379 项单测、lint 0 errors / 15 warnings、debug 与原签名 release（含 `lintVitalRelease`、R8、资源裁剪）成功；`aapt2` 元数据 70 / 4.1.1 / minSdk33 / targetSdk37，`apksigner` 证书与正式 68 一致；145 条目、7 份许可资产与源一致、Apple 素材 0 匹配、无嵌套归档，最终 SHA-256 `7F92400A…D963` 已绑定本地交付包 `morrow-70-4.1.1-release.apk`（5,196,689 B）。真机与升级保数据仍不在本项范围。
- [x] **#21 推送/发行（2026-11-18 完成）**：提交 `505c822`（代码/资源/版本）与 `fbc1a89`（README/HANDOFF/TODO）普通推送 main，回读 `refs/heads/main=fbc1a89`；确认 `v4.1.1` 未被占用（release 与 git ref 均 404）后建草稿 Release id `449587411`，上传 `morrow-70-4.1.1-release.apk`（201，digest 一致）→ **回下载比对逐字节一致** → 转正式（`published_at` 2026-11-18T06:05:24Z，非 draft/prerelease）→ `/releases/latest` 回读 tag/标题/资产/大小/digest 全一致，`git ls-remote --tags v4.1.1=fbc1a89`。`update/manifest.json` 按真实数据回填并独立提交 `4a31259`；该提交推送时 WorkBuddy 自带代理对 `github.com` 返回 502，**改走本机 `127.0.0.1:10809` 代理后推送成功**，`git ls-remote` 回读 `main=4a31259`、远端清单 200 且与本地语义一致。CHANGELOG/RELEASE/SIGNING 已同步并推送。**tag 与标题按用户指定不带 code，资产名仍带 `70`；未改脚本、未覆盖旧 tag/Release、未纳入 MANUAL-TEST。**

- [x] **发布后修复：设置页状态栏割裂（2026-11-18，`956461d`）**：设置页原先在安全区内自绘壁纸，与日历页 4.1.1 修过的同一处病根；改为由全窗口 `GlassBackdropHost` 提供背景，并在设置页打开期间让首页整块让位避免透底。379 项单测 / 47 XML 全通过、lint 0 errors / 15 warnings（无新增）、`assembleDebug` 成功；**真机观感未验**。已推送 `main=956461d`；**源码已领先已发布的 70 包，下次发行须 4.1.2 / 71**。
- [x] **逐组件许可与版权归属审阅（2026-11-18，`docs/LICENSE-AUDIT.md`）**：按 109 个坐标回 `~/.gradle/caches` 重开归档、独立重算正文 SHA-256 并重建「坐标→正文组」映射（0 缺失）。结论：107 Apache-2.0 / 1 BSD-3 / 1 MIT+MCU；8 组正文载体；35 个坐标无自带文本，由静态 `apache-2.0.txt` 补 §4(a)；§4(d) 仅 Glasmio 六模块需转发 NOTICE，已随包。**发现两处问题**：① protobuf 的 BSD-3 正文缺版权行（真实缺口，未修，见下）；② `androidx.core:core:1.18.0` 在清单里重复输出两次，已加 `distinctBy` 修掉，`runtime.txt` 111,134 → **110,947 B**。
- [x] **为 protobuf 追加静态版权归属（4.1.2 / 71 完成）**（`docs/LICENSE-AUDIT.md` §4.1）：上游 `datastore-preferences-external-protobuf:1.3.0` 随包的 BSD-3 正文没有 `Copyright` 行，不满足 BSD-3 第 1 条。做法：新增静态归属文件（上游 BSD-3 全文 + `Copyright 2008 Google Inc. All rights reserved.` + `Source:`/`License:` 头），并让 `generateRuntimeNotices` 支持「坐标已有归档正文时追加静态归属」（当前 `expectedTextGaps` 只补缺口）；随后重跑门槛与全量回归，随 4.1.2 / 71 发行。**结果**：新增 `assets/licenses/protobuf-bsd-3.txt`（2,482 B，SHA-256 `835c9e42…5320`，上游 v3.25.5 全文 + `Copyright 2008 Google Inc.`）；生成器新增 `attributionFiles()`，条件与 `fallbackFiles()` 不同——**坐标命中即无条件追加**，缺文件直接失败；归档模板仍原字节保留，两者互不顶替。`runtime.txt` 110,947 → **116,447 B / `d1508af0…1449`**（9 组 / 119 来源），清单同时打印两份 SHA-256 与归属说明；`LicenseCatalog` 增第 8 条、两个测试类补断言；380 项单测与完整性门槛通过。

上一批结果与真机未验事项保留如下；**366 项旧基线已由 2026-11-18 的全量 379 项取代**（47 份 XML，0 failures/errors/skipped），lint 0 errors / 15 warnings。**4.1.1 / 70 已正式发布**：tag `v4.1.1`、Release「朝夕 4.1.1」（`449587411`）、资产 5,196,689 B / `7F92400A…D963`，`/releases/latest` 与 manifest 均已回读一致。工程动作已全部结束，剩余为**人工逐组件版权审阅**与**真机验收（含 68→70 保数据升级）**。下方“本批”均指历史 4.1.0 功能批次。

## 目标与当前完成度

以 ying 原版功能/数据语义为基础，用 Kotlin + Compose + Glasmio 0.1.3 重写 Android 13+「朝夕」，不并行维护 Flutter 主线。
M0–M6 核心及 M7 更新/发布工程已落地；M2a–M6 有 2026-11-12 用户「基本通过」反馈，仍有未验细项。
**本批事件类型/重复与界面改进六阶段实现、本地验证和测试包已完成；用户已授权提交/推送，功能提交 `4bfc12b` 已同步 GitHub main 并回读核验，文档收尾独立提交。不重做旧里程碑，下一步为用户真机测试。**

- 本批最新全量 **338 项单测通过**（40 份 XML），0 failures/errors/skipped；lint **0 errors / 13 warnings**；debug / 原签名 release 构建成功。
- 上级 `morrow-69-4.1.0-release-feature-update.apk`（5,110,088 B）优先测试；证书 SHA-256 与 68 正式包一致。另有 debug-feature-update 包（80,818,980 B），不能覆盖正式安装。版本/摘要/报告位置集中见 HANDOFF；code-review 是上一批历史包，不含本批全部功能。
- 发布清单仍 `v4.0.0-68`；已核对并推送 GitHub main，但未查询最新 Release、修改清单、上传 APK 或公开发布。adb 无设备，未安装/卸载或测真机性能；MANUAL-TEST 的 301 条 checkbox / 72 条历史勾选本批未改，不是新版通过证明；该文件已有本地修改未纳入本次提交。

## 本批功能（已批准，六阶段实现与本地验证完成）

用户确认：旧事件全部按待办保留原状态；新建默认待办、不重复；图片裁剪固定实际背景窗口比例。
以下勾选只表示实现、本地回归与打包完成，不代表真机视觉/触摸/系统提醒/升级保数据/性能通过或公开发布；真机事项留在下一节。

- [x] **#7 类型/重复/兼容**：纪念日与待办、NONE/MONTHLY/YEARLY、原日期锚点与已处理周期游标、Room 1→2、备份/ying/Undo、提醒/部件一致性；编辑合并最新完成/置顶；旧闹钟缺动作字段兼容修补及接收器处理链路回归。
- [x] **#8 详情/选择**：置顶图标—大主动作—删除图标；按类型显示完成/恢复/下一月/下一年，纪念日不能完成；强化二级选中反馈。
- [x] **#9 首页搜索栏**：不再弹完整搜索界面，结果复用首页卡片；实现键盘/返回处理与普通、搜索列表独立滚动状态，实际交互待真机。
- [x] **#10 年历**：十二个月小日历、今天/事件标记、日期与标题摘要、按宽度/字体缩放适配；统计按落点次数，大字体/窄屏效果待真机。
- [x] **#11 图片裁剪**：准备/预览/平移缩放/重置/确认/取消，确认后才提交图片和最终画面取色，失败保留旧图；几何/像素/设置提交回归通过，手势/配置变化待真机。
- [x] **#12 回归与测试包**：阶段进度回填；最终 338 项全量单测、lint 0 errors / 13 warnings、debug/原签名 release 成功；内部版本/签名及源包与副本摘要已核验，独立 feature-update 包不覆盖历史交付包，路径见 HANDOFF。

当前结果是 338 项全量通过与最终 feature-update APK，不再沿用前次 334 项/11 失败或“最终构建进行中”的状态。搜索/年历阶段 328 项、上一批 313 项与 code-review 仅为历史记录；先前 Native ZIP/Windows 文件占用异常未确证根因，复发继续查生命周期，不削弱测试。用户已追加本批代码/工程文档提交推送授权；MANUAL-TEST 的本地修改排除，仍不发布、改清单/CI/密钥、安装/卸载或代勾真机记录。

## 真机与发布待办（按前提推进）

### 1. 4.1.0 真机验收与反馈修复

- [ ] 本批类型/动作：详情置顶/删除图标与大按钮；待办完成/恢复、纪念日不完成；每月/月末/闰日推进、撤销与旧已完成重复事件恢复。
- [ ] 本批选择/提醒：二级控件选中反馈、旧闹钟、重复通知动作与过期“稍后”；处理器回归不等同系统广播/闹钟端到端验收。
- [ ] 本批搜索/年历：搜索栏输入/IME/返回/详情保持/普通滚动恢复；十二个月摘要、窄屏/大字体与实际滑动表现。
- [ ] 本批裁剪：移动/双指缩放/重置/取消/确认、取最终画面颜色、配置变化与保存失败保旧图；备份不含图片本体，原图另留。
- [ ] **AL1–AL15**：明暗/缓存、相册/持久化、玻璃可读性、取色/灰图、备份不含图片、搜索栏/详情跳转、部件亮度/分隔线；旧搜索浮层样例已被 SearchBar 取代。
- [ ] **A–D、AI5、AJ1、AK2**：启动/数据、撤销、底栏融合、配色联动、滚动；旧编号适用版本以 MANUAL-TEST 顶部为准。
- [ ] 核心回归：同色换图立即生效、关闭取色后换图、编辑/分类修改后旧撤销作废、删除/撤销/导入连击、显式 COUNTDOWN 过期展示及跨日进度。
- [ ] 用户按编号报告设备/版本/步骤/现象后复现、定位、修复并补回归；不凭自动测试或旧勾选认定新版真机通过。

完成条件：新增功能和核心回归有明确真机结果，无未解决阻塞缺陷。

### 2. 同签名升级与应用内更新

- [x] 原发布密钥构建 4.1.0 release，APK 内部元数据为 69 / 4.1.0；apksigner 校验通过且证书 SHA-256 与正式 68 一致。
- [ ] 正式 68 → 新 release 的实际原位升级、事件/设置/提醒/小部件保留；先备份，不要求用户直接卸载正式版。
- [ ] **N1–N9**：检查/无更新/断网、发现高 code、下载/续传、系统确认、升级保数据、选包/校验失败。
- [ ] debug ↔ release 的用户备份/卸载/安装/导入路径单独确认，不能算成同签名升级；背景原图另留。

前提：更新端 code 大于安装端，保数据安装同包名同签名。当前 69 对清单 68 无更新正常；隔离测试源需授权，不为测试发布假摘要/改线上清单。
Robolectric 已验证提交后 READY/系统确认提示/可关闭，不代表真机安装成功。安装仍有系统确认。
定位：`service/update/` / `ui/update/`；安装和发布先按 SIGNING / RELEASE。

### 3. 系统、数据与性能剩余覆盖

- [ ] **G/H**：锁屏/Doze/重启、时间/时区/重复事件、远期排期、通知完成/稍后、跨零点。广播路径尚无端到端自动测试。
- [ ] **J**：云盘 SAF、合并不改外观、覆盖导出无尾部、坏备份不清数据、导入残留提醒取消。Room/DataStore 分开提交，不承诺跨存储原子性。
- [ ] **L/M**：极端日期/连点、大字体/返回/分屏/权限、省电；Android 13/14/15/16 与低端机玻璃性能。窗口宽度、系统 dataExtractionRules 的现有 lint 警告结合实际行为核对，不盲目改策略。
- [ ] 真机阻塞网络读取消时延；磁盘 close 故障和 REPLACE 空分类分支的直接故障/专用断言覆盖仍缺失。已有 loopback HTTP/仓库回归不能扩称覆盖所有网络/取消时点。
- [ ] 旧 Flutter 迁移幂等仅在可读取旧数据的沙盒验证；不同包私有目录不可自动互读，真实迁移用旧应用导出后导入。

完成条件：适用编号有设备/版本/结果。性能优化目前有代码与自动回归证据，无真机 FPS/耗电/启动测量结论；不代勾用户记录。

### 4. 正式发布（当前已授权，但受 #19 阻塞约束）

执行顶部 #20–#21：4.1.1 / 70，签名/许可/全量验证后发布并披露真机未验项；发布核验成功后才同步真实清单。69 测试包及 code-review 保留为历史副本，不公开上传 debug 或冒用旧版本。
**前置条件：先解决 #19 的 Glasmio Apple 素材再分发阻塞并证明最终 release APK 不分发，否则不得发行。**

用户已授权本批代码/工程文档提交推送、新版发行及成功后真实清单同步。**未获 MANUAL-TEST 提交、安装/卸载、换密钥或改 CI 授权**；用户手工验收记录继续保留本地。

### 5. 可选工程项

- [ ] ktlint/detekt 未接入，仅另行确定范围时实施，CI 修改先确认，不沿用旧 M2 排期。

## 范围边界

| 功能 | 现状 / 边界 |
|---|---|
| 分类、日历/农历、提醒诊断、部件、搜索、滚轮、撤销、备份、更新 | 已实现，继续验收/修复，不重做 |
| 图片背景 / 取色 | 4.1.0 已实现并修换图边界，待 AL；图片本体不备份 |
| 部件配置 | 保留预设/字号/配色/显隐/绑定，不扩逐元素对齐或直接壁纸取色 |
| 字体库、节日皮肤 | 延后，无新实施授权 |
| 头像 / Emoji / 单位文案、赞助页 | 不做，保留关于页 |
| ColorOS 私有服务卡片 | 不做；标准 pin 无法指定桌面坐标 |

后续只更新对应待办，区分代码、本地验证、真机通过、远端发布；约束/构建/摘要集中 HANDOFF，避免多份漂移。

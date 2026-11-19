# TODO — 朝夕 · 当前待办

> 更新：2026-11-19。续接先读 [HANDOFF](HANDOFF.md)；历史实现见 [CHANGELOG](CHANGELOG.md)，许可归属见 [LICENSE-AUDIT](LICENSE-AUDIT.md)。
> 当前状态：**4.5.0 / 78 已正式发布（2026-11-19T09:59:53Z）**——Release `451154161`、tag `v4.5.0` = `9043c9b`、
> 资产 `morrow-78-4.5.0-release.apk` 5,359,721 B / `bdb0f872…53667a`，证书同 68（可原位升级）。
> 上一版 4.4.1 / 77：Release `450909704`、资产 `4ea97ff9…08dc`。

## 4.5.0 / 78：子任务 · 时间轴 15 分钟 · 拖拽 · 自然语言 · 进度环

- [x] **子任务 / checklist**：`events.checklist` 一列 JSON（上限 20 条 / 标题 60 字），不建子表（避开 `replaceAll()` 孤儿）；不用逗号串（标签的 `clean` 会把逗号换成空格）。入口：编辑面板增删勾、详情页勾选、首页卡片「3/5 子任务」+ 细进度条。
- [x] **倒计时环三条轨道**：待办有子任务看勾选比例、重复事件看「上次 → 下次」周期、其余看「创建 → 目标」。**修掉两个真问题**：`EventCardView` 里 `if (repeatType == NONE)` 整类跳过重复事件；`refreshClock` 每秒用旧规则重算会把前两轨打回 0。
- [x] **时间轴 15 分钟**：`SNAP_MINUTES`/`MIN_BLOCK_MINUTES = 15`、网格 96 条；空白处**长按后拖**出区间开新建（点按会抢滚动）；块底 14dp 把手改时长（不进撤销队列）；`NowLine` 时刻标签 + 1.5dp 线。
- [x] **农历年份可切**：`DateCalculatorSheet` 的年份从三重锁死改为可 ±（`MIN_YEAR..MAX_YEAR`），年份进 `remember` 状态。
- [x] **长按图标快捷新建**：`shortcuts.xml` 两条静态快捷方式 + `MainActivity` 按 action 分派并消费后清空。
- [x] **自然语言输入**：`NaturalDateParser`（纯规则）＋ `EventFormState.applyParsed()`；「每周一三五」整体认、「初十」单独判、时段词修正小时。
- [x] **小按钮按压去泛白**：`GlassPanel` / `GlassCirclePanel` / `GlassTextButton` 不再把 `onClick` 交给材质（小控件满档 + 3.33 倍增益 = 整块白），改自绘「缩小 4.5% + 7% 薄高光」。
- [x] **存储**：Room schema **5→6**（`MIGRATION_5_6`）；备份 format **4→5**；`UndoCodec` + `UndoKind.TOGGLE_CHECKLIST`。
- [x] **验证**：**514 项单测 / 0 失败**（新增 50 项：checklist 12、自然语言 17、表单合并 13、进度环 8；三条存储路径各补往返）；lint **0 errors / 15 warnings**；`assembleRelease` + `verifyRuntimeNoticeCompleteness` 通过。
- [x] **发布 4.5.0 / 78**：CI 在 `9043c9b` 转绿 → draft（id `451154161`）→ 上传 `morrow-78-4.5.0-release.apk` → API 回下载 `cmp` 逐字节一致 → 转正式（`published_at` 2026-11-19T09:59:53Z）→ 回填 `update/manifest.json`（`9ce37c6`）；`/releases/latest` 与公网下载地址均已回读一致。

## 4.4.1 / 77：修构建链（Glasmio AAR 进仓库 · 许可哈希按 LF 锁定）

- [x] **诊断**：4.4.0 的三次推送 CI 全红——`Could not resolve ... io.github.neoruaa.glasmio:glasmio-scaffold:0.1.4-main`（依赖只在本机 `~/.m2`）；补上 AAR 后又挂在 `generateRuntimeNotices`（锁定哈希是 Windows CRLF 字节，CI 检出为 LF）。
- [x] **AAR 进仓库**：`vendor/glasmio-maven/` 6 模块 `.aar`/`.pom`/`.module`（6.3 MB / 18 文件），`settings.gradle.kts` 用 file Maven 仓替代 `mavenLocal()`，`scripts/build-glasmio.sh` 加 `sync`。
- [x] **哈希改 LF**：`glasmioNoticeSha256` → `297bbf9d…`（= 上游 `030d31c` blob）；随包声明文件落盘 LF；`RuntimeNoticesAssetTest` 同步。
- [x] **验证**：搬走 `~/.m2` 与 Gradle 依赖缓存后仍可解析；CI 在 `ca12bfd` 转绿；459 项 / 57 XML / 0 失败；lint 0/15；APK 5,325,145 B / `4ea97ff9…08dc`、77 / 4.4.1、8 份许可资产一致、图像 0、`runtime.txt` 122,289 B / `478adc67…a8d6`。
- [x] **发布**：77 / 4.4.1 已发布——Release id `450909704`、`published_at` 2026-11-19T05:21:27Z、tag `v4.4.1` = `4186831`、main = `9bf0457`；资产回下载 `cmp` 一致后转正式，`/releases/latest` 与 `update/manifest.json` 回读一致。

## 4.4.0 / 76：顶栏同基线 · 月视图标记 · 小部件玻璃行 · 图标换设计 · Glasmio 走 main

- [x] **三页顶栏同基线**：新增 `ui/component/PageHeader.kt`（`PageHeaderRow` / `PageTitleSlot` / `PageHeaderActions`），标题首行与按钮各占固定 46dp 的槽，槽内居中。三页按钮中心恒为 `HeaderTop(12) + 46/2 = 35dp`，不再随标题行数漂移。回归：`PageHeaderAlignmentTest` 按「单行 / 两行 / 34sp 大标题 + 副标题」三种真实形态量按钮中心，断言三者相等且等于 35dp。
- [x] **月视图今天标记**：去掉整格描边圆（直径盖住农历行与底部事件点，圈底必然压点），改成只包住日期数字的淡色圆角底 + 强调色数字；事件点固定占底部 6dp，最多 4 点、2.5dp 直径。年视图格子只有 16dp，改用「强调色 + 加粗」标今天，不画圆。
- [x] **小部件行卡片**：`widget_row_fill` 由纯白实心改「白色纵向 alpha 渐变」（顶部 0.90 → 底部 0.75），圆角 26→22dp；描边改 30% 白；`WidgetAppearance.rowTint` 新增 0.62 不透明度上限，任何配色档都不会刷成实心白纸片。
- [x] **删除字体推荐链接**：移除 `FontSheet` 的「去哪里找字体」区块与四款 OFL 字体跳转，连同 `AppFont` 的 `RecommendedFont` / `RECOMMENDED_FONTS`；导入引导与授权提示保留。
- [x] **Glasmio 换 main 源码**：`vendor/glasmio` 作为 submodule 固定 main 的 `030d31c`（比 0.1.3 多 30 个提交：insets/安全区、玻璃渲染回退、菜单快照、导航过渡修复等）。因 AGP 9 的 Android 库在 composite build 下变体匹配失败，改由 `scripts/build-glasmio.sh` 编出 `0.1.4-main`，**AAR 随仓库提交在 `vendor/glasmio-maven`**（用方无需 NDK，也不再依赖 mavenLocal）。
- [x] **图标换设计**：「半日半月」——一侧实心暖金半圆、另一侧等宽描边圆弧，中间一刀分开；背景纯色 `#171728`。去掉光芒/地平线/倒影等装饰，缩小到启动器尺寸也不糊。
- [x] **交接文档精简**：HANDOFF 359→约 120 行、TODO 176→约 80 行，历史批次收敛为「一行索引 + 指向 CHANGELOG」。
- [x] **验证**：**459 项单测 / 57 份 XML / 0 失败**；lint 0 errors / 15 warnings；`assembleRelease` + `verifyRuntimeNoticeCompleteness` 通过；APK 5,322,913 B / `8d5f7539…b2182`、76 / 4.4.0、证书同 68、7 份许可资产一致、图像 0、`runtime.txt` 116,929 B / `6b0dfaf0…92bc`。
- [x] **发布**：76 / 4.4.0 已发布；tag `v4.4.0` = `842d52e`，资产回下载 `cmp` 一致后转正式，`/releases/latest` 与 `update/manifest.json` 已回读一致。

## 真机验收（累积，按版本标注）

- [ ] **4.5.0**：子任务勾选后首页进度条与环是否同步；时间轴空白处**长按后拖**能否建出 15 分钟粒度的块、会不会抢走纵向滚动；块底把手改时长是否顺手（误触与吸附手感）；「现在」线在跨小时时的位置；日期计算器农历切年份后的换算结果；长按图标两个快捷方式是否都进对面板；一句话输入（试「明天下午三点 开会 1 小时」「每周一三五 健身」「农历八月十五 中秋」）；排序胶囊与 ✕ 按下是否还泛白；生日与新加待办是否都有进度环；**schema 5→6 迁移**（77 → 78 原位升级保数据）。

- [ ] **4.4.0**：三页顶栏按钮是否真的齐平（设置 / 日历 / 首页来回切）；月视图今天标记与多个事件点的间距；年视图今天是否够清楚；小部件行卡片在浅色与深色壁纸上的通透度；新图标在启动器 / 圆形 / 方圆 / 主题图标下的观感；字体设置里推荐区块移除后的留白。
- [ ] **4.4.0 依赖**：Glasmio 换 main 后，玻璃面板、底栏、菜单、Sheet 的渲染与动画是否与 4.3.1 一致（重点：`GlassMenu` 展开、`GlassSheet` 内边距、日历翻页过渡）。
- [ ] **4.3.1**：日历列表视图不再闪退（每天 / 每周 / 跨多天持续事件）；年视图两列观感。
- [ ] **4.3.0**：有时长事件点列表不再闪退；归档圆钮观感；多天事件结束日滚轮与开始日一致；**schema 4→5 迁移**。
- [ ] **4.2.0**：时间块重叠分列、当前时刻线；农历生日跨年；归档箱交互；日期计算器；**schema 2→3→4 迁移**。
- [ ] **通用**：同签名原位升级保数据（当前 77 → 78，schema 5→6；也可直接从 76 或更早原位升）；大字体下的列表与顶栏；节日柔光在浅色主题的浓度。

## 长期项

- [ ] **人工逐组件版权 / NOTICE 终审**：`docs/LICENSE-AUDIT.md` 已按 109 个坐标回查正文组，`verifyRuntimeNoticeCompleteness` 退出 0 只证明文本齐全，不等于逐组件归属审阅完成。
- [ ] **可选工程项**：ktlint / detekt 未接入；接之前先确认 CI 改动范围。

## 范围边界

| 项目 | 现状 / 边界 |
|---|---|
| 事件类型、重复规则、农历、提醒、持续事件、时间块、标记、计算器、归档 | 已实现，继续验收 / 修缺陷，不重做 |
| 日历四视图、年视图、列表视图 | 已实现；列表视图的 key 冲突已在 4.3.1 修掉 |
| 小部件 | 预设 / 字号 / 配色 / 显隐 / 绑定；不扩逐元素对齐，不实时采样桌面 |
| 字体 | 只做本机导入（SAF）+ 就地预览；**不内置、不推荐外链** |
| 节日皮肤 | 八个农历节日柔光 + 首页祝语；中元不给 |
| 头像 / Emoji / 赞助页 / ColorOS 私有卡片 | 不做 |

后续只更新本文件与本批对应章节；构建、发布、同步步骤集中 HANDOFF，避免多份漂移。

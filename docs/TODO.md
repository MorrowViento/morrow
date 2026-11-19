# TODO — 朝夕 · 当前待办

> 更新：2026-11-19。续接先读 [HANDOFF](HANDOFF.md)；历史实现见 [CHANGELOG](CHANGELOG.md)，许可归属见 [LICENSE-AUDIT](LICENSE-AUDIT.md)。
> 当前状态：**4.4.1 / 77 已正式发布**（内容同 4.4.0，只修构建链）。上一版 4.4.0 / 76：Release `450858119`、资产 `8d5f7539…b2182`。

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

- [ ] **4.4.0**：三页顶栏按钮是否真的齐平（设置 / 日历 / 首页来回切）；月视图今天标记与多个事件点的间距；年视图今天是否够清楚；小部件行卡片在浅色与深色壁纸上的通透度；新图标在启动器 / 圆形 / 方圆 / 主题图标下的观感；字体设置里推荐区块移除后的留白。
- [ ] **4.4.0 依赖**：Glasmio 换 main 后，玻璃面板、底栏、菜单、Sheet 的渲染与动画是否与 4.3.1 一致（重点：`GlassMenu` 展开、`GlassSheet` 内边距、日历翻页过渡）。
- [ ] **4.3.1**：日历列表视图不再闪退（每天 / 每周 / 跨多天持续事件）；年视图两列观感。
- [ ] **4.3.0**：有时长事件点列表不再闪退；归档圆钮观感；多天事件结束日滚轮与开始日一致；**schema 4→5 迁移**。
- [ ] **4.2.0**：时间块重叠分列、当前时刻线；农历生日跨年；归档箱交互；日期计算器；**schema 2→3→4 迁移**。
- [ ] **通用**：同签名原位升级保数据（当前 76 → 77，schema 未变；也可直接从 75 或更早原位升）；大字体下的列表与顶栏；节日柔光在浅色主题的浓度。

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

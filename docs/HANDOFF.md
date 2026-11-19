# HANDOFF — 朝夕 · 会话续接入口

> 更新：2026-11-19。默认只读本文件与 [TODO](TODO.md)；历史批次查 [CHANGELOG](CHANGELOG.md)，
> 许可归属查 [LICENSE-AUDIT](LICENSE-AUDIT.md)，人工验收查 [MANUAL-TEST](MANUAL-TEST.md)（该文件不代勾）。
> 当前状态：**4.4.0 / 76 已正式发布（2026-11-19T03:54:20Z）**。tag `v4.4.0` → `842d52e`、Release「朝夕 4.4.0」
> id `450858119`、资产 `morrow-76-4.4.0-release.apk` **5,322,913 B / `8d5f7539…b2182`**（回下载 `cmp` 逐字节一致后才转正式）；
> 提交链 `73ed9b3`(代码+版本+submodule) → `842d52e`(文档) → `a02646a`(manifest)，main = `a02646a`。
> aapt2 76 / 4.4.0 / minSdk 33 / targetSdk 37、证书 `8894605E…8D02` 同 68、全量 **459 项 / 57 XML / 0 失败**、
> lint 0 errors / 15 warnings、`runtime.txt` 116,929 B / `6b0dfaf0…92bc`、7 份许可资产与源一致、图像条目 0。
> 上一版 4.3.1 / 75 已发布（Release `450807520`）。

## #28 4.4.0 / 76：顶栏同基线 · 月视图标记 · 小部件玻璃行 · 图标换设计 · Glasmio 走 main

**用户指令**：顶栏按钮位置与高度在三页叠加后不重合；小部件纯白卡片难看；删掉字体推荐外链；Glasmio 同步到 main
而不是 0.1.3；日历圆圈与事件点重合；图标更现代化；交接文档去冗余后发一版 release。

### ① 顶栏三页同基线（本批最容易复发的一项）

病根不是内边距，是**垂直居中的参照物不同**：三页都写 `Row(verticalAlignment = CenterVertically)`，
Row 的高度由最高的子项决定——首页是「34sp 大标题 + 副标题」，日历是「17sp 标题 + 11sp 副标题」，
设置只有一行 22sp 标题。同样是 34dp 圆钮，被各自的 Row 拽到不同位置，叠图能差出 1.5–7dp。

修法：新增 `ui/component/PageHeader.kt`，把「首行」变成**固定高度的槽**：

```kotlin
PageHeaderRow {                       // padding 16/12/16/4 + 顶对齐
    Column(Modifier.weight(1f)) {
        PageTitleSlot { Text("朝夕") }  // 高 46dp，文字在槽内居中
        Text("4 个事件 · 3 进行中")      // 副标题挂在槽下面，不参与首行
    }
    PageHeaderActions { SortChip(); MoreButton() }  // 同高 46dp，按钮居中
}
```

于是三页按钮中心恒为 `HeaderTop(12) + HeaderFirstLine(46)/2 = 35dp`。
`PageChrome.HeaderFirstLine = 46.dp`、`HeaderActionGap = 8.dp`（日历右侧原来 10dp，一并收敛）。
首页大标题显式 `lineHeight = titleSize * 1.15`，否则 34sp 的默认行高会超槽被裁。
回归 `PageHeaderAlignmentTest` 用三种真实标题形态量 `getUnclippedBoundsInRoot`，断言偏移全等且 = 35dp。

### ② 月视图：今天标记与事件点分离

旧：今天画一个 `matchParentSize().padding(6.dp)` 的描边圆。格子约 48dp，圆直径约 36dp，
而内容（日期 18sp + 农历 12sp + 点 4dp ≈ 34dp）整块居中——圆底边必然压到那排事件点，就是截图上「圈和点黏在一起」。

现在：今天 = 只包住数字的淡色圆角底（`accent 18%` + `RoundedCornerShape(7.dp)`）+ 强调色数字；
事件点固定占底部 6dp 一条，最多 4 个、2.5dp 直径、2.5dp 间距、强调色 75%。
年视图格子只有 16dp，圆会更挤，改成「强调色 + 加粗」标今天，不画圆（`MonthCard`）。

### ③ 小部件行卡片去「纸白」

`widget_row_fill.xml` 原来是 `#FFFFFFFF` 实心 + 26dp 圆角，`setColorFilter(rowTint)` 上色后是不透明浅白片，
在暖色壁纸上像贴纸。改成**白色纵向 alpha 渐变**（`E6→BF`，圆角 22dp），描边降到 30% 白；
`WidgetAppearance.rowTint` 加 `ROW_MAX_ALPHA = 0.62f` 上限 —— `veilAlpha(frost, opaque)` 最大会到 0.72，
不封顶则「不透明」档仍是实心白。
> shape 的 alpha 与 colorFilter 的 alpha 在 SRC_ATOP 下相乘，所以 xml 里给的是相对系数，真正的浓度由 rowTint 决定。

### ④ 删掉字体推荐外链

`FontSheet` 的「去哪里找字体」区块 + 四款 OFL 跳转行、`AppFont.RecommendedFont` / `RECOMMENDED_FONTS` 全部移除，
连带 `Intent` / `toUri` / `LocalContext` 的无用 import。导入按钮、32 MB 上限、sfnt 魔数校验、就地预览、授权提示都保留。

### ⑤ Glasmio 从 0.1.3 换到 main 源码

上游 main 比 `v0.1.3` 多 **30 个提交**（`030d31c` —— Android 12 玻璃渲染回退、insets/安全区策略、
玻璃 popover/tooltip、pull-to-refresh、菜单字段快照、导航过渡中断修复等），但 `VERSION_NAME` 仍是 0.1.3、
Maven Central 最新也只到 0.1.3，没有可依赖的发布物。

- `vendor/glasmio` 是 **git submodule**（固定在 `030d31c`），Morrow 依赖 `0.1.4-main`、仓库加 `mavenLocal()`。
- **不能用 `includeBuild`**：AGP 9 的 Android 库在 composite build 里不发布 `library` / `androidJvm` 变体属性，
  依赖解析直接报 variant ambiguity（已实测失败）。
- `glasmio-glass` 有 C++ SDF 着色器，构建需要 **NDK 27.1.12297006 + CMake 3.22.1**（已装进 `.toolchain/sdk`）。
- 同步命令见下「Glasmio 源码同步」；`vendor` 里改 `gradle.properties` 的版本号不算改动（用 `-PVERSION_NAME` 覆盖）。

### ⑥ 图标换设计：「半日半月」

去掉「日轮 + 八道光芒 + 地平线 + 倒影」这套插画式构图，改成一个几何分割圆：

- 左半 `M54,29 A25,25 0 0 0 54,79 Z` 实心暖金 `#F3C56C`（朝）
- 右半 `M54,29 A25,25 0 0 1 54,79` 同色 6dp 描边、圆头（夕）
- 背景纯色 `#171728`；三份 drawable（foreground / background / monochrome）同几何，monochrome 纯白给 Android 13 主题图标
- 半径 25 + 描边 3，最远 28dp < 33dp 安全半径，圆形 / 方圆 / 水滴遮罩都切不到

## 最近三批（细节看 CHANGELOG）

| 版本 | 一句话 | 关键点 |
|---|---|---|
| 4.3.1 / 75 | 日历列表视图闪退真因 | `MonthListView` 的 LazyColumn key 只用事件 id，每天/每周事件同月多条 → `Key was already used`；key 加日期。年视图两列。 |
| 4.3.0 / 74 | 修复打磨 + 多天结束时刻 | 归档按钮改 56dp 圆钮；`CountdownEvent.endTime` + schema 4→5；图标首版重绘。 |
| 4.2.0 / 73 | 重复规则 + 时间块 / 标记 / 计算器 / 归档 | `RepeatType` 五档、农历循环、多点提醒、持续事件；schema 2→3→4。 |

## 工程约定

### 构建（Git Bash，从仓库根）

```bash
export JAVA_HOME="$(cd ../.toolchain && pwd)/jdk/jdk-21.0.13+9"
export ANDROID_HOME="$(cd ../.toolchain && pwd)/sdk"
../.toolchain/gradle-9.6.0/bin/gradle :app:testDebugUnitTest --rerun :app:lintRelease \
    :app:assembleRelease :app:verifyRuntimeNoticeCompleteness --console=plain
```

- 只有 `testDebugUnitTest` 一个单测变体（没有 `testReleaseUnitTest`）。
- `rm -rf` 删 `test-results` 会触发安全删除拦截，用 `--rerun`。
- 工具链固定在 `../.toolchain/{jdk,gradle,sdk}`；本批新增 `sdk/ndk/27.1.12297006` 与 `sdk/cmake/3.22.1`。

### Glasmio 源码同步

```bash
# 1) 拉上游 main（首次：git submodule update --init --recursive）
cd vendor/glasmio && git fetch origin main && git checkout origin/main
# 2) 发布到本机 Maven 本地仓库（版本号用 -P 覆盖，别改 gradle.properties）
cd ../.. && ../.toolchain/gradle-9.6.0/bin/gradle -p vendor/glasmio -PVERSION_NAME=0.1.4-main \
    :glasmio-core:publishToMavenLocal :glasmio-glass:publishToMavenLocal \
    :glasmio-icons:publishToMavenLocal :glasmio-ui:publishToMavenLocal \
    :glasmio-preference:publishToMavenLocal :glasmio-scaffold:publishToMavenLocal
# 3) 六个模块都发完再构建 Morrow（Dokka 会拖时间，属正常）
```

升级后必须回归：`GlassPanel` / `GlassCirclePanel` / `GlassMenu` / `GlassSheet` / `GlasmioScaffold` 的调用点
（封装层在 `ui/glass/`，改动只在那一层收口）。

### 发布流程（手工 API，脚本未跟上）

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`（**必须用户点头**）。
2. 全量验证四件套（见上）；`verifyRuntimeNoticeCompleteness` 退出 0 不等于许可审阅完成。
3. APK 核验：`aapt2 dump badging`（版本 / minSdk 33 / targetSdk 37）、`apksigner.jar verify --print-certs`
   （证书 SHA-256 `8894605E…8D02`，自 68 起未变）、`unzip -p ... assets/licenses/*.txt | sha256sum` 逐份对源、
   `runtime.txt` 116,447 B / `d1508af0…1449`、图像条目 0。
4. 提交 → 推送 main → 建 draft → 上传 asset → **回下载 `cmp` 逐字节一致** → 转正式 → 回填 `update/manifest.json`。
5. 注意：`target_commitish` 用短 SHA 会 422，必须完整 SHA 或分支名；`GITHUB_PERSONAL_ACCESS_TOKEN` 已失效，
   token 从 `~/.git-credentials` 取 `ghp_`；API 与 git 都走 `127.0.0.1:10809`（WorkBuddy 注入的 59xxx 代理对 github 会 502）。
6. `scripts/release.sh` 仍是旧的 `v<name>-<code>` 命名，与手工流程不一致 —— 要脚本化得先改它。

### 受保护与边界

- **不编辑 / 不代勾 / 不提交 `docs/MANUAL-TEST.md`**；已有本地修改，保护 SHA-256
  `93FBCB61575E9B06AFED6BF0971BFC7818E6274FD5C2F41F712B29E3042C8A4A`（每批发布后复核）。
- 不改 CI、密钥、包名、schema 语义（只增可空列）、生产依赖（Glasmio 是唯一例外，本轮按用户要求换源）、AGP / Kotlin 版本。
- 不安装 / 卸载设备；改版本号须用户点头。
- 字体不随包（`runtime.txt` 自 4.1.2 起未变，9 组 / 119 来源）。
- 玻璃控件尺寸集中在 `ui/component/PageChrome.kt`：圆钮 34dp / 字形 17sp / 胶囊 34dp / 主操作 56dp / 首行槽 46dp。

### 崩溃排查经验（4.3.1 得出来的）

状态层有 `.catch`，纯函数与 `HomeUiState` 永远抓不到渲染期异常 —— **只有 Compose 组合期才抛**。
所以做法是 Robolectric + `createComposeRule` 真渲染"四视图 × 重复事件"，并**先回退修复复现异常、再恢复确认全绿**。
4.3.0 当时只补了详情页测试、漏了日历列表视图，于是同一个 bug 又活了一版。

## 文档分工

| 文件 | 用途 |
|---|---|
| HANDOFF（本文件） | 当前状态、本批实现、构建/同步/发布步骤 |
| TODO | 当前待办与真机验收清单 |
| CHANGELOG | 一行一条的版本索引 |
| MANUAL-TEST | 人工验收条目（只读，不代勾） |
| LICENSE-AUDIT | 109 个坐标的许可正文归属 |
| RELEASE / SIGNING | 发版与签名细则 |

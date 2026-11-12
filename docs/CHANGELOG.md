# CHANGELOG

> 倒序排列，最新在上。一行结论优先，细节看提交。

## [2026-11-13] 4.0.0-alpha9 (48)：底栏换成 Glasmio 导航栏 + 悬浮加号、厚度改图标四档、编辑面板补动效

- **底栏整条重做**：`GlasmioNavigationBar`（图标 + 10sp 小字、选中格有滑动/可拉伸的透镜指示器）
  放在左，右侧一颗**同高的独立悬浮圆钮**做「新建」，两者装在 `BottomNavigationContact` 里
  **共用同一份材质上下文**——材质由宿主按两颗控件的并集绘制，折射、液面、按压高光连成一体，
  这就是示例里「导航栏 + 悬浮搜索」的原版观感。三格是 主页 / 日历 / 搜索（预留，
  点一下给提示）。中央原来的「＋」格子与蓝色圆底一并去掉。
- **「底栏完全不透明」的修法**：以前每个格子各是一个 `GlassSurface`，外面还套
  `GlasmioToolbarActionHost(Merged)`，再叠一层本项目的色纱，三层填充加起来就是一块实心板。
  现在整条只由库的 `navigationGlassBackground` 画一次，**不套色纱**，透明度回到库的默认值。
- **底栏不再受「玻璃厚度」影响**：它是系统导航面，iOS 也从不随内容材质变厚薄；
  厚度现在只作用于卡片 / 弹层 / 菜单。
- **玻璃厚度改回图标分段控件**：`GlassFrostLevel` 四档（通透 / 轻盈 / 标准 / 厚重），
  图标是「一张玻璃卡叠在两个圆点（背后的内容）上」，档位越高面越实、圆点越看不见——
  透明度这个抽象量被画成了可见的遮挡关系。色纱曲线同时调成 `0.72 * t^1.25`
  （四档落在 0 / 0.19 / 0.40 / 0.68），默认 `0.62` 对应「标准」。滑块与拖动预览链路删除。
- **编辑面板的按钮动效**：`ChoiceChip` 与弹层里的「取消 / 保存」按下时缩小到 0.94~0.945、
  底色加深、外圈泛一圈高光，走同一条带一点回弹的弹簧（`dampingRatio = 0.55`）。
  没有给每颗 chip 套 `GlassSurface`：一屏十几颗，每颗开一次材质采样与折射会掉帧。
- **日期滚轮的选中条改成流体玻璃**：四层叠出来——极淡的面、顶部镜面高光、底部内阴影、
  发丝描边，`WheelDateTimePicker` 汇总五个滚轮的 `isScrollInProgress`，滑动时镜面与内阴影
  一起提亮再落回。数字不再按选中换字号（换字号要重排，滚动会跳），改成缩放 + 浓淡的弹簧。
- **修「数字不在暗框里居中」**：高亮条原先居中在「滚轮 + 年月日单位行」的整列上，
  单位行占掉约 14dp，条因此下沉半行。现在条与滚轮同处一个 Box，且滚轮的上下留白
  改为 `(rows-1)/2` 行，`firstVisibleItemIndex` 就是正中那一行。
- **修跨零点新建表单日期错位**：`EventFormState.newEvent()` 的 `date` 与 `now` 各自调了一次
  `LocalDate.now()` / `LocalDateTime.now()`，23:59:59.9 与 00:00:00.1 之间调用会得到
  「今天的日期 + 昨天的时分」。改为日期从同一个 `now` 推导（单测在凌晨跑就会红）。

## [2026-11-12] 4.0.0-alpha8 (47)：玻璃厚度改无极调节、底栏改三段

**「四档没区别」不是错觉，是库的问题。** 翻 0.1.3 的源码：
`NavigationGlassMaterial.RegularMedium(5.dp, 24.dp, .6f, .6f, .25f)` 与
`RegularLarge(5.dp, 24.dp, .6f, .6f, .25f)` 五个构造参数完全相同，`fills()` 也共用
同一个分支；在原生材质通道里两者只差一个 `luminanceRollOff`（0.65 / 0.4），
落在「模糊之后的亮度调色」上，肉眼分辨不出。而 `RegularSmall` 与 `RegularMedium`
在深色下只差面层填充与阴影半径——阴影画在面板**外侧**，纯黑背景上等于看不见。
所以四档里真正能分辨的只有「Clear ↔ 其余」。枚举只有四个值，没有第五种。

- **玻璃厚度改成 0~1 的连续滑块**（`GlasmioAdaptiveSlider`，iOS 那种按下后轨道
  展开成三格刻度的滑块）。落点是库真正有视觉差异的两处：`GlassSurface(frostRadius=)`
  的**模糊半径 2dp→24dp**，以及面板内侧色纱的**不透明度 0→0.78**（1.15 次幂曲线，
  前半段细腻、后半段快速转实）。材质枚举退化为三档（`Clear` / `RegularSmall` /
  `RegularMedium`），`RegularLarge` 不再使用。
- **拖动实时预览**：`SettingsUiState.effectiveSettings` 用内存里的
  `glassFrostPreview` 覆盖已存值，`MainActivity` 把它整份传给 `GlassTheme`，
  所以拖滑块时整页玻璃当场变厚变薄；按 2% 台阶向上游推送，避免逐帧重建模糊核。
  松手（`onValueChangeFinished`）才写 DataStore。
- **旧值全部可迁移**：`glass_frost`（Float）缺失时依次回落旧四档字符串
  （clear/light/standard/heavy → 0 / 0.28 / 0.55 / 0.92）、更旧的无障碍布尔
  （true → 0.92）、默认 0.35。导出备份同时写 `glassFrost` 与旧 `glassMaterial`。
- **底栏重排**：左「首页」/ 中「＋」/ 右「日历」。中央去掉蓝色圆底（整条底栏里
  唯一的色块，和玻璃材质放在一起很突兀），改成和左右一致的图标 + 小字，
  只是略宽一点；新增手画的房子图标（与日历图标同款 1.2dp 描边）。
- **排序挪到首页标题区**：大标题右侧一颗玻璃胶囊（排序图标 + 当前排序名），
  点一下轮换距离 / 目标日期 / 创建时间。

## [2026-11-12] 4.0.0-alpha7 (46)：配色回到 Glasmio 默认、材质四档、控件统一成分段控件

- **底色换成库的默认色板**：删掉蓝紫粉三段渐变，页面底色改为
  `GlassColors.groupedBackground` + 顶部一团 `elevatedBackground` 的径向光
  （深色 #000 / #1C1C1E，浅色 #F2F2F7 / #FFF）。文字色改读
  `GlassColors.content / secondaryContent / tertiaryContent`，底栏强调色改用
  `GlassColors.accent`（iOS 蓝，原来是自定义的朝霞橙）。
- **玻璃材质四档**：`GlassMaterialLevel`（通透 / 轻盈 / 标准 / 厚重）= 库的
  `NavigationGlassMaterial.Clear / RegularSmall / RegularMedium / RegularLarge`，
  即示例 Materials 页那四张卡。`LocalGlassMaterial` 一处注入，所有 `GlassPanel` /
  `GlassCirclePanel` 透传 `material`，改一档整屏同时变厚变薄。设置项取代原来的
  布尔「减弱透明」开关（厚重档 = 原来的减弱透明，旧数据与旧备份都会自动映射过来）。
- **设置页第二次改造**：分组卡片从 `GlasmioGroupedList` 换成真正的 `GlassSurface` ——
  前者画的是 55% 不透明的**纯色**（`SheetGroupedListBackgroundAlpha`），没有采样与折射，
  纯色底上就是一块死板方块，这就是「玻璃应用不完全」。行改成自绘：
  subtitle 允许两行（原来单行 + 省略号，几条长说明全被截成「…」），点击不带 Material
  的矩形 ripple。
- **主题改成图标分段控件**（Icon Segmented Control）：三个 Canvas 手画字形
  （自动 / 太阳 / 弯月）+ 未选压暗；指示器是库的 `SegmentedIndicator` 透镜，
  按下时几乎全透明、边缘把相邻图标折射变形。
- **单选组统一成分段控件**：小部件配置页的「样式预设 / 字号 / 配色」由三排 chip
  改成 `GlassSegmentedControl`；`ChoiceChip` 只留给多选语义（全天、每年重复、显示元素），
  并去掉 ripple（它只按布局边界裁，会在圆角外留方块）。

## [2026-11-12] 4.0.0-alpha6 (45)：界面效果与动效整体移植 Glasmio

> 分支策略：`archive/4.0.0-alpha2` 保留 alpha2 (41) 的完整快照（含 Release 对应提交），
> 之后所有「效果移植」都在 `feat/glasmio-motion` 上做，随时可以切回归档分支恢复旧观感。

- **页面切换**：`ui/glass/GlassMotion.kt` 新增 `GlassPage`。日历 / 设置不再是
  `fadeIn/fadeOut` 互相淡化，改为 iOS 的导航推入：新页整宽从右滑入、旧页滑回右侧，
  曲线是库那条临界阻尼弹簧（ω = 2π / 0.3s，刚度 ≈ 438.6；库内标了 `internal`，
  按 `IosNavigationMotion` 的公式复算并在注释里写明来源）。`reduceMotion` 时退化为瞬时切换。
- **弹层全部换成 `GlasmioBottomSheet`**（新建/编辑、详情、分类、提醒、更新、导入、许可）：
  UIKit 的 modal 呈现（上滑 + 圆角收拢）、跟随手指的下拉关闭、滚到顶继续上拉展开、
  detent、返回键与无障碍 dismiss 动作。库用 `rememberCrossWindowBackdrop` 做跨窗口采样，
  Dialog 里的玻璃仍能折射应用背景。关闭动画靠「先 `visible = false`、等库收起后再回调」实现。
- **菜单换成 `GlasmioMenu`**（首页「⋯」）：从按钮位置液态形变展开、关闭缩回，
  自动上下翻转与贴边收缩，取代原来的「遮罩 + 底部动作表」。
- **弹窗换成 `GlasmioAlertDialog`**（清空全部、清除数据）：UIKit 的缩放淡入、
  按 iOS 规则排布按钮（Cancel 加粗、Destructive 系统红）。
- **设置页换成 iOS 分组列表**：`GlasmioGroupedList` + `GlasmioListRow` + `GlasmioSectionTitle`，
  开关换成 `GlasmioSwitch`（系统绿胶囊轨道 + 按压缩放 + 透镜折射），删掉自绘的
  `animateDpAsState` 版本。
- **日历视图切换**换成 `GlasmioSegmentedControl`（一条玻璃轨道 + 会滑动拉伸的指示器），
  取代三颗独立按钮。
- **滚动回弹**：首页与设置页包进 `GlasmioOverscrollHost`，短内容/空列表也能拉出再弹回。
- 撤销条改为滑入滑出（与弹层同族曲线），不再是硬切。
- 封装层新增 `ui/glass/GlassSheet.kt`、`GlassMenu.kt`、`GlassAlert.kt`、`GlassMotion.kt`、
  `GlassNativeControls.kt`（分段控件 / 开关 / 回弹宿主），业务代码依旧不直接 import Glasmio。

## [2026-11-12] 4.0.0-alpha5 (44)：按压交回 Glasmio，底栏跨页面常驻

- **删掉自绘的按压层**（`ui/glass/GlassPress.kt` 整文件）。按压不再自己画白光环/描边，
  改为直接用库的按钮语义：`GlassPanel` / `GlassCirclePanel` 新增 `onClick` / `prominent` /
  `accent`，内部传给 `GlassSurface(onClick = ...)`。该路径内部是
  `clickable(indication = null)` + 按压高光采样 + 材质重绘，**光斑由材质本身画**，
  因此是「玻璃折射」而不是叠一层白。
- **底栏改用 `GlasmioToolbarActionHost(Merged)`**：三个格子各是一个 `GlassSurface(onClick)`，
  库把相邻玻璃合并成一整条（`ToolbarActionGroup` 的 connected run），按下时在那一格上做
  形变 + 折射，这是 0.1.3 自带的底部工具条光学。
- **底栏改为跨页面常驻**：从 `CalendarScreen` 里移出，放到首页层级最后一层（日历 / 设置之上）。
  切到日历底栏不重建、按钮不变，只把「日历」格子加一层淡橙色纱表示选中；再点一次即关闭。
- **日历页**：不再有自带底栏，视图切换（月 / 年 / 列表）与「今天」回到头部，改用
  `GlassTextButton`（`GlasmioButton`，选中态走 `GlassEmphasis.Prominent`）；
  头部圆钮、月卡、事件行全部换成 `onClick` 版本；单日格子仍是
  `clickable(indication = null)` + 底色加深（42 格不进材质链路）。
- `GlassPanel` 的 `tint` 语义修正：从「顶掉色纱」改为「色纱之上再叠一层 wash」，
  避免「减弱透明 + 选中格」反而更透。
- 新增 `ui/glass/GlassButton.kt`（文字玻璃按钮的封装，业务层不直接 import Glasmio）。

## [2026-11-12] 4.0.0-alpha4 (43)：按压改成「一圈玻璃光晕」，日历页统一到底栏

- **按压缩放升级为三层叠加**：新增 `Modifier.glassPressHalo()`——在元素**轮廓之外**画一圈
  放大的径向白光 + 形状外扩后的折射亮边（两遍描边，粗而淡 + 细而亮），配合
  `glassPressFeedback` 的放大 / 上浮（新参数 `lift`）与 Glasmio 的 shader 液体光斑，
  观感是「玻璃被顶起来、边缘折射出一圈光」。`glassPressHalo` 用 `drawBehind`，
  **必须排在 `clip` 之前**，否则光晕只剩一条硬边。
- **新增 `Modifier.glassPressClick()`**：光晕 + 裁剪 + 缩放上浮 + 手势一条链，按钮密集处
  不用再手写四行 Modifier；顺带把 `GlassPress` 记下按下的**坐标**，光晕跟着手指走。
- **日历页不再像上一版**：顶部「月 | 年 | 列表」分段控件与右下角悬浮 `＋` 全部撤掉，换成与首页
  同构的底栏（左「视图 · 月/年/列表」循环切换、中橙色「＋」、右「今天」，已在今天时置灰）。
  头部圆钮放大到 34dp 并换成玻璃按压；单日格子用 `clickable(indication = null)` + 底色加深，
  42 个格子不进 shader 光斑链路（性能）；月卡与事件行同步换掉 Material ripple。
- 顺带：`mipmap-anydpi` 合并（alpha3 已做）、`YearView` 底部留 96dp 给底栏。

## [2026-11-12] 4.0.0-alpha3 (42)：排序/日历搬到底栏，Glasmio 升 0.1.3 拿到按压光效

- **底栏取代标题栏按钮**：新增 `ui/component/HomeBottomBar.kt`——一条 62dp 胶囊玻璃，左「排序 ·
  当前模式」、中「＋」、右「日历」；首页标题区只剩「朝夕」大标题和「⋯」。首页右下角的 `CreateFab`
  随之移除（日历页仍保留它，因为日历整页覆盖会把底栏盖住）。
- **修掉「点排序冒出方形背景框」**：根因不是样式没调好——`Modifier.clickable` 的 Material ripple
  只按**布局边界**裁剪，不认玻璃的圆角/胶囊，所以胶囊里会冒出一个矩形高亮。新增
  `ui/glass/GlassPress.kt`：手势自己处理（`detectTapGestures`）+ Glasmio 的
  `InteractiveHighlight` 液体光斑，全程不出现 Material 指示器。事件卡片、`⋯`、日历页 `＋` 同步切换，
  卡片按压缩放取 0.012（大面积放大过头会晕）。
- **Glasmio 0.1.2 → 0.1.3**：0.1.3 带导航 / 浮动搜索 / 自适应工具栏与玻璃渲染性能更新，
  `GlassSurface` 新增 `onClick` / `onLongClick` / `detailAction` 按压光学，新增公开的
  `InteractiveHighlight`。封装层同步扩展：`GlassPanel(capsule=, tint=)`、
  `GlassCirclePanel(tint=)`、公开 `glassPanelShape()`。**只有 `libs.versions.toml` 一处版本号**，
  业务代码没有为升级改动（`ui/glass/` 之外零改动）。
- **注意**：按压光效走 `RuntimeShader`，Glasmio 在 **API 36 以下直接跳过**（源码里 `SDK_INT < 36`
  就 return）。所以封装层另加一层 0.10 alpha 的白色提亮当兜底，低版本仍能感知「按下去了」。
- **图标目录合并**：`mipmap-anydpi-v26` / `-v33` 合并为 `mipmap-anydpi`（minSdk 33 用不到
  旧限定符），并补 `<monochrome>`——之前只有一个版本带单色层，Android 13+ 主题图标取不到。
  顺带清掉 4 条 lint 警告（`ObsoleteSdkInt` ×2、`MonochromeLauncherIcon` ×2）。
- **验证**：228 项单测全绿；lint **0 errors / 10 warnings**（其中 7 条是「依赖有新版本」类提示，
  随依赖元数据刷新出现）；debug APK `morrow-42-4.0.0-alpha3-debug-bottom-bar.apk`，
  79,902,883 B，`sha256:e804a7ca…973147`。

## [2026-11-12] 发版流程固化为一条命令（`scripts/release.sh`）

- 新增 `scripts/release.sh`：读 gradle 版本号 → 校验工作区与标签 → 构建 → 命名资产
  → 建 Release → 上传（失败重试 3 轮）→ **回读 `/releases/latest` 核对标签/资产/摘要**
  → 回填并提交 `update/manifest.json`。首次发版这些步骤全是手工 curl，代理 502 与摘要
  抄错各踩过一次，因此每一步都做成硬校验而不是注释。
- 两个容易翻车的点在脚本里挡掉：资产名必须匹配 `morrow-<code>-…`（客户端靠它解析
  versionCode，不合规会被判成「没有更新」）；默认不标 prerelease（`/releases/latest`
  不返回预发布，标了手机永远显示已是最新）。
- 新增 `docs/RELEASE.md`：一条命令的用法、三条硬规则、签名差异（debug 76 MB / release
  R8 后十几 MB）、手工兜底片段、撤回策略（转 draft 而不是删资产）。

## [2026-11-12] 发布 4.0.0-alpha2 (41)：仓库统一更名 morrow，首个 GitHub Release 上线

- **命名收敛**：GitHub 仓库与本地目录 `morrow-native` → **`morrow`**，GitHub 旧地址自动重定向；
  `UpdateEndpoints.REPO`、`UpdateNetwork` 的 UA、`BackupFiles.projectUri()`、设置页「项目主页」
  链接同步为 `MorrowViento/morrow`——否则改包名后「检查更新」会打到旧仓库。
- **版本**：`versionCode 40→41`、`versionName 4.0.0-alpha1→4.0.0-alpha2`。
  应用内更新按 `versionCode` 判定，同版本号重新发版用户收不到提示，故必须上调。
- **首个 Release**：`v4.0.0-alpha2-41`，资产 `morrow-41-4.0.0-alpha2-debug.apk`。
  发的是 **debug 签名**包：现网安装的也是 debug 包，能直接覆盖升级，正好用来验 N1–N9；
  等自有密钥出正式包后可再发一版，那时需先卸载 debug 包（签名不同不能互装）。
- **清单**：`update/manifest.json` 同步为该 Release 的 tag / 资产名 / 大小 / SHA-256，
  作为 API 不可达时 jsDelivr / raw 两路的回退数据。

## [2026-11-12] 品牌重塑：应用更名「朝夕」、工程改为 morrow、新增图标与自有签名

- **应用名**：`strings.xml` 的 `app_name` 由「萤」改为「**朝夕**」；小部件中文标签与设置页文案同步。
- **工程命名**：Gradle `rootProject.name` `ying`→`morrow`，本地目录 `ying-native`→`morrow`；
  `namespace` / `applicationId` / Kotlin 包 `com.jiuxina.ying`→`com.morrowviento.morrow`；
  `YingApp` / `YingDatabase` / `YingWidgetProvider` / `YingDetailWidgetProvider` / `Theme.Ying` → `Morrow*`；
  源码与测试包目录、Room schema 目录随包名迁移。
- **内部标识**：`ying.db`→`morrow.db`、`ying_settings`→`morrow_settings`、`ying://`→`morrow://`、
  `com.jiuxina.ying.action.*`→`com.morrowviento.morrow.action.*`；签名参数 `YING_*` / `ying.keystore`
  改为 `MORROW_*` / `morrow.*`；更新包与 CI 工件名统一 `morrow-`。
- **图标（新增）**：此前 `AndroidManifest` **没有** `android:icon`，应用一直显示系统默认图标。现补自适应图标：
  `ic_launcher_background`（晨曦金 → 朝霞橙 → 暮色紫的线性渐变）、`ic_launcher_foreground`
  （日轮 + 地平线 + 倒影，构图控制在中心 66dp 安全圆内）、`ic_launcher_monochrome`（Android 13 主题图标）；
  `mipmap-anydpi-v26` 与 `-v33` 两套，Manifest 补 `icon` / `roundIcon`。
- **签名**：新增 `scripts/create-keystore.sh`（交互式生成 RSA 4096、密码不回显不落盘）与 `docs/SIGNING.md`
  （生成 / 注入 / 验证 / CI / 备份 / 换签名后果）；`app/build.gradle.kts` 支持环境变量与
  `~/.gradle/gradle.properties` 双通道，keystore 路径支持 `~/` 展开，缺密码或找不到文件会在配置期明确报错。
  未提供 keystore 时 release 仍未签名、debug 完全不受影响，仓库不落任何密钥。
- **破坏性**：`applicationId` 已变，旧「萤」**不能**覆盖升级；真机需先导出备份、卸载旧包、装新包后导入。
  换用 release 签名后，debug 与 release 之间也不能互相覆盖安装。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；**228 项单测全绿**；
  lint **0 errors / 9 warnings**（比上一版少 2 条，无新增）；APK 约 76 MB，包内
  `package=com.morrowviento.morrow`、`application-label=朝夕`、自适应图标资源已打包。

## [2026-11-12] 日期滚轮年份扩到 1900–2100；M7 应用内更新落地（第一批）

- 真机反馈：M2a–M6 真机验收基本通过，进入下一阶段；同时报「新建事件日期只有 2025 年，需要更早、更大的范围」。
- **① 年份范围**：`WheelPicker` 写死 `thisYear-1..thisYear+20`（2026 年时最早 2025），纪念类事件（出生年、历史日期）选不了。
  改由 `CalendarMath.SELECTABLE_YEARS` 提供 1900–2100，与农历年表 `LunarCalendar.MIN_YEAR/MAX_YEAR`、
  日历页翻页边界共用同一套——再放宽就会选出「日历里翻不到、农历查不出」的事件。
  新增 `yearIndex/yearAt` 纯函数；顺带修掉首帧 `snapshotFlow` 会把越界旧数据悄悄改写成边界年的问题
  （年份回调只在值真的变化时回写）。验收项 B6b 要求能选 1990 / 1949 / 1900 与 2100。
- **② M7 第一批（应用内更新）**，零新增依赖：`HttpURLConnection` + 已有 Gson。
  - 数据层 `service/update/`：`ReleaseJson`（GitHub Releases / 仓库清单共用解析器）、`UpdatePolicy`
    （版本号解析、ABI 选包、多源取最新、断点续传起点、SHA-256 归一化、进度换算，全部纯函数）、
    `UpdateEndpoints`（GitHub API + jsDelivr + raw 三路候选，镜像前缀由 `gradle.properties` 注入）、
    `UpdateRepository`（并发探测）、`ApkDownloader`（Range 续传 + 换线路清分片 + 长度/摘要校验）、
    `ApkInstaller`（PackageInstaller 会话优先，`ACTION_VIEW`+FileProvider 兜底）与
    `UpdateInstallReceiver`（处理 `STATUS_PENDING_USER_ACTION`）。
  - **版本判定取多源里版本号最大的一份，不是最快的一份**：CDN 清单可能有缓存，
    只按速度选会把用户困在旧版本。测速决定的是下载线路与日志，不是有无更新。
  - UI：设置 → 关于 → 「检查更新」，底部玻璃面板展示版本 / 更新说明 / 下载进度 / 安装按钮。
    手动触发而非冷启动自动跑，避免静默失败变成随机报错。
  - 工程：`INTERNET` + `REQUEST_INSTALL_PACKAGES` + FileProvider（`cache/updates/` 白名单）；
    `BuildConfig.UPDATE_MIRROR_PREFIXES`；`lint.abortOnError = true`（基线 0 errors / 11 warnings，不阻断）；
    release 签名由 `MORROW_KEYSTORE` 等环境变量注入、ABI 拆分由 `-Pmorrow.abiSplit=true` 开启，
    **默认都不启用**，日常 debug 仍是单 APK；仓库不落任何密钥。
  - 新增 `update/manifest.json` 作为 CDN 镜像清单模板（形状与 Releases API 一致），发布 Release 时同步。
  - 新增 `UpdatePolicyTest` / `ReleaseJsonTest`；`CalendarMathTest` 补年份范围 3 条。
- **关于「静默安装」**：普通第三方应用不是设备所有者，PackageInstaller 提交会话后系统仍会弹一次
  确认（`STATUS_PENDING_USER_ACTION`），本实现把它拉起而不是试图绕过；Google Play 对
  `REQUEST_INSTALL_PACKAGES` 有用途审查，若商店审核不通过，删掉该权限即可自动回退系统安装页。
- **仍待配合**：仓库目前还没有 GitHub Release，因此真机上「检查更新」会显示「已是最新版本」；
  要验完整链路需先发布一个带 APK 资产的 Release（资产名建议 `morrow-<versionCode>-<版本>-<abi>.apk`）。
  正式签名密钥由用户持有，环境变量注入即可，不由仓库生成。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；**228 项单测全绿**（新增 13 条：年份范围 3 + 更新策略/解析 10）；
  lint **0 errors / 11 warnings**（`abortOnError=true` 收紧后仍通过，无新增项）；debug APK 约 77 MB，零新增依赖。
- 状态：代码完成并提交 `a525f2a`，推送在代理 502 下后台重试第 24 次成功，本地与 `origin/main` 一致。
  工作区仅剩 `docs/MANUAL-TEST.md` 里用户勾选的 40 项真机验收记录，**按用户要求暂不提交、不推送**。
- 交付：上级目录 `morrow-4.0.0-alpha1-debug-year-range-m7.apk`。真机优先验 B6b（年份 1900 / 1949 / 1990 / 2100）、
  N1/N2/N3（更新入口 / 无更新 / 断网）、A5（断网冷启动；更新为手动触发，应用不依赖网络）。

## [2026-11-12] 「减弱透明」只动玻璃面板、保留彩色渐变；设置页重复的「总览」行合并

- 真机反馈（截图对照）：① 打开「减弱透明」后浅色整屏泛白、深色整屏发黑，图三（关闭时）的蓝紫粉渐变全没了，
  质疑「减弱透明」是不是就等于整体变黑变白；② 图三「桌面小部件」里出现**两行一模一样**的「总览 / 编辑」，要求删掉一行。
- **① 上一版修错了落点**：`GlassBackdrop` 在被采样底色上叠了一层 alpha 0.88 的中性渐变，
  虽然绕开了 Glasmio 的 `reduceTransparency` 降级开关，但等于把整屏配色抹平，视觉上仍是「变黑/变白」。
  正确语义是**只降低玻璃面板的透明度**：底色渐变两种模式下一律原样保留；`GlassPanel` / `GlassCirclePanel`
  在玻璃内侧加一层 0.55 alpha 的柔和紫色纱（浅 `#F7F3FF` / 深 `#24213F`），透过面板的背景压到约 45%，
  文字更清晰而面板之外、以及色纱下透出来的颜色都还在。`GlassAppearance()` 保持液态玻璃全开。
  设置页文案改为「让玻璃面板更实、文字更清晰，背景配色保持不变」。
- **② 两行来自桌面确实放了两张总览卡片**：`SettingsViewModel` 原先按 widgetId 逐个列行，
  总览卡片之间没有任何可区分信息，于是两行标题/副标题/按钮完全相同。
  改法：`WidgetEntries.rows()` 把同类实例折叠成一行（`PlacedWidget` 从单个 `id` 改成 `ids: List<Int>`），
  单事件按绑定事件分组；`openConfig` 带 `EXTRA_WIDGET_IDS`，配置台保存时对**整组 id** 逐个落盘并一次刷新。
  系统 / 长按菜单仍只带单个 `EXTRA_APPWIDGET_ID`，回落成「只改这一个」，两条路共用配置台。
  一行代表多个实例时副标题标注「共 N 个，一起生效」。新增 `WidgetEntriesRowsTest` 四条锁定折叠规则。
- 验收：`docs/MANUAL-TEST.md` 的 I3 改为「开/关对比，背景颜色必须一致」，新增 H12b 放两张同类卡片验证合并与整组生效。

## [2026-11-12] 减弱透明不再把控件刷成纯黑/纯白；设置页删掉多余说明行

- 真机反馈：① 设置页「桌面小部件」里「长按桌面没有『编辑』？」这一行不可点击、纯说明，多余；
  ② 打开「减弱透明」后控件直接变成纯黑或纯白，玻璃效果全没了。
- **② 根因**：`GlassAppearance(reduceTransparency = true)` 是 Glasmio 的**降级开关**——
  它让 `getUsesBackdrop()` 返回 false，`GlassSurface` 放弃背景采样与折射，直接画一块纯色面板。
  改法：不再把该设置交给库（`GlassAppearance()` 保持液态玻璃全开），改由 `LocalReduceTransparency`
  驱动 `GlassBackdrop` 在**被采样的底色**上压一层柔和中性渐变（非纯黑/纯白）。
  于是背景不再花哨、文字对比度提高，而模糊 / 折射 / 高光完整保留。设置页文案同步改为
  「压平玻璃背后的渐变，保留玻璃质感，文字更清晰」。
- **①** 删掉那条 `onClick = null` 的说明行与它前面的 Divider（否则分组末尾留一条空分隔线）。
  厂商桌面不给「编辑」的兜底路径仍在：列表里每一行的「编辑」按钮就是这个入口。

## [2026-11-12] 加小部件改为「先设置、再落桌面」；总览列表刷新加双保险

- 真机反馈两件：① pin 确认后直接落桌面，希望能**先进配置台调好外观，确认后再落桌面**；② 应用内增删事件后总览小部件不更新。
- **① 流程反转**：长按图标的快捷方式与设置页的「添加」都改为直接打开配置台（无 widgetId 即 pin 模式）。
  调好样式点「添加」才 `requestPinAppWidget`——样式先存进「待认领槽位」，新实例首次 onUpdate 时认领。
  顺带根治了冷启动 pin 失败：配置台是用户正在看的页面，窗口必然可见，`WidgetPinActivity` 与
  等首帧/重试/设置页兜底那整套机制全部删除。槽位 10 分钟过期（用户可能点了添加又在系统确认框上取消），
  已有专属配置的老实例不会被顶掉，认领即消耗保证一次设置只作用一个新实例（`WidgetPendingStyleTest` 六条锁定）。
- **② 列表不更新**：总览改成集合视图后，列表内容不在 RemoteViews 里，刷新依赖
  `notifyAppWidgetViewDataChanged` 让桌面回调 `onDataSetChanged`——部分 ROM 桌面对它的处理不可靠。
  给 adapter Intent 的 data URI 加自增序号（extras 不参与 Intent 相等性比较，只有 data 有效），
  每次刷新都迫使桌面重新绑定数据源，双保险。刷新链路补齐日志（`adb logcat -s MorrowWidget`：
  refreshAll → 已刷新 → onDataSetChanged 三段可追溯）。
- 测试注：`WidgetPendingStyleTest` 需跑在 Robolectric + runTest 下——纯 JVM 的 DataStore Okio 存储
  在「读后再写」时 Windows 上 rename 会间歇失败，非被测逻辑问题。

## [2026-11-12] 冷启动长按图标加小部件：等首帧 + 多轮重试，失败不再把路堵死

- 现象（真机反馈）：滑动翻页已正常；但**进程没运行时**长按图标加小部件仍提示「当前桌面不支持从这里添加小部件」，
  只要之前成功添加过一次就正常。
- 根因：`onResume` 只代表本进程跑起来了，窗口这一帧还没画完。ColorOS 用「窗口是否可见」判定调用方在不在前台，
  冷启动时启动初始化把这段时差拉长，请求正好落进失败窗口；进程活着时首帧早就画过，所以「添加过一次就行」。
  另外 `requestPinAppWidget` 的返回值原先根本没检查，也没有针对 launcher 侧能力查询（`isRequestPinAppWidgetSupported`）
  冷启动竞态的应对。
- 修法：
  1. 等到第一帧真正要画（`OnPreDraw`）→ 再让一帧给窗口完成上报，才发请求；`preDraw` 不触发时有 800ms 超时兜底；
  2. 检查返回值，不支持 / 返回 false 都按 250 / 500 / 800 / 1000ms 重试，总跨度约 2.5 秒；
  3. 全部失败才退到兜底**真界面**：打开应用并**把「桌面小部件」分组顶到设置页最前面**（`EXTRA_OPEN_WIDGET_SETTINGS`），
     那里的「添加」是在真前台点击的，必然成功——不再是只丢一句「桌面不支持」让用户无路可走；
  4. 每步失败原因都写 `MorrowWidgetPin` 日志，真机可通过 `adb logcat -s MorrowWidgetPin` 区分是能力查询还是请求被拒。
- 分组内容在设置页只写一份（`widgetsSection`），位置由 `highlightWidgets` 决定，避免两处分叉。

## [2026-11-12] pin 请求改为可见窗口；总览卡片改成可滚动列表，拉多高就排多少行

- 现象（真机反馈）：
  1. 不先打开应用就长按图标加小部件，会提示「当前桌面不支持…」，必须先开一次 App；
  2. 总览卡片拖大之后下方一直空白，只在创建时那几条；
  3. 希望卡片能上下滑动看其它事件。
- 根因：
  1. `WidgetPinActivity` 用了 `Theme.NoDisplay`，且 `onCreate` 里就 finish——这个 Activity
     从没到过 RESUMED。ColorOS 一类桌面要求 pin 的调用方在前台可见，于是直接丢弃请求，
     我们只能在异常路径给一句「不支持」，看起来像权限问题。
  2. 总览用 5 组**写死**的静态行（`row0..row4`），行数由样式预设决定，跟卡片高度无关；
     静态 RemoteViews 本身也不能滚动，所以放大只会留白。
- 处置：
  1. pin 改用透明但**可见**的窗口（`Theme.Morrow.PinWidget`），在 `onResume` 里发请求，
     1.2s 后自行结束；`isRequestPinAppWidgetSupported == false` 与异常分别留日志。
  2. 总览改成**集合视图**：`widget_overview.xml` 换成「头部 + ListView + 空态」，
     新增 `widget_row.xml` 与 `WidgetListService`（`RemoteViewsFactory` 直接读 Room / DataStore，
     进程被回收后桌面重新取数也自洽）。行数由卡片高度自然决定，内容超出可在卡片里上下滑动，
     上限 50 条只为防超大列表拖慢 binder。
  3. 条目点击用「模板 PendingIntent（`FLAG_MUTABLE`）+ fill-in」组合，点哪条进哪个事件；
     头部与空态点击仍是打开应用。
  4. 预设不再决定行数，配置页文案改为「卡片拉多高就排多少行，内容多了可上下滑动」。

- 关于 ColorOS 的「服务卡片」：那是 OPPO 自有的卡片框架（速览卡片 / 智慧服务平台），
  第三方只能按 OPPO 的开发规范接入，标准 Android 应用无法通过公开 API 出现在那里；
  能做的仍是 App Widget + 系统的「添加到主屏幕」确认框。

## [2026-11-12] 长按图标可直接添加小部件；小部件的编辑入口补到应用内

- 现象（真机反馈）：单事件已能绑定，但**长按小部件只有拖动手柄、没有「编辑」**；
  希望「长按应用图标就能加小部件」。
- 结论：**「长按小部件 → 编辑」无法由应用提供**。它是桌面读
  `AppWidgetProviderInfo.widgetFeatures` 后自己决定要不要渲染的菜单项，
  ColorOS 一类只给「移除 / 应用信息」。我们能改的只有「哪些入口通向同一个配置台」。
- 处置：
  1. 新增静态快捷方式（`res/xml/shortcuts.xml`）：长按「朝夕」图标 → 「总览小部件」
     「单事件小部件」。点击走 `AppWidgetManager.requestPinAppWidget`，
     系统弹「添加到主屏幕」确认框，确认即落桌面，省掉「长按空白 → 小部件 → 找朝夕 → 拖」。
     桌面不支持 pin 能力时给一句可执行的指引，不静默失败。
  2. `WidgetPinActivity`：`Theme.NoDisplay`，无界面，只做 pin 请求；
     快捷方式用不同 action 区分两种小部件（`<extra valueType>` 不支持 boolean）。
  3. 设置 → 「桌面小部件」顶部同样加两条「添加」，与快捷方式同一条链路。

## [2026-11-12] 单事件小部件能选事件了；小部件有了应用内编辑入口；诊断页写明最近一次排期走哪条通道

- 现象（真机反馈）：
  1. 单事件小部件加进来**选不了事件**，面板里根本没有「绑定事件」这一节；
  2. 长按桌面小部件**没有「编辑」**；
  3. 锁屏通知准时，但状态栏**没有闹钟图标**，怀疑功能坏了。
- 根因：
  1. `widget_detail_info.xml` 的 `android:configure` 与 `WidgetRefresher.configIntent`
     都指向 `WidgetConfigActivity`（**总览**的配置台）。单事件的配置台写好之后一直没被接上，
     从 M5 起就是这个状态：加小部件、点未绑定小部件，进的都是没有绑定区的那一面。
  2. 「长按 → 编辑」不是应用能保证的能力：它取决于桌面是否愿意为
     `widgetFeatures="reconfigurable"` 的 provider 显示这一项，ColorOS 一类只给「移除 / 应用信息」。
  3. 图标不是功能，是 `setAlarmClock` 的副产物，且只在**待触发**期间存在，触发即消失；
     落点在 12 小时之外的排期本来就只走精确闹钟、不挂图标。
- 处置：
  1. 两处指向都改成 `DetailWidgetConfigActivity`；未绑定的单事件小部件点一下即进正确的配置台。
  2. `WidgetConfigStore` 的默认样式（`save` 落盘与 `defaultStyle` 读取两处）**剥掉 `eventId`**：
     绑定属于某个实例，跟着默认样式传下去会让新加的小部件一上来就绑在前一个的事件上。
     配置台里若已绑事件被删除，回落到最近事件，不再出现「已选标记挂在一条不存在的行上」。
  3. 设置页新增「桌面小部件」分组：列出已放置的实例（单事件显示绑定的标题），
     点一下打开同一个配置台——长按没有「编辑」时这就是唯一可靠的入口。
  4. 闹钟窗口**保持 12 小时不动**：缩短只会让「晋升成系统闹钟」的时机更难命中，
     而真机上「晚一分钟」的根因正是没走到这条通道；图标只是视觉副作用。
     改为让通道**可自证**：调度器每次排期把结果（setAlarmClock / 精确 / 不精确 + 落点）
     写入 `ScheduleTraceStore`，诊断页在「系统闹钟」下方多一行「最近一次排期」，
     写明这次为什么有 / 没有图标。
- 测试：205 项全绿（新增 `ScheduleTraceTest` 5 项、`WidgetBindingIsolationTest` 4 项），
  lint 11 项基线。MANUAL-TEST 更新 G2b、H5，新增 H7b / H12。

## [2026-11-12] 翻页彻底改成瞬时切页；通知点进应用不再停在通知面板；闹钟图标可辨认

- 现象（真机反馈）：
  1. 双页动画版本翻完仍然「所有日期抖一下」；
  2. 测试通知能进应用了，但落回的是刚才那个提醒界面；
  3. 锁屏/状态栏的小闹钟图标触发后不消失，也不确定是不是本应用挂的。
- 处置：
  1. 丢掉一切位移动画：滑动只做手势判定，松手直接切页，与点头部箭头走同一条路径。
     屏上内容从此只有一个来源（单一 CalendarUiState），不存在两个状态源差一帧就抖的可能。
     浮层里删掉 FrozenPages / 位移 / 归零等待与相关 import。
  2. 通知 Intent 带 `EXTRA_FROM_NOTIFICATION`；MainActivity 自增计数传给 HomeScreen，
     收到后收起所有浮层（表单 / 分类 / 菜单 / 日历 / 提醒 / 设置），落回首页；
     真实提醒仍由 `EXTRA_EVENT_ID` 另开详情。
  3. `setAlarmClock` 只用于 **12 小时内**的落点（`ALARM_CLOCK_HORIZON_MILLIS`）：
     远期提醒改用 `setExactAndAllowWhileIdle`，不再为一年后的纪念日现在就挂图标。
     诊断页新增一行「系统闹钟」：读 `AlarmManager.getNextAlarmClock()`，
     用 `showIntent.creatorPackage` 判断归属，写明「来自朝夕 / 来自其他应用 / 当前没有」
     与下一个触发时刻，图标不再需要靠猜。
- 测试：196 项全绿（新增 `AlarmClockHorizonTest` 3 项），lint 11 项基线。
  MANUAL-TEST 更新 F12/F13/F14、G1/G2b，新增 G2c。

## [2026-11-12] 翻页后的抖动；测试通知点不进应用；提醒改用系统闹钟级别

- 现象（真机反馈）：
  1. 滑动翻页之后所有日期会抖一下；
  2. 测试通知点了没有任何反应，不会进应用；
  3. 真实到点通知仍会晚一分钟左右才出现在通知栏。
- 根因：
  1. 换页那一刻，相邻页的 lambda 已经重新指向「新的再下一页」——屏中央先闪一帧不该出现的月份，
     下一帧归零又换回正确内容，两次跳变就是「抖一下」。
  2. 诊断页的测试通知从未设置 `contentIntent`（正文里明确写了「点击应进入应用」，但代码没做）。
  3. `setExactAndAllowWhileIdle` 只保证「不被 Doze 批量推迟」，应用被 ROM 冻结时广播仍要排队到
     下一次唤醒窗口——量级正好是一分钟。
- 改动：
  - 拖动开始时把相邻两页的渲染内容**冻结**（`FrozenPages`），整段手势与动画期间都显示这份快照，
    数据切换不再影响屏上内容；归零与解冻同帧完成。
  - 测试通知补 `contentIntent`（打开应用首页）。
  - 提醒排期改用 `setAlarmClock`：系统把它当「用户自己设的闹钟」，Doze 不推迟、待机也照常唤醒；
    被拒时依次退回精确闹钟、不精确闹钟，并留日志。代价是状态栏/锁屏会出现小闹钟图标，
    诊断页「精确闹钟」一行已注明。
- 验证：193 项单测 0 失败，lint 11 项基线。

## [2026-11-12] 日历翻页中间那段空白去掉：相邻页跟着手指一起走

- 现象（真机反馈）：滑动翻页时，两个界面之间有一段空白动画间隔，观感很差。
- 原因：翻页是「当前页滑出 → 换数据 → 新页从另一侧滑入」三段拼的，旧页推出去之后屏里没有任何内容，
  这段空白 = 全屏距离的滑出时间。再短的动画时长也掩盖不了一屏空白。
- 修法：相邻页与当前页**同时渲染**（`CalendarPresentation.strip` 一次给出 前后/当前 三页），
  滑到哪、哪页就已经在那里。换页不换位置——滑到位时新页正好停在正中，此时才切 nav，
  等新数据落位再把位移归零，归零前后画面完全静止。
- 顺带：到农历年表边界不再空翻一页，改为阻尼位移 + 回弹；只有当前页可交互。
- 测试：193 项全绿（新增 strip 3 项：相邻页内容、年视图按年翻、边界返回 null），lint 11 项基线。

## [2026-11-12] 日历可左右滑动翻页；日期缝隙不再穿透到首页卡片

- 现象（真机反馈）：
  1. 日历只能点头部箭头翻页，没有左右滑动；
  2. 在月视图点**日期数字之间的空白**，会打开某个首页事件的详情。
- 根因：
  1. 日历整页是 `HomeScreen` 里的覆盖层，但页面本身没有任何指针输入节点；格子之间留了 1dp 缝
     （`padding(1.dp)` 排在 `clickable` 之前，缝隙不属于任何一格），面板四周也有留白。
     这些位置命中不到可点击节点，触摸就穿透到下层首页的卡片上——正好点在卡片上就是「打开了首页记录」。
  2. 翻页只有头部箭头这一条路径，`detectHorizontalDragGestures` 从未接过。
- 改动：
  - 新增 `SwipePager`：跟手位移 + 松手按「甩动速度优先、位移其次」判定翻页；不翻页则回弹。
    翻页时先把当前页顺着手指推出、换页、再从反侧拉回正中，观感等同 Pager 而不需要渲染相邻两页。
    月视图翻月、年/列表视图翻年；回调经 `rememberUpdatedState` 取最新值（手势协程只启动一次）。
  - 判定逻辑抽成纯函数 `swipeDirection`（速度 600dp/s 或屏宽 18% 触发）。
  - 日历页铺一层只吃触摸的透明遮罩，彻底堵住穿透；`DayCell` 把 `clickable` 提到 `padding` 之前，
    让格子缝隙归入相邻日期，点缝隙也能选中该日，而不是落到空气里。
- 涉及文件：`ui/calendar/CalendarScreen.kt`、`docs/MANUAL-TEST.md`（F9/F10/F11）
- 验证：190 项单测 0 失败（新增 `CalendarSwipeTest` 5 项）、lint 11 项基线不变。

## [2026-11-12] 新建默认此刻，分类改名后表单自动跟随

- 现象（真机反馈）：
  1. 新建事件时时间滚轮默认停在当天 **09:00**，下午新建等于一保存就是过去的时刻；
  2. 从表单进「管理分类…」把当前选中的分类改名后回到表单，旧名和新名**同时**出现在选项里且旧名仍被选中，不手动点新名直接保存 → 旧名被写回事件，于是新旧两个分类长期并存。
- 根因：
  1. `EventFormState.time` 默认值写死 `LocalTime.of(9, 0)`，与「新建＝从现在开始」的直觉不符（9 点是**全天事件**的提醒锚点，两件事被混成了一件）。
  2. 表单状态由 `EventEditSheet` 自己持有（`remember(initial.id)`），父级只传初始值。分类改名只改了 DataStore 与 Room 里的事件，改不到面板内部那份 `form.category`；chip 行按「列表 + 当前值」渲染，旧名就以「孤儿分类」身份并排显示，保存时原样写回。
- 改动：
  - 新增 `EventFormState.newEvent(date, now)`：日期默认今天（日历里点的某天则用那天），时刻默认**此刻**并把秒/纳秒截断到分钟；FAB 与日历内新建都走它。`time` 的默认值同步改为「当前分钟」，避免任何漏改的构造点又回到 9 点。
  - `CategorySheet` 增加 `onRenamed` / `onRemoved` 回调；`HomeScreen` 把「旧名 → 新名」记成 alias 映射传给 `EventEditSheet`，面板用 `LaunchedEffect` 把选中项沿映射解到终点（支持 A→B→C 链式改名），只改与旧名相同的值，不覆盖用户后来手动点的分类。
  - 删除分类同理：有迁移目标就跟到目标，没有就退回第一个仍存在的分类，别把已删的名字原样保存回去。
  - 每次打开新表单清空 alias，避免上一个表单里的改名影响新表单。
- 涉及文件：`ui/event/EventFormState.kt`、`ui/event/EventEditSheet.kt`、`ui/category/CategorySheet.kt`、`ui/home/HomeScreen.kt`、`docs/MANUAL-TEST.md`（B10、E2b、E3b）
- 验证：185 项单测 0 失败（新增 `newEvent` 默认时刻 2 项、`withCategoryAlias` 链式改名 1 项）、lint 11 项基线不变。

## [2026-11-11] 撤销条重开就没有：不再和系统回收赛跑，重开就给满一个窗口

- 现象（真机反馈，第三次）：划掉最近任务再重开，撤销条依然不出现。
- 根因（前两版错在哪）：一直在试图「精确记录离开时的剩余时间」。但真机上用户说的「退出后台」是**划掉最近任务＝进程终止**，进程一死，内存里的冻结时刻就没了；而 `restore()` 仍按墙钟 `expiresAtMillis` 判过期，超过 6 秒即 `clear()`——**连落盘那条也一起删了**。于是第一次重开失败之后，盘上已经干净，后面怎么试都不会再出现。这条路根本不该靠「记录离开时刻」来救。
- 改动（三重保障，不再依赖任何一次时序成功）：
  - **到期只清内存，不删落盘**：撤销条消失后，那条记录仍在 DataStore 里等一次重开机会。
  - **重开给满窗口**：`restore()` 不再比对 `expiresAt`，改看新增的 `pushedAtMillis`（首次记录时刻）——10 分钟内重开就重置成完整 6 秒。用户对「进程何时被杀」没有控制力，不该为此丢撤销。
  - **恢复提前**：`undoRepository.restore()` 从「自愈链让路 1.5 秒之后」提到 `onCreate` 即时执行，否则开局就先吃掉四分之一窗口。
  - 回到前台仍按剩余量续期（前台只消耗可见时间），这条逻辑不变。
- 涉及文件：`data/undo/UndoRepository.kt`、`data/undo/UndoCodec.kt`、`domain/model/UndoOperation.kt`、`MorrowApp.kt`、`MainActivity.kt`、`docs/MANUAL-TEST.md`（D8/D9）
- 验证：182 项单测 0 失败（`UndoWindowTest` 7 项，含「前台过期后划掉重开仍能撤销」「十分钟后不再复活」）、lint 11 项基线不变。
- 注意：旧包可能已经把待撤销项删掉了，装新包后请重新做一次删除再测 D8。

## [2026-11-11] 撤销条跨进程失效：冻结时刻落盘，划掉卡片重开也能接着撤销

- 现象（真机反馈）：上一版「前台计时」上线后复验——切后台 6 秒内回来能看见撤销条，超过 6 秒回来就没了。
- 判断：内存冻结只在「进程还活着」时成立。用户实际的「退出后台」是**从最近任务上滑划掉应用**（进程终止），重开走的是冷启动 + `restore()`，而 `restore()` 里判过期用的是墙钟 `expiresAtMillis`——冻结时刻只存在内存里，随进程一起消失了。
- 改动：
  - 冻结时刻落盘到 `undo.paused_at_v1`（与撤销项分开存，`UndoOperation` 不必感知它），`pauseWindow()` 改 `suspend` 并在 `onStop` 里通过 `lifecycleScope` 调用。
  - `restore()` 读到冻结时刻就把「冻结 → 进程死亡」这段时长补回到期时刻并重新落盘，等价于跨进程延续前台计时。
  - 冻结上限 10 分钟：允许「切出去看一眼」「划掉马上重开」，但隔夜回来还弹一条 4 秒的撤销只会像 bug。
- 涉及文件：`data/undo/UndoRepository.kt`、`MainActivity.kt`、`docs/MANUAL-TEST.md`（新增 D8）
- 验证：181 项单测 0 失败（`UndoWindowTest` 扩到 6 项，含「后台被杀后重开仍剩 4 秒」「超过十分钟不再恢复」）、lint 11 项基线不变。

## [2026-11-11] 撤销条一离开前台就没了：窗口改为「前台计时」

- 现象（真机反馈）：删除事件后切到别的应用（或按 Home），超过 6 秒回来撤销条已经消失，用户根本没机会点；数据已落库，只能去重录。
- 根因：窗口是**墙钟** 6 秒。`UndoRepository.scheduleExpiry` 的 `delay` 挂在应用级 scope 上，进程在后台照样倒数到点并清空 `pending`；UI 侧只是按 `expiresAtMillis` 复算，回到前台时读到的已经是「已过期」。整个链路里没有任何一处区分前台与后台。
- 改动：
  - `UndoRepository` 新增 `pauseWindow()` / `resumeWindow()`：离开前台时取消到期任务并记下时刻，回来时把冻结时长加回 `expiresAtMillis`、重新排期并重新落盘。窗口算的是「前台可见时间」，后台那段时间不计入 6 秒。
  - `MainActivity` 在 `onStop` 冻结、`onStart` 恢复。旋转屏、权限弹窗、跳系统设置往返都会走这条路径，一律不吞窗口。
  - 冻结状态刻意不落盘：进程若在后台被杀，重启后 `restore()` 仍按墙钟判过期——那种情况可用时间只剩几百毫秒，不值得为它多存一份状态。
- 涉及文件：`data/undo/UndoRepository.kt`、`MainActivity.kt`、`ui/event/UndoBar.kt`（注释）、`docs/MANUAL-TEST.md`（新增 D6/D7）
- 验证：179 项单测 0 失败（新增 `UndoWindowTest` 4 项，含「后台一分钟回来仍剩 4 秒」）、lint 11 项基线不变。
- 说明：后台冻结对「切出去看一眼再回来」是刚需；若后台待很久（几小时）再回来，撤销条会按冻结时的剩余秒数继续——数据层是快照回填，不会因此产生错误数据。

## [2026-11-11] 提醒仍晚约 20 秒：通知不等数据库，自愈链让路，延迟拆成两段

- 现象（真机）：补 `USE_EXACT_ALARM` 后从「晚 4 分钟」降到「晚约 20 秒」，仍未准点。
- 判断：4 分钟 → 20 秒说明精确闹钟已生效，剩下的是另一层耗时。可能来源有两个，且都在应用侧可控——① 接收器冷启动时先 `findById` 查 Room，等于让通知等数据库；② `MorrowApp.onCreate` 里的自愈链（`syncAll` 多次 AlarmManager 调用 + `refreshAll` 的 RemoteViews Binder 往返）与通知投递同时起跑，争抢 CPU/IO。
- 改动：
  - **通知不再依赖数据库**：`Title` / `isAllDay` 随 Intent 带给 `ReminderReceiver`，`ReminderNotifier` 新增不接 `CountdownEvent` 的重载。投递在前、查库在后，数据库只用于「已完成则撤销 / 重复事件重排」的对账。旧排期（Intent 里没有标题）自动回退到查库路径。
  - **自愈链让路**：`warmStart()` 延后 1.5 秒启动，给闹钟广播拉起的进程腾出资源。自愈本身是纯后台维护，晚 1.5 秒无可见影响。
  - **延迟拆成两段**：新增 `LAST_REMINDER_SHOWN_AT`，与 `LAST_REMINDER_FIRED_AT`、`LAST_REMINDER_TRIGGER_AT` 一起算出「系统延迟」与「应用处理耗时」，诊断页分别显示。应用耗时超过 1 秒才单独提示——那才是代码问题，其余是系统问题。
  - **新增「后台运行」状态行**：`PowerManager.isIgnoringBatteryOptimizations` + 跳系统电池优化列表。国产 ROM 的后台冻结会让进程延迟收广播，即便精确闹钟权限齐备也会晚几秒到几十秒，这一项必须和「精确闹钟」分开显示。
- 涉及文件：`reminder/{ReminderReceiver,ReminderNotifier}.kt`、`data/reminder/{AlarmReminderScheduler,ReminderPermissions}.kt`、`data/settings/LegacySettingsKeys.kt`、`ui/reminder/{ReminderViewModel,ReminderUiState,ReminderSheet}.kt`、`MorrowApp.kt`、`MainActivity.kt`
- 验证：175 项单测 0 失败、lint 11 项基线。

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
- 涉及文件：`data/settings/SettingsRepository.kt`、`widget/WidgetStyle.kt`、`widget/WidgetRefresher.kt`、`ui/event/EventViewModel.kt`、`app/src/test/java/com/morrowviento/morrow/data/settings/SettingsTypeSafetyTest.kt`（新建 5 例）
- 验证：全量单测 0 失败（新增 5 例：布尔/数字旧值读取不抛、缺键保持默认、`legacyDefaults()` 完整复现路径、无旧键时不崩）；lint 仍为基线

## [2026-11-11] 启动闪退结案：冷启动自愈链缺兜底

- 结论：m6 的「打开即闪退」根因是 `MorrowApp` 冷启动自愈链**没有任何异常兜底**。迁移 / 撤销恢复 / 提醒对账 / 小部件刷新整段塞在一个裸 `appScope.launch {}` 里，协程内未捕获异常直达系统默认 `UncaughtExceptionHandler` 直接杀进程——所以是「一闪即退、看不到任何画面」，而不是界面画到一半才挂。诊断包（把四步逐步 `runCatching` 隔离）装上后**用户反馈不再闪退**，与推断一致。这也解释了为何 JVM 侧测不出来：Robolectric 下 `Dispatchers.IO` 的调度时序与真机不同，异常没在同样的时机被抛出。
- 改动：`util/StartupTrace` 收敛为**仅 debug 包生效**——落盘与崩溃拦截都走 `ApplicationInfo.FLAG_DEBUGGABLE` 判定（不依赖 buildConfig 开关）；正式包只写 logcat、不接管 `UncaughtExceptionHandler`（接管会挡住系统与商店的崩溃上报）。启动轨迹与 `warmStart`/`step` 的逐步隔离保留，这是真修复。
- 涉及文件：`util/StartupTrace.kt`、`MorrowApp.kt`
- 验证：全量单测 0 失败；`assembleDebug` 通过
- 状态：闪退已解除。**被吞掉的异常本身尚未归因**——若能在装过诊断包的那台机器上取到 `Android/media/com.morrowviento.morrow/startup.log`，看哪一步标了 `: XXXException`，即可确认是哪个自愈步骤在这台机器上失败（大概率是旧数据迁移或提醒对账），再决定要不要单独修。

## [2026-11-11] 诊断：m6 安装后启动即退（真机 Android 16）

- 现象：`morrow-4.0.0-alpha1-debug-m6.apk` 在同一台 Android 16 机器上点开**一闪即退、看不到任何画面**；同机 `debug-m5.apk` 正常。故问题由 M6 引入，且落在启动早期。
- 本地复现结论：**代码逻辑层无问题，无法在 JVM 侧复现**。新增 `StartupSmokeTest`（Robolectric 4.16）把 `MorrowApp.onCreate` 与 `MainActivity` 拉到 `onResume`，并强制 `decorView.measure/layout` 走完首帧测量（上一轮「点 + 号闪退」正是测量期异常，只到 onResume 是测不到的），三例在 **API 33 与 34** 下全通过。另用 `javap` 核实两处外部假设：①`GlasmioTheme` 默认 `appearance` 即 `GlassAppearance()`（构造 mask=7 全默认），M6 显式传入的 `GlassAppearance(reduceTransparency = false)` 与之逐字段相等，**GlassTheme 改造可排除**；②`GlassAppearance` 默认值为 `liquidGlassEnabled=true / reduceTransparency=false / adaptiveColorScheme=false`。`preferencesDataStore` 全项目仅一处声明，无「同一文件多实例」陷阱；`Glasmio` 的 `libglasmio_liquid_menu_sdf.so` 只被 `LiquidMenuNativeField` 加载，不在启动路径上。
- 改动：新增 `util/StartupTrace`（**临时诊断设施，M7 发布前移除**）——`mark()` 把启动每一步追加进 `startup.log` 并立即落盘，即使崩溃是 native 层（SIGSEGV 不被 Java handler 捕获）也能看出卡在哪一步；`install()` 挂 `UncaughtExceptionHandler`，把 Java 堆栈连同末 40 行启动轨迹、设备与版本信息写成 `crash-*.txt`。落盘在 `Android/media/<包名>/`，Android 11+ 对该目录免权限且任何文件管理器可见，用户取日志不需要电脑或 root。`MorrowApp` 的启动自愈链拆成 `warmStart()` + `step()`：迁移 / 撤销恢复 / 提醒对账 / 小部件刷新逐步 `runCatching` 隔离——此前整段 `appScope.launch` 无任何兜底，其中任一步抛异常都会直达默认处理器**杀掉整个进程**，与「打开即闪退」的现象完全吻合。
- 涉及文件：`util/StartupTrace.kt`（新建）、`MorrowApp.kt`、`MainActivity.kt`、`app/src/test/java/com/morrowviento/morrow/StartupSmokeTest.kt`（新建）、`app/build.gradle.kts`（`testImplementation(libs.robolectric)`，版本目录里本就预留 4.16）
- 验证：全量单测 0 失败（新增 3 例启动冒烟）；编译仅 1 条 `getExternalMediaDirs` 废弃警告（API 37 标注，行为不受影响）
- 状态：**等用户取回 `startup.log` / `crash-*.txt` 再定根因**。若日志停在 `main setContent returned` 之后，则锁定首帧绘制（渲染层）；若停在更早的位置，按最后一条 mark 逐步收窄。

## [2026-11-11] M6 设置中心与备份

- 改动：新增 `ui/settings/`——`SettingsScreen`（整页覆盖，自带 `GlassBackdrop` 与 `BackHandler`；外观 / 数据 / 提醒 / 关于四组玻璃卡片，全部由一个 LazyColumn 承载）+ `SettingsViewModel`（持久设置走 DataStore Flow、瞬时状态走本地 StateFlow，两者 combine 成一个 UI 状态）+ `SettingsUiState`/`SettingsActions`（含 `ImportReport.summaryText` 文案）+ 自绘 `GlassSwitch`（跟随 `reduceMotion` 用 `snap()` 关动画）；`GlassTheme` 新增 `darkTheme`/`reduceTransparency`/`reduceMotion` 三参数并暴露 `LocalGlassDark`/`LocalReduceMotion`，`GlassBackdrop` 与 `glassTextPrimary/Secondary` 改为读 `LocalGlassDark`（此前只认系统深浅，用户强制浅色会底色与文字分裂）；新增 `data/settings/AppSettings`（`ThemeMode` + `isDark(systemDark)` 纯函数）；新增 `data/backup/`——`BackupDocument`/`BackupCodec`（逐字段容错、时间双格式、坏条目跳过计数、版本过高拒绝）+ `BackupMapper`（领域 ↔ 可读文本）+ `BackupMerge`（按 id 合并）+ `BackupRepository`（导出全量 / 导入 REPLACE|MERGE / 清空全部）+ `BackupFiles`（SAF 读写 + 建议文件名）；`WidgetConfigStore` 增 `widget.default_style` 作为「widgetId 查不到」时的兜底。
- 涉及文件：`data/settings/{AppSettings.kt（新建）,SettingsRepository.kt}`、`data/backup/{BackupDocument,BackupRepository,BackupFiles}.kt`（新建）、`ui/settings/{SettingsScreen,SettingsViewModel,SettingsUiState}.kt`（新建）、`ui/glass/GlassTheme.kt`、`ui/home/HomeScreen.kt`（更多菜单加「设置」+ 整页覆盖 + 主题下传）、`MainActivity.kt`、`MorrowApp.kt`、`widget/WidgetConfigStore.kt`、`app/src/test/java/com/morrowviento/morrow/data/backup/{BackupCodecTest,BackupMergeTest}.kt`（新建）
- 决策：①**主题与减透明走 Glasmio 原生参数**（`GlasmioTheme(isDark = …)` + `GlassAppearance(reduceTransparency = …)`），不在外面叠遮罩——遮罩挡得住背景挡不住折射与高光，等于假开关；②**设置行自绘，不用 `glasmio-preference`**：其 `GlasmioListPreference` 选中要弹二级对话框，而主题只有三个选项，横排 chips 少一次点击；且它的行组件与现有 `SectionTitle`/`ChoiceChip` 是两套间距与字号，混用会看出接缝；③**备份存 ISO-8601 本地时刻字符串**而不是毫秒：与 Flutter 版 `toIso8601String()` 同语义、用户能直接打开看，解码时兼容毫秒（`parseTime` 两种都收）；④**导入分「合并 / 覆盖」两档**：合并只碰事件与分类（外观与本机小部件样式不动，因为用户通常只想补另一台设备的事件），覆盖才整套替换；⑤**小部件样式按 widgetId 存，靠 `widget.default_style` 兜底换机场景**——widgetId 由桌面宿主分配，换机必变，只存按 id 的配置等于换机后每加一个都要重设。
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；**167 项单测 0 失败**（新增 25：编解码往返/容错/时间双格式 17 + 合并与设置模型 8）；lint 11 项与基线完全一致（过程中新引入的 `UseOfNonLambdaOffsetOverload` 已改掉）；debug APK 76 MB（零新依赖）
- 状态：代码完成，**待真机验收**——导出到云盘/本地、导入两种模式的数据落点、清空后提醒与小部件是否同步清干净、浅色/深色/跟随系统三态切换是否连底色与文字一起变、减弱透明在真实玻璃上的观感

## [2026-11-11] M5 桌面小部件

- 改动：新增 `data/widget/WidgetSnapshot`（纯函数：未完成事件 → 小部件行，置顶优先 → 未来按临近升序 → 已过排队尾，徽章「今天 / N 天 / 已过 N」与首页同规则，进度百分比只在非重复事件上算）+ `WidgetRefreshTrigger`（接口，给 ViewModel 用，避免 ui 层依赖 widget 包）；新增 `widget/` 包——`WidgetStyle`（预设 1/3/5 行 × 字号三档 × 配色四套（跟随系统/深色/浅色/品牌色）× 分类/日期/进度三开关，`colors()` 按 `uiMode` 在 ARGB 层判明暗）+ `WidgetStyleCodec`（逐字段容错解码 + 旧键尽力映射）+ `WidgetConfigStore`（`widget.<id>.style` 单键 JSON，复用 `morrow_settings` 同一个 DataStore）+ `WidgetRefresher`（**唯一刷新入口**：一次性查 Room → 拼 RemoteViews → 统一套背景/文字色/字号/显隐/进度 tint，两个小部件共用）+ `MorrowWidgetProvider` / `MorrowDetailWidgetProvider`（onUpdate / onAppWidgetOptionsChanged / onDeleted 清配置 / onEnabled 排闹钟）+ `MidnightRefreshScheduler` + `WidgetMidnightReceiver`（本地零点刷一次并排下一次；有精确权限走 `setExactAndAllowWhileIdle`，否则 `setAndAllowWhileIdle`）+ `WidgetRefreshRunner`（`goAsync` + IO 协程，超时必 `finish`）+ `WidgetConfigActivity` / `DetailWidgetConfigActivity`（玻璃风配置台：实时预览 + 预设/字号/配色/显隐 + 单事件绑定，整页 LazyColumn 承载）；资源新增 `widget_overview.xml`（5 组写死行）、`widget_detail.xml`、两个 appwidget-provider、三套圆角渐变底、`WidgetText` 文字样式。
- 涉及文件：`data/widget/{WidgetSnapshot,WidgetRefreshTrigger}.kt`（新建）、`widget/{WidgetStyle,WidgetConfigStore,WidgetRefresher,WidgetRefreshRunner,MorrowWidgetProvider,MidnightRefreshScheduler,WidgetMidnightReceiver,WidgetConfigActivity}.kt`（新建）、`res/layout/widget_{overview,detail}.xml`（新建）、`res/xml/widget_{overview,detail}_info.xml`（新建）、`res/drawable/widget_bg_{dark,light,accent}.xml`（新建）、`res/values/{colors,widget_styles}.xml`（新建）、`res/values/strings.xml`、`AndroidManifest.xml`、`MorrowApp.kt`、`MainActivity.kt`、`ui/event/EventViewModel.kt`、`reminder/ReminderBootReceiver.kt`、`data/settings/SettingsRepository.kt`
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

- 改动：新增 `ReminderPlan`（纯函数排期：锚点 = 全天 09:00 / 否则目标时刻，每条提醒 = 锚点 − 提前分钟；年度重复按当次周年推导，单事件最多两次落点 × 5 条提醒 = 10 个闹钟上限，超过上限由「触发后立即重排」自转）；`ReminderScheduler` 接口 + `AlarmReminderScheduler`（槽位模型：`requestCode` = 槽位下标、`Intent.data` 带事件 id，因此取消只需扫 0..9、事件之间也不会互相覆盖；有精确闹钟权限走 `setExactAndAllowWhileIdle`，没有则退化 `setAndAllowWhileIdle`）；`ReminderReceiver`（触发投递 / 通知「完成」/「稍后 10 分钟」三 action 共用，`goAsync` + IO 读库避免 ANR，触发后立即 `sync` 补下一次落点）；`ReminderBootReceiver`（`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` 全量对账）；`ReminderNotifier`（`reminders` 高优先级渠道、通知日期取**当次落点**而非首次目标、通知里自带 self-check 权限守卫）；`ReminderPermissions`（通知 / 精确闹钟状态探测与设置页跳转，ROM 裁掉设置页时回落应用详情页）；`ui/reminder/`（诊断页：权限两行状态 + 排期列表 + 发测试通知 + 手动重排期，回前台自动重读权限）；`EventViewModel` 在保存/删除/完成/批量清理/撤销后同步闹钟；`MorrowApp` 冷启动全量对账；通知点击经 `singleTop` + `StateFlow` 直达详情页。
- 涉及文件：`domain/time/ReminderPlan.kt`（新建）、`data/reminder/{ReminderScheduler,AlarmReminderScheduler,ReminderPermissions}.kt`（新建）、`reminder/{ReminderNotifier,ReminderReceiver,ReminderBootReceiver}.kt`（新建）、`ui/reminder/{ReminderUiState,ReminderViewModel,ReminderSheet}.kt`（新建）、`res/drawable/ic_notification.xml`（新建）、`AndroidManifest.xml`、`MorrowApp.kt`、`MainActivity.kt`、`ui/event/EventViewModel.kt`、`ui/home/HomeScreen.kt`、`res/values/strings.xml`
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
- 涉及文件：`domain/model/{UndoOperation,CountdownEvent}.kt`、`data/undo/**`、`data/{EventRepository,db/EventDao}.kt`、`ui/event/{EventDetailScreen,UndoBar,EventActions,EventViewModel,HomeUiState}.kt`、`ui/home/{HomeScreen,EventCardView}.kt`、`MainActivity.kt`、`MorrowApp.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；72 项单测 0 失败（新增 10 项：编解码 7 + 时间窗口 3）；lint 无新增问题；debug APK 75 MB。撤销条的浮层叠放与跨进程恢复待真机验证
- 状态：代码完成，待真机验收

## [2026-11-11] M2b2 分类管理

- 改动：分类改为「名字即身份」——事件表继续存 `category` 字符串，重命名/删除迁移通过 SQL 级联更新事件行，避免为展示性字段升 Room schema；新增 `EventCategory`（预置 7 类 + 12 字上限）、`CategoryRules`（纯函数：归一化/合并孤儿/增删改校验/至少保留一项）、`CategoryCodec`（DataStore 存 JSON 数组）、`CategoryRepository`（合并「已保存 + 旧事件孤儿」，删除被占用分类强制选迁移目标）；Room 增 `observeCategoryUsage` / `countByCategory` / `renameCategory`；新增分类管理浮层 `CategorySheet`（列表、行内重命名、删除迁移确认）；`EventEditSheet` 分类 chips 改读仓库并加「管理分类…」入口；抽出 `ui/component/GlassControls.kt` 供两个浮层复用；删除 `EventFormState.DEFAULT_CATEGORIES`。
- 涉及文件：`domain/model/EventCategory.kt`、`data/category/**`、`data/db/{CategoryUsage,EventDao}.kt`、`data/EventRepository.kt`、`ui/category/**`、`ui/component/GlassControls.kt`、`ui/home/HomeScreen.kt`、`ui/event/EventEditSheet.kt`、`MainActivity.kt`、`MorrowApp.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；62 项单测 0 失败（新增 21 项：分类规则 15 + 编解码 6）；lint 无新增问题；debug APK 75 MB。分类浮层视觉与交互待真机验收
- 状态：代码完成，待真机验收

## [2026-11-11] T0 远程对齐与 M2a/M2b 入库

- 改动：新增 `.gitattributes`（`* text=auto eol=lf` + 二进制类型显式标注），消除 `core.autocrlf=true` 导致的 README 整文件换行符假差异；`git reset --mixed origin/main` 丢弃本地重复提交对象 `3e4333e`（内容已在远程，工作区零损失）；拆两次提交 `c02adc9`（chore 行尾统一）与 `548edad`（feat M2a+M2b+docs），`push -u origin main` 建立 upstream。
- 涉及文件：`.gitattributes`（新建）、`docs/TODO.md`、`docs/HANDOFF.md`
- 验证：本地 `assembleDebug + testDebugUnitTest + lintDebug` 通过，41 项单测 0 失败，debug APK 75 MB；CI run `42983525295`（commit `548edad`）全绿；`git status -sb` 为 `## main...origin/main`
- 状态：完成

## [2026-11-11] M2b 事件新建表单

- 改动：`EventFormState` 纯函数层（标题校验、落库字段映射、`safeDate` 日夹取、提醒增删/去重/上限 5、快捷日期 今天/明天/一周后/月底/年底、`fromEvent` 回填）；`WheelDateTimePicker`（年/月/日/时/分五列滚轮，`rememberSnapFlingBehavior` 吸附，全天隐藏时间列，换月自动夹取日期）；`EventEditSheet`（同页面浮层，不用 ModalBottomSheet 以保证 backdrop 采样不断；标题/滚轮/全天/方向/每年重复/快捷日期/分类 chips/提醒 chips/备注）；`EventViewModel.save`；首页 FAB 打开表单；`CalendarMath.dateClamped`。
- 涉及文件：`ui/event/{EventFormState,EventEditSheet,EventViewModel}.kt`、`ui/component/WheelPicker.kt`（新建）、`ui/home/HomeScreen.kt`、`domain/time/CalendarMath.kt`、`MainActivity.kt`、`app/src/test/java/com/morrowviento/morrow/ui/event/EventFormStateTest.kt`
- 验证：`assembleDebug + testDebugUnitTest + lintDebug` 通过；41 项单测 0 失败（新增 9 项）。滚轮吸附与视觉未真机校准
- 状态：代码完成，待真机验收；未提交（分类 CRUD 仍待 M2b2）

## [2026-11-11] M2a 首页事件列表

- 改动：新增 `SortMode`（映射旧 `distance/targetDate/createdAt`）；`HomeUiState` + `EventPresentation` 纯函数层（置顶优先、已过事件沉底且「刚过去」优先、每年重复按周年换算、进度环 0..1 夹取、今日剩余时分秒）；`EventViewModel`（Room Flow + 排序偏好 + 每秒时间源 combine，`WhileSubscribed(5s)` 停表）；`HomeScreen`（折叠大标题、排序 chip、进行中/已完成分区、空态/加载/错误态、玻璃悬浮新建）；`EventCardView`（天数徽章 + Canvas 进度环）；`ui/glass/` 增 `glassTextPrimary/glassTextSecondary` 与 `GlassCirclePanel`；`MainActivity` 接入 ViewModel。
- 涉及文件：`ui/event/{HomeUiState,EventViewModel}.kt`、`ui/home/{HomeScreen,EventCardView}.kt`、`domain/model/SortMode.kt`、`ui/glass/GlassTheme.kt`、`MainActivity.kt`、`app/src/test/java/com/morrowviento/morrow/ui/event/EventPresentationTest.kt`
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

- 改动：领域模型落地（`CountdownEvent` / `EventReminder` / `CountDirection` / `RepeatType`，字段与 Dart 版一一对应）；`CalendarMath`（LocalDate 差值，规避夏令时）与 `RepeatRule`（每年重复，闰日回落 2/28）；Room `events` 表 + `EventDao` + `EventMapper`；DataStore `morrow_settings` 沿用 Flutter 原始键名；`FlutterSharedPreferences` → Room/DataStore 幂等迁移器（`migration.legacy_v1_done`，单条损坏不阻断）；接入 KSP/Room 与 schema 导出。
- 涉及文件：`app/src/main/java/com/morrowviento/morrow/domain/**`、`data/**`、`app/src/test/java/com/morrowviento/morrow/**`、`app/schemas/**`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、`app/src/main/AndroidManifest.xml`
- 验证：本地 `assembleDebug + testDebugUnitTest` 通过（22 项 0 失败：日历 12 / 迁移 6 / 映射 4）
- 状态：完成（远程 CI 已跑通，但本地与远程 SHA 不同，见 HANDOFF 待确认项 1）

## [2026-11-11] M0 工程基建与 Glasmio 渲染验证

- 改动：Gradle 工程骨架（AGP 9.4.1 / Kotlin 2.4.21 内置 / Gradle 9.6.0 / compileSdk 37 / minSdk 33 / JDK 21）；`ui/glass/` 封装层（`GlassTheme` / `GlassBackdropHost` / `GlassPanel`，Glasmio 唯一入口）；M0 玻璃材质验证页；GitHub Actions 出 debug APK；移除 NDK/CMake（AAR 已内置四 ABI 预编译库）。
- 涉及文件：`settings.gradle.kts`、`build.gradle.kts`、`app/build.gradle.kts`、`gradle/**`、`app/src/main/java/com/morrowviento/morrow/{MainActivity.kt,ui/glass/GlassTheme.kt}`、`.github/workflows/android-ci.yml`、`README.md`
- 验证：本地 `assembleDebug + testDebugUnitTest + lintDebug` 通过，产出约 74 MB debug APK；CI 全绿
- 状态：完成

## [2026-11-11] 仓库与 CI 通道打通

- 改动：创建 `MorrowViento/morrow`；补齐 Token 的 Contents / Workflows / Actions 权限；CI 平台包名修正为 `platforms;android-37.0` 并固定 cmdline-tools；移除 AGP 9 已不允许的 `kotlin-android` 插件；按 Glasmio `v0.1.2` 标签源码对齐封装层签名。
- 涉及文件：`.github/workflows/android-ci.yml`、`build.gradle.kts`、`app/build.gradle.kts`、`gradle/libs.versions.toml`、`app/src/main/java/com/morrowviento/morrow/ui/glass/GlassTheme.kt`
- 验证：CI 五轮迭代后编译通过并产出 APK
- 状态：完成

## [2026-11-09] 重构方案与决策确认

- 改动：量化 Flutter 版现状（Dart 约 2.2–2.4 万行 / 102 文件，Android 原生小部件已是 Kotlin）；确认 Glasmio 与 Flutter 渲染层不兼容，定为 Kotlin 重写；拆出 M0–M7 八个里程碑与 10–14 周估算；确认决策 D1–D6。
- 涉及文件：上级目录 `ying-kotlin-refactor-plan.md`（方案文档，不在本仓库）
- 验证：无（方案阶段）
- 状态：完成

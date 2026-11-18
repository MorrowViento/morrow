# 朝夕（morrow）

Android 13+ 的倒计时 / 纪念日 / 待办应用，用 Kotlin + Jetpack Compose + [Glasmio](https://github.com/NEORUAA/Glasmio) 0.1.3 重写自
[ying](https://github.com/jiuxina/ying)（原 Flutter 版）。

这是一个**独立维护的 Kotlin 应用**，不是 Flutter 版的外壳或补丁：数据模型、界面、提醒与小部件都在本仓库实现，
不并行维护 Flutter 主线。

## 功能

| 模块 | 能力 |
|---|---|
| 事件 | 纪念日与待办两类；置顶、分类、搜索；完成 / 恢复 / 进入下一周期 |
| 重复 | 不重复、每月、每年；闰日按 2/28 回落，周期推进保留原日期锚点 |
| 日历 | 月视图与年历，农历副标题与农历日期选择 |
| 提醒 | AlarmManager + 通知，支持精确闹钟权限、开机与升级后重建 |
| 小部件 | 列表 / 概览等外观配置，画布与宿主同源，午夜自动刷新 |
| 备份 | 纯文本 JSON 导出 / 导入（覆盖或合并），可读入 ying 旧数据 |
| 外观 | 玻璃厚度、深浅主题、强调色、从背景取色、丝缎或自定义背景、减弱动画 |
| 字体 | 导入本地 TTF / OTF 作为界面字体，列表就地预览；另附几款 OFL 字体的下载入口 |
| 节日皮肤 | 春节、中秋等农历节日当天加一层节日色柔光与一行祝语，可关闭 |
| 更新 | 应用内检查 / 下载 / 安装，支持自建镜像前缀 |

## 技术形态

| 项 | 取值 |
|---|---|
| 最低系统 | Android 13（API 33），Glasmio 的运行时要求 |
| 语言 / UI | Kotlin · Jetpack Compose（Compose BOM 2026.09.00）· Glasmio 0.1.3 |
| 数据 | Room 2.8.5（导出 schema）+ DataStore Preferences 1.3.0 |
| 构建 | JDK 21 · compileSdk / targetSdk 37（build-tools 37.0.0）· Gradle 9.6.0 · AGP 9（内置 Kotlin） |
| 包名 | `com.morrowviento.morrow` |

> 不需要 NDK / CMake：Glasmio 的 AAR 已内置四个 ABI 的预编译 `libglasmio_liquid_menu_sdf.so`，
> 本工程没有原生源码。

## 构建

- CI（`.github/workflows/android-ci.yml`）执行 `assembleDebug` + `testDebugUnitTest` + `lintDebug`，
  产物为 debug APK。
- 本地：安装 JDK 21 与 Android SDK 后，在 `local.properties` 写入 `sdk.dir`，然后
  `gradle :app:assembleDebug`（仓库自带 wrapper；若无法访问 `services.gradle.org`，直接用本机
  Gradle 9.6.0）。
- 发布签名：`gradle :app:assembleRelease`。密钥通过 `MORROW_KEYSTORE*` 环境变量或
  `~/.gradle/gradle.properties` 的 `morrow.*` 注入，步骤见 [`docs/SIGNING.md`](docs/SIGNING.md)。
  **仓库里不存放任何 keystore 或密码。**
- 发布前必须通过许可完整性门槛：

  ```bash
  gradle :app:verifyRuntimeNoticeCompleteness
  ```

  它会在随包许可正文缺失时失败；失败即不可发行，不能以「构建成功」替代。

## Glasmio 封装约定

**所有 Glasmio 调用以 `v0.1.3` 标签源码为准，不是 main 分支。** 业务代码不得直接 import
`io.github.neoruaa.glasmio.*`，只经 `ui/glass/` 封装层调用；升级库时改动应收敛在该目录。

| 项 | 我们依赖的形态 |
|---|---|
| `GlasmioBackdropHost` | `(modifier, sampledContent, overlayContent)`；纯色底由调用方自己画 `Box` |
| `GlassSurface` | 默认取 `LocalGlassBackdrop`；0.1.3 起自带 `onClick` / `onLongClick` / `detailAction` 按压光学 |
| `InteractiveHighlight` | 0.1.3 的「液体光斑」，由 `ui/glass/` 统一封装 |
| 渲染门槛 | 按压高光走 `RuntimeShader`，**API 36 以下库内直接跳过**，封装层另加淡白色兜底 |

`com.kyant.capsule.ContinuousRoundedRectangle` 与 `com.kyant.shapes.Capsule` 同样只在封装层使用。
升级版本时先比对 `vX.Y.Z` 标签源码，再改 `gradle/libs.versions.toml`。

## 包结构

```
app/src/main/java/com/morrowviento/morrow/
  ui/glass/     # Glasmio 封装层（唯一允许 import Glasmio 的目录）
  ui/           # 页面：首页 / 日历 / 搜索 / 事件编辑 / 设置 / 更新 / 通用组件
  data/         # Room、DataStore、备份、分类、提醒、小部件配置、旧数据迁移
  domain/       # 事件模型与日历 / 重复 / 农历 / 提醒时刻计算
  reminder/     # AlarmManager 调度、通知与开机重建
  service/      # 应用内更新
  widget/       # 桌面小部件（Provider / 配置 / 渲染）
app/src/main/assets/licenses/   # 随包许可原文（见下）
```

## 许可

| 对象 | 许可 |
|---|---|
| 本仓库代码 | MIT，见 [`LICENSE`](LICENSE) |
| ying（原 Flutter 版） | MIT，Copyright (c) 2026 Gao Shuai，全文见 `assets/licenses/ying.txt` |
| Glasmio 0.1.3 | **Apache-2.0**（不是 MIT）；第三方声明与 Apple 素材排除项见 `assets/licenses/glasmio-third-party-notices.txt` |
| AndroidX / Jetpack Compose / Room / DataStore | Apache-2.0 |
| MaterialKolor 4.1.1 | MIT；内嵌 Material Color Utilities 部分为 Apache-2.0，两份正文分别随包 |
| DataStore external protobuf 1.3.0 | BSD-3-Clause；归档内正文缺版权行，随包另附上游全文 `assets/licenses/protobuf-bsd-3.txt` |

`app/build.gradle.kts` 的 `generateRuntimeNotices` 按**精确坐标与版本**审核 release 运行时依赖，
把每个组件实际随包的 LICENSE / NOTICE 原文收集到 `assets/licenses/runtime.txt`，并逐条登记坐标、
artifact 与来源。原文只按字节完全相同去重，不同来源不会合并；缺少正文的坐标必须补静态全文，
否则构建失败。`fallbackFiles()` 只在归档缺正文时补齐，`attributionFiles()` 则是**坐标命中即无条件
追加**——用于归档正文虽在、但漏掉了版权行这类「字节齐全、归属不齐」的情况（目前的 protobuf 即如此）。
设置页「开源许可」可离线阅读这些全文。

Glasmio 的 AAR 内含若干源自 Apple 系统资源的图像（Tapback 帧、音频与聆听图标）。它们**不受
Apache-2.0 覆盖**，本项目通过资源裁剪与构建后核验确保不随包分发；应用也不使用 SF Symbols、
Apple 字体或 Apple 壁纸。

**应用不内置任何第三方字体。**「字体」设置只接受用户自己导入的 TTF / OTF，文件复制到应用私有
目录、留在本机，既不随包分发也不写进备份；设置页里的推荐字体只给项目主页链接，由用户自行下载，
授权与署名义务以字体自身的许可为准。

## 包名说明

`applicationId` = `com.morrowviento.morrow`（Kotlin 包同名）。**这是 2026-11-12 品牌重塑后的新标识，
与 Flutter 版 `com.jiuxina.ying` 不是同一个应用**：旧「萤」无法覆盖升级到「朝夕」，需要先导出备份、
卸载旧版、安装新版后导入。

换来的是命名彻底干净（代码、资源、数据文件里不再出现旧前缀）。签名密钥与升级注意事项见
[`docs/SIGNING.md`](docs/SIGNING.md)。若要恢复「老用户无缝覆盖升级」，唯一做法是把 `applicationId`
改回旧值并沿用旧签名密钥——这不是改一个字符串就能完成的事。

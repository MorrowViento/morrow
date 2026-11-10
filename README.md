# ying-native

「萤」的 Kotlin 重构工程（Jetpack Compose + Glasmio 0.1.2）。

重构总计划见上一级目录《ying-kotlin-refactor-plan.md》。本仓库遵循以下既定决策：

| 决策 | 结论 |
|---|---|
| D1 最低系统 | Android 13（API 33），Glasmio 强制要求 |
| D2 重构策略 | 不并行维护 Flutter 主线，全力专注重构 |
| D3 Glasmio | 锁定 `io.github.neoruaa.glasmio:*:0.1.2`（Maven Central）；业务代码不得直接 import，仅经 `ui/glass/` 封装层调用 |
| D4 图标 | 不使用 SF Symbols（Apple 字体许可），使用备用图标方案 |
| D5 技术栈 | Compose + ViewModel + Room + DataStore + WorkManager |
| D6 构建链 | JDK 21 · Android SDK 37（platform android-37.0 / build-tools 37.0.0）· Gradle 9.6.0 |

> D6 修订说明：原计划要求 NDK 27.1 + CMake 3.22.1，实际**不需要**。Glasmio 的 AAR 已内置
> 四个 ABI 的预编译 `libglasmio_liquid_menu_sdf.so`，NDK 仅用于 Glasmio 自身构建该库；
> 本工程无原生源码。去掉后 CI 单次构建约减少 3–5 分钟，并消除一个失败点。

## 构建

- CI：GitHub Actions 自动出包（`.github/workflows/android-ci.yml`），执行
  `assembleDebug + testDebugUnitTest + lintDebug`，产物为 debug APK
- 本地：安装 JDK 21 与 Android SDK 后，在 `local.properties` 写入 `sdk.dir`，
  然后 `gradle :app:assembleDebug`
- Gradle wrapper 已随仓库提供；若本机无法访问 `services.gradle.org`，直接用本机安装的
  Gradle 9.6.0 执行即可（wrapper 只影响本地开发，CI 使用 `gradle/actions/setup-gradle` 拉取）

## Glasmio API 版本约定

**所有 Glasmio 调用以 `v0.1.2` 标签源码为准，不是 main 分支。** 两者存在已确认的差异：

| 项 | v0.1.2（发布版，我们使用） | main 分支（未发布） |
|---|---|---|
| `GlasmioBackdropHost` | 仅 `(modifier, sampledContent, overlayContent)` | 另有 `(sampledColor, modifier, overlayContent)` 便捷重载 |
| `GlassSurface` | 已提供，默认取 `LocalGlassBackdrop` | 同名 |

升级 Glasmio 时，先比对 `vX.Y.Z` 标签源码再改 `ui/glass/`。


## 包结构约定

```
app/src/main/java/com/jiuxina/ying/
  ui/glass/    # Glasmio 封装层（唯一允许 import Glasmio 的目录）
  ui/          # Compose 页面（M2+ 逐步填充）
  data/        # Room + DataStore（M1）
  domain/      # 事件/日历/重复规则业务逻辑（M1）
  service/     # 通知、更新、备份、字体、壁纸（M4/M6/M7）
  widget/      # 桌面小部件（M5，迁移自 Flutter 版 android/ 目录）
```

## 包名说明

`applicationId` 保持 `com.jiuxina.ying` 与 Flutter 版一致，确保正式发布时升级安装覆盖旧包。

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
| D6 构建链 | JDK 21 · Android SDK 37 · NDK 27.1.12297006 · CMake 3.22.1 · Gradle 9.6.0 |

## 构建

- 本地：安装 JDK 21 与 Android SDK（NDK/CMake 随 SDK Manager 安装），`local.properties` 填 `sdk.dir`
- CI：GitHub Actions 自动出包（`.github/workflows/android-ci.yml`），产物为 debug APK

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

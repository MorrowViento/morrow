# morrow

「朝夕」（Morrow）的 Kotlin 重构工程（Jetpack Compose + Glasmio 0.1.2）。

重构总计划见上一级目录《ying-kotlin-refactor-plan.md》（文件名沿用旧称呼，未随本次更名调整）。本仓库遵循以下既定决策：

| 决策 | 结论 |
|---|---|
| D1 最低系统 | Android 13（API 33），Glasmio 强制要求 |
| D2 重构策略 | 不并行维护 Flutter 主线，全力专注重构 |
| D3 Glasmio | 锁定 `io.github.neoruaa.glasmio:*:0.1.2`（Maven Central）；业务代码不得直接 import，仅经 `ui/glass/` 封装层调用 |
| D4 图标 | 自绘自适应图标「晨昏日轮」（矢量前景 + 渐变底 + Android 13 单色版）；不使用 SF Symbols（Apple 字体许可） |
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
- 发布签名：`gradle :app:assembleRelease`，密钥参数走 `MORROW_KEYSTORE*` 环境变量或
  `~/.gradle/gradle.properties` 的 `morrow.*`；生成与验证步骤见 [`docs/SIGNING.md`](docs/SIGNING.md)。
  **仓库里不存放任何 keystore 或密码。**

## Glasmio API 版本约定

**所有 Glasmio 调用以 `v0.1.2` 标签源码为准，不是 main 分支。** 两者存在已确认的差异：

| 项 | v0.1.2（发布版，我们使用） | main 分支（未发布） |
|---|---|---|
| `GlasmioBackdropHost` | 仅 `(modifier, sampledContent, overlayContent)` | 另有 `(sampledColor, modifier, overlayContent)` 便捷重载 |
| `GlassSurface` | 已提供，默认取 `LocalGlassBackdrop` | 同名 |

升级 Glasmio 时，先比对 `vX.Y.Z` 标签源码再改 `ui/glass/`。


## 包结构约定

```
app/src/main/java/com/morrowviento/morrow/
  ui/glass/    # Glasmio 封装层（唯一允许 import Glasmio 的目录）
  ui/          # Compose 页面（M2+ 逐步填充）
  data/        # Room + DataStore（M1）
    db/        # Room 实体 / DAO / 映射
    legacy/    # Flutter 旧数据迁移（解析 + 搬运）
    settings/  # DataStore 设置读写
  domain/      # 事件/日历/重复规则业务逻辑（M1）
  service/     # 通知、更新、备份、字体、壁纸（M4/M6/M7）
  widget/      # 桌面小部件（M5，迁移自 Flutter 版 android/ 目录）
```

## M1 数据层（已完成）

| 项 | 内容 |
|---|---|
| 领域模型 | `CountdownEvent` / `EventReminder` / `CountDirection` / `RepeatType`，与 Flutter 版字段一一对应 |
| 日历计算 | `CalendarMath`（LocalDate 差值，天然规避夏令时）、`RepeatRule`（每年重复，闰日回落 2/28） |
| 持久化 | Room `events` 表（时间存本地时区毫秒，提醒列表存 JSON），schema 导出至 `app/schemas/` |
| 设置 | DataStore `morrow_settings`，**键名沿用 Flutter 原始键名**，迁移后无需二次映射 |
| 旧数据迁移 | 读 `FlutterSharedPreferences`（`flutter.` 前缀）→ 事件入 Room、其余键入 DataStore；幂等（`migration.legacy_v1_done`），单条损坏不阻断 |
| 测试 | 22 项单元测试（日历/重复/提醒归一化/旧 JSON 解析/键值拆分/实体映射） |

迁移兼容点均对齐 Dart 侧 `fromJson` 兜底行为：`category` 缺省「生活」、未知枚举回落默认值、
无 `reminders` 时读旧字段 `reminderMinutes`、`targetDate` 无时区时按本地时刻解释（带 `Z` 也能解析）。

## 包名说明

`applicationId` = `com.morrowviento.morrow`（Kotlin 包同名）。**这是 2026-11-12 品牌重塑后的新标识，与 Flutter 版
`com.jiuxina.ying` 不再是同一个应用**：旧「萤」无法覆盖升级到「朝夕」，需要先导出备份再卸载、装新包后导入。

代价换来的是命名彻底干净（代码、资源、数据文件里不再出现旧前缀）。签名密钥与升级注意事项见
[`docs/SIGNING.md`](docs/SIGNING.md)；如果以后需要恢复「老用户无缝覆盖升级」，唯一做法是把 `applicationId`
改回旧值并沿用旧签名密钥，这不是改个字符串就能完成的事。

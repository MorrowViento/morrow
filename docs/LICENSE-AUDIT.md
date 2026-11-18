# 运行时依赖许可与版权归属审阅

> 2026-11-18。对象：4.1.1 / 70 实际发行 runtime classpath。本文件是**工程核对记录**，不是法律意见；它把「逐组件看一遍」落成可复核的表，剩余判断仍由人做。

## 结论摘要

- **109 个坐标**：107 个 Apache-2.0、1 个 BSD-3-Clause（`datastore-preferences-external-protobuf`）、1 个 MIT + MCU Apache-2.0 混合（`material-color-utilities-android`）。
- 正文载体 8 组，互不重复；三份 Apache 正文都含完整条款（`END OF TERMS AND CONDITIONS` + 第 9 条），不是摘要版。
- **35** 个坐标的归档里没有任何 LICENSE/NOTICE，由静态 `apache-2.0.txt` 补 §4(a) 副本义务；其余 **74** 个自带正文。
- §4(d) 只有一处需要转发：Glasmio 六个模块的 `THIRD_PARTY_NOTICES.md`，已按原字节随包；其余 103 个归档内无 NOTICE。
- **一个真实缺口**：protobuf 的 BSD-3 正文**没有版权行**，而 BSD-3 第 1 条要求保留版权声明（§4.1）。
- 审阅过程中发现并已修掉的清单缺陷：`androidx.core:core:1.18.0` 在解析结果里出现两次，清单因此多打一条完全相同的记录、artifact 计数虚高一个；加 `distinctBy(坐标, 文件, classifier)` 后为 109 条，`runtime.txt` 110,947 B / SHA-256 `4773AF4B609D49E7970EF0B40D37F8A3CAF421823E30301F256E1A9CA46948A4`（原 111,134 B）。
- 本轮不覆盖：源码文件头逐文件版权、R8 之后的类级残留、Apple 素材授权本身。

## 1. 口径与方法

- 数据源：`app/build/generated/runtimeNotices/licenses/runtime.txt`（由 `generateRuntimeNotices` 按实际发行 classpath 生成）。
- 独立复核：不采信生成器自述，按坐标回到 `~/.gradle/caches/modules-2/files-2.1` 重开归档，重算每个正文的 SHA-256 并重建「坐标 → 正文组」映射。109 个坐标全部定位成功，0 缺失。
- 正文完整性：Apache 组以 `END OF TERMS AND CONDITIONS` 与第 9 条是否齐备判断是否全文；MIT/BSD 组检查保留条款与免责声明。
- 覆盖范围仅「随包的许可文本」这一层：包内的许可资产、`runtime.txt` 的元数据、归档内文本三类事实互相对账。

## 2. 正文载体（8 组）

| SHA-256 前 12 | 字节 | 载体 | 覆盖坐标 | 版权/归属行 |
| --- | --- | --- | --- | --- |
| `809fa1ed2145` | 10,175 | 归档自带 Apache-2.0 | 67 | 协议本身无逐组件版权行（见 §3） |
| `1781b86746a6` | 10,775 | 静态 `apache-2.0.txt`（androidx.core 1.18.0 AAR 原文） | 0（补 35 个缺口） | 同上 |
| `cfc7749b96f6` | 11,358 | 归档自带 Apache-2.0（Glasmio 六模块，含 APPENDIX） | 6 | 同上 |
| `8741d7edb8ae` | 11,195 | 静态 `material-color-utilities.txt` | 1（MaterialKolor 的 MCU 部分） | `Copyright 2021 Google LLC` |
| `a01b712fbb30` | 1,434 | 归档自带 BSD-3-Clause（protobuf） | 1 | **缺失**，见 §4.1 |
| `d29ac877ee63` | 1,631 | 静态 `materialkolor.txt` | 1（MaterialKolor 本体） | `Copyright (c) 2025 Jordon de Hoog` |
| `e7327a8f2829` | 5,151 | 归档自带 `THIRD_PARTY_NOTICES.md` → `glasmio-third-party-notices.txt` | 6 | 逐项列明，含 Apple 图像声明 |
| `413d645c35f7` | 1,129 | 根 `LICENSE` → `morrow.txt` | 应用自身 | `Copyright (c) 2026 Morrow contributors` / `Gao Shuai (ying-derived portions)` |

## 3. Apache-2.0 的归属怎么落

Apache-2.0 正文里没有、也不要求逐组件版权行；义务是 §4(a) 给接收者一份协议副本、§4(d) 转发上游 NOTICE。因此本项目的落点是：

- §4(a)：三种 Apache 正文变体全部全文随包（10,175 / 10,775 / 11,358 B），根 `assets/licenses/apache-2.0.txt` 恒在，不依赖某个组件是否自带。
- §4(d)：**只有** Glasmio 六模块的 `THIRD_PARTY_NOTICES.md` 需要转发，其余 103 个坐标的归档内均无 NOTICE 文件（逐归档检查确认）。
- 组件清单由 `runtime.txt` 承担：每个坐标一行，带 artifact 名与 SHA-256，可追溯到具体归档。

这一节是「Apache 组件不必逐个抄版权行」的依据，也是它与 MIT/BSD 组件的差别——后两者**必须**有版权行才成立。

## 4. 例外逐项

### 4.1 protobuf：BSD-3 正文缺版权行（真实缺口，未修）

- 事实：`androidx.datastore:datastore-preferences-external-protobuf:1.3.0` 的 `META-INF/androidx/datastore/datastore-preferences-external-protobuf/LICENSE.txt`（1,434 B）**从「Redistribution and use…」直接开始**，全文无 `Copyright`、无 `Google`；随包正文照搬原字节，所以包里也没有。
- 风险：BSD-3-Clause 第 1 条要求「保留上述版权声明」，当前没有声明可保留。原样转发是「不改上游文件」的正确做法，但**不足以满足该条**——上游缺行不能顺延成我们的合规。
- 建议处置：新增一份静态归属文件（上游 protobuf 的 BSD-3 全文 + `Copyright 2008 Google Inc. All rights reserved.` + `Source:`/`License:` 头）挂到该坐标。这需要生成器支持「坐标已有归档正文时**追加**静态归属」，当前 `expectedTextGaps` 只补缺口、不做追加。改动会影响清单字节与 APK，随下一次发行（4.1.2 / 71）一起走。

### 4.2 Glasmio 与 Apple 原始图像

- Glasmio 0.1.3 是 **Apache-2.0**（不是 MIT）；六个模块各带 `THIRD_PARTY_NOTICES.md`，已按原字节随包。
- 该 NOTICES 明确：Tapback 与 listening-mode 的 10 项图像取自 iOS 27.0 模拟器，**Apple Inc. 保留版权，且不受 Apache 覆盖**。
- 已核验 4.1.1 / 70 包内图像条目为 0、资源表无 `tapback|listening|adaptive_audio`，即这些素材**不随包分发**；但 NOTICES 里的原图声明仍照原样转发，不做删改。
- 这不构成对这些图像的授权判断，也不代表可以据 Apache 声明使用它们。

### 4.3 MaterialKolor 4.1.1

- 发布声明是 **MIT**（`Copyright (c) 2025 Jordon de Hoog`），内嵌的 material-color-utilities 代码是 **Apache-2.0**（`Copyright 2021 Google LLC`）。两份正文都随包，缺一不可。

### 4.4 ying 派生部分

- 应用本体 MIT；`ying.txt` 保留上游 MIT（`Copyright (c) 2026 Gao Shuai`）；根 `LICENSE` 与 `morrow.txt` 用 `Copyright (c) 2026 Morrow contributors` + `Copyright (c) 2026 Gao Shuai (ying-derived portions)` 同时声明。
- 逐文件派生边界仍是人判断：哪些文件属于重写、哪些保留 ying 原结构，没有逐文件记录。

### 4.5 本仓库自身

- MIT，根 `LICENSE` 与 `morrow.txt` 一致（1,129 B，`413d645c…`）。

## 5. 逐坐标清单（109 项）

按坐标字典序；「正文载体」列说明该组件的许可文本从哪来，便于回查 `runtime.txt` 与归档。

| # | 坐标 | 协议标签 | 正文载体 |
| --- | --- | --- | --- |
| 1 | `androidx.activity:activity-compose:1.13.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 2 | `androidx.activity:activity-ktx:1.13.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 3 | `androidx.activity:activity:1.13.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 4 | `androidx.annotation:annotation-experimental:1.5.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 5 | `androidx.annotation:annotation-jvm:1.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 6 | `androidx.arch.core:core-common:2.2.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 7 | `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 8 | `androidx.autofill:autofill:1.0.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 9 | `androidx.collection:collection-jvm:1.6.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 10 | `androidx.collection:collection-ktx:1.6.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 11 | `androidx.compose.animation:animation-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 12 | `androidx.compose.animation:animation-core-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 13 | `androidx.compose.foundation:foundation-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 14 | `androidx.compose.foundation:foundation-layout-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 15 | `androidx.compose.material3:material3-android:1.4.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 16 | `androidx.compose.material3:material3-window-size-class-android:1.4.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 17 | `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 18 | `androidx.compose.material:material-icons-extended-android:1.7.8` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 19 | `androidx.compose.material:material-ripple-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 20 | `androidx.compose.runtime:runtime-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 21 | `androidx.compose.runtime:runtime-annotation-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 22 | `androidx.compose.runtime:runtime-retain-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 23 | `androidx.compose.runtime:runtime-saveable-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 24 | `androidx.compose.ui:ui-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 25 | `androidx.compose.ui:ui-geometry-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 26 | `androidx.compose.ui:ui-graphics-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 27 | `androidx.compose.ui:ui-text-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 28 | `androidx.compose.ui:ui-unit-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 29 | `androidx.compose.ui:ui-util-android:1.12.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 30 | `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 31 | `androidx.core:core-ktx:1.18.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 32 | `androidx.core:core-viewtree:1.0.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 33 | `androidx.core:core:1.18.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 34 | `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 35 | `androidx.datastore:datastore-android:1.3.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 36 | `androidx.datastore:datastore-core-android:1.3.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 37 | `androidx.datastore:datastore-core-okio-jvm:1.3.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 38 | `androidx.datastore:datastore-preferences-android:1.3.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 39 | `androidx.datastore:datastore-preferences-core-android:1.3.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 40 | `androidx.datastore:datastore-preferences-external-protobuf:1.3.0` | BSD-3-Clause | 归档 BSD-3-Clause（1,434 B，**无版权行**） |
| 41 | `androidx.datastore:datastore-preferences-proto:1.3.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 42 | `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 43 | `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 44 | `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 45 | `androidx.lifecycle:lifecycle-common-java8:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 46 | `androidx.lifecycle:lifecycle-common-jvm:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 47 | `androidx.lifecycle:lifecycle-livedata-core:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 48 | `androidx.lifecycle:lifecycle-process:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 49 | `androidx.lifecycle:lifecycle-runtime-android:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 50 | `androidx.lifecycle:lifecycle-runtime-compose-android:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 51 | `androidx.lifecycle:lifecycle-runtime-ktx-android:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 52 | `androidx.lifecycle:lifecycle-viewmodel-android:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 53 | `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 54 | `androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 55 | `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.10.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 56 | `androidx.navigation3:navigation3-runtime-android:1.2.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 57 | `androidx.navigationevent:navigationevent-android:1.1.2` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 58 | `androidx.navigationevent:navigationevent-compose-android:1.1.2` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 59 | `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 60 | `androidx.room:room-common-jvm:2.8.5` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 61 | `androidx.room:room-ktx:2.8.5` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 62 | `androidx.room:room-runtime-android:2.8.5` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 63 | `androidx.savedstate:savedstate-android:1.4.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 64 | `androidx.savedstate:savedstate-compose-android:1.4.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 65 | `androidx.savedstate:savedstate-ktx:1.4.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 66 | `androidx.sqlite:sqlite-android:2.6.2` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 67 | `androidx.sqlite:sqlite-framework-android:2.6.2` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 68 | `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 69 | `androidx.tracing:tracing-android:2.0.1` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 70 | `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 71 | `androidx.window:window-core-android:1.5.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 72 | `androidx.window:window:1.5.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 73 | `com.google.code.gson:gson:2.11.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 74 | `com.google.errorprone:error_prone_annotations:2.27.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 75 | `com.google.guava:listenablefuture:1.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 76 | `com.materialkolor:material-color-utilities-android:4.1.1` | MIT（MIT + MCU Apache-2.0） | 静态 `apache-2.0.txt` 补充 |
| 77 | `com.squareup.okio:okio-jvm:3.17.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 78 | `dev.drewhamilton.poko:poko-annotations-jvm:0.21.1` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 79 | `io.github.kyant0:capsule:2.1.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 80 | `io.github.kyant0:shapes-android:1.2.1` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 81 | `io.github.neoruaa.glasmio:glasmio-core:0.1.3` | Apache-2.0 | 归档 Apache-2.0 全文（11,358 B，Glasmio） + 归档 THIRD_PARTY_NOTICES（5,151 B） |
| 82 | `io.github.neoruaa.glasmio:glasmio-glass:0.1.3` | Apache-2.0 | 归档 Apache-2.0 全文（11,358 B，Glasmio） + 归档 THIRD_PARTY_NOTICES（5,151 B） |
| 83 | `io.github.neoruaa.glasmio:glasmio-icons:0.1.3` | Apache-2.0 | 归档 Apache-2.0 全文（11,358 B，Glasmio） + 归档 THIRD_PARTY_NOTICES（5,151 B） |
| 84 | `io.github.neoruaa.glasmio:glasmio-preference:0.1.3` | Apache-2.0 | 归档 Apache-2.0 全文（11,358 B，Glasmio） + 归档 THIRD_PARTY_NOTICES（5,151 B） |
| 85 | `io.github.neoruaa.glasmio:glasmio-scaffold:0.1.3` | Apache-2.0 | 归档 Apache-2.0 全文（11,358 B，Glasmio） + 归档 THIRD_PARTY_NOTICES（5,151 B） |
| 86 | `io.github.neoruaa.glasmio:glasmio-ui:0.1.3` | Apache-2.0 | 归档 Apache-2.0 全文（11,358 B，Glasmio） + 归档 THIRD_PARTY_NOTICES（5,151 B） |
| 87 | `org.jetbrains.compose.runtime:runtime-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 88 | `org.jetbrains.compose.runtime:runtime-saveable-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 89 | `org.jetbrains.compose.ui:ui-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 90 | `org.jetbrains.compose.ui:ui-geometry-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 91 | `org.jetbrains.compose.ui:ui-graphics-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 92 | `org.jetbrains.compose.ui:ui-text-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 93 | `org.jetbrains.compose.ui:ui-unit-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 94 | `org.jetbrains.compose.ui:ui-util-android:1.12.0` | Apache-2.0 | 归档 Apache-2.0 全文（10,175 B） |
| 95 | `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 96 | `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 97 | `org.jetbrains.kotlin:kotlin-stdlib:2.4.21` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 98 | `org.jetbrains.kotlinx:atomicfu-jvm:0.28.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 99 | `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 100 | `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 101 | `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.7.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 102 | `org.jetbrains:annotations:23.0.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 103 | `org.jspecify:jspecify:1.0.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 104 | `sh.calvin.reorderable:reorderable-android:3.1.0` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 105 | `top.yukonga.miuix.kmp:miuix-core-android:0.9.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 106 | `top.yukonga.miuix.kmp:miuix-navigation3-ui-android:0.9.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 107 | `top.yukonga.miuix.kmp:miuix-shader-android:0.9.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 108 | `top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |
| 109 | `top.yukonga.miuix.kmp:miuix-ui-android:0.9.3` | Apache-2.0 | 静态 `apache-2.0.txt` 补充 |

## 6. 未完成 / 需要人判断

1. **protobuf 版权行**（§4.1）：需生成器支持追加静态归属，随 4.1.2 / 71 发行。
2. **源码文件头版权**：各依赖 `*.kt` / `*.java` 文件头的版权行未逐文件核对；R8 之后哪些类残留也没有逐类比对。本轮只覆盖「随包许可文本」。
3. **ying 派生边界**：逐文件归属仍需人工判定（§4.4）。
4. **Apple 素材**：只证明不分发，不涉及授权本身；`apache-2.0.txt` 也不覆盖它们。
5. 本文件不构成法律意见，也不因 `verifyRuntimeNoticeCompleteness` 退出 0 而自动成立——门槛只校验「每个需要文本的坐标都有文本」。

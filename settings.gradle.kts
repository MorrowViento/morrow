pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Glasmio 0.1.4-main 的 AAR 随仓库提供，见文件末尾注释
        maven {
            name = "glasmioVendor"
            url = uri("vendor/glasmio-maven")
            content { includeGroup("io.github.neoruaa.glasmio") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "morrow"
include(":app")

/*
 * Glasmio 用 main 源码，而不是 Maven 上停更的 0.1.3。
 *
 * 上游 main 积了 30 个提交（insets / 安全区、玻璃渲染回退、菜单快照、导航过渡修复等），
 * 但 VERSION_NAME 仍是 0.1.3、也没发新版。源码在 `vendor/glasmio`（git submodule，
 * 固定在 main 的 commit `030d31c`）。
 *
 * 构建时依赖的是 `vendor/glasmio-maven/` 里随仓库提交的 6 个模块 AAR（版本 `0.1.4-main`），
 * **不依赖 mavenLocal、也不需要 NDK/CMake**——上游 glass 模块带 C++ SDF 着色器，只有重新
 * 编译 Glasmio 时才需要原生工具链，使用方直接吃预编译 .so。
 *
 * 为什么不用 mavenLocal：AAR 只存在本机时，GitHub Actions 与任何干净检出都会报
 * `Could not find io.github.neoruaa.glasmio:glasmio-ui:0.1.4-main`（4.4.0 的 CI 就是这样挂的）。
 * 为什么不用 includeBuild：AGP 9 的 Android 库在 composite build 里不发布 `library` /
 * `androidJvm` 变体属性，依赖解析会报 variant ambiguity，构建直接失败。
 *
 * 要跟进上游新提交：`scripts/build-glasmio.sh`（拉 submodule → 发布 → 同步进 vendor/glasmio-maven）。
 * 细节见 docs/HANDOFF.md「Glasmio 源码同步」。
 */

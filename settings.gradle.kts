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
        // vendor/glasmio 以 0.1.4-main 发布到本机仓库，见文件末尾注释
        mavenLocal()
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
 * 固定在 main 的 commit），由 `scripts/build-glasmio.sh` 以 `0.1.4-main` 发布到本机
 * Maven 本地仓库，这里再从 `mavenLocal()` 依赖。
 *
 * 为什么不用 `includeBuild`：AGP 9 的 Android 库在 composite build 里不发布
 * `library` / `androidJvm` 变体属性，依赖解析会报 variant ambiguity，构建直接失败。
 * 先发布到 mavenLocal 是唯一稳定路径。同步步骤见 docs/HANDOFF.md「Glasmio 源码同步」。
 */

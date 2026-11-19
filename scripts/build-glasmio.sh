#!/usr/bin/env bash
# 把 vendor/glasmio（submodule，固定在 main 的某个 commit）发布到本机 Maven 仓库，
# 供 Morrow 以 io.github.neoruaa.glasmio:glasmio-*:0.1.4-main 依赖。
#
# 为什么不用 includeBuild：AGP 9 的 Android 库在 composite build 下不发布
# library / androidJvm 变体属性，依赖解析会报 variant ambiguity。
#
# 需要 NDK 27.1.12297006 + CMake 3.22.1（glasmio-glass 的 C++ SDF 着色器）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="${ROOT}/../.toolchain"
export JAVA_HOME="${JAVA_HOME:-${TOOLCHAIN}/jdk/jdk-21.0.13+9}"
export ANDROID_HOME="${ANDROID_HOME:-${TOOLCHAIN}/sdk}"
GRADLE="${TOOLCHAIN}/gradle-9.6.0/bin/gradle"
VERSION="${GLASMIO_VERSION:-0.1.4-main}"

# 版本号用命令行属性覆盖，不改 vendor 里的 gradle.properties（submodule 保持干净）
"${GRADLE}" -p "${ROOT}/vendor/glasmio" "-PVERSION_NAME=${VERSION}" --console=plain \
  :glasmio-core:publishToMavenLocal \
  :glasmio-glass:publishToMavenLocal \
  :glasmio-icons:publishToMavenLocal \
  :glasmio-ui:publishToMavenLocal \
  :glasmio-preference:publishToMavenLocal \
  :glasmio-scaffold:publishToMavenLocal

echo "glasmio ${VERSION} 已发布到 ~/.m2/repository"

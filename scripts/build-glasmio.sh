#!/usr/bin/env bash
# 重新编译 vendor/glasmio（submodule，固定在 main 的某个 commit），
# 并把 6 个模块的 AAR 同步进仓库内的 vendor/glasmio-maven，供 Morrow 以
# io.github.neoruaa.glasmio:glasmio-*:0.1.4-main 依赖。
#
# 为什么 AAR 要进仓库：只发布到 mavenLocal 的话，GitHub Actions 与任何
# 干净检出都解析不到依赖（4.4.0 的 CI 就是 `Could not find ...:glasmio-ui` 挂掉的）。
# 为什么不用 includeBuild：AGP 9 的 Android 库在 composite build 下不发布
# library / androidJvm 变体属性，依赖解析会报 variant ambiguity。
#
# 需要 NDK 27.1.12297006 + CMake 3.22.1（glasmio-glass 的 C++ SDF 着色器）。
# 只改上游 commit、不重新编译时，可直接跳到最后的同步步骤（见 docs/HANDOFF.md）。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN="${ROOT}/../.toolchain"
export JAVA_HOME="${JAVA_HOME:-${TOOLCHAIN}/jdk/jdk-21.0.13+9}"
export ANDROID_HOME="${ANDROID_HOME:-${TOOLCHAIN}/sdk}"
GRADLE="${TOOLCHAIN}/gradle-9.6.0/bin/gradle"
VERSION="${GLASMIO_VERSION:-0.1.4-main}"
MODULES="glasmio-core glasmio-glass glasmio-icons glasmio-ui glasmio-preference glasmio-scaffold"
M2="${HOME}/.m2/repository/io/github/neoruaa/glasmio"
VENDOR="${ROOT}/vendor/glasmio-maven/io/github/neoruaa/glasmio"

publish() {
  # 版本号用命令行属性覆盖，不改 vendor 里的 gradle.properties（submodule 保持干净）
  local args=()
  for m in ${MODULES}; do args+=(":${m}:publishToMavenLocal"); done
  "${GRADLE}" -p "${ROOT}/vendor/glasmio" "-PVERSION_NAME=${VERSION}" --console=plain "${args[@]}"
}

# 把 .aar / .pom / .module 同步进仓库（sources、javadoc 不进，省 4 MB）
sync_vendor() {
  for m in ${MODULES}; do
    local dst="${VENDOR}/${m}/${VERSION}"
    mkdir -p "${dst}"
    for ext in aar pom module; do
      cp "${M2}/${m}/${VERSION}/${m}-${VERSION}.${ext}" "${dst}/"
    done
  done
  echo "已同步到 vendor/glasmio-maven（${VERSION}），记得提交："
  echo "  git add vendor/glasmio-maven && git commit -m 'build: glasmio ${VERSION} 同步'"
}

case "${1:-build}" in
  build) publish; sync_vendor ;;
  sync)  sync_vendor ;;
  *)
    echo "用法: $0 [build|sync]" >&2
    echo "  build（默认）重新编译上游并同步；sync 只把 ~/.m2 里的产物同步进仓库" >&2
    exit 2
    ;;
esac

#!/usr/bin/env bash
set -euo pipefail
runout_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
runout_cargo="${CARGO:-$HOME/.cargo/bin/cargo}"
runout_rustup="${RUSTUP:-$HOME/.cargo/bin/rustup}"
runout_ndk="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$HOME/.local/share/runout-build/android-ndk-r27d}}"
runout_toolchain="$runout_ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
runout_output="${RUNOUT_JNI_OUTPUT:-$runout_root/app/build/generated/soulseekJni}"
if [[ ! -x "$runout_cargo" || ! -x "$runout_toolchain/aarch64-linux-android28-clang" ]]; then
    echo "Rust 1.91.0 and Android NDK r27d or newer are required. Set CARGO and ANDROID_NDK_HOME." >&2
    exit 1
fi
runout_targets=(aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android)
runout_abis=(arm64-v8a armeabi-v7a x86_64 x86)
runout_linkers=(aarch64-linux-android28-clang armv7a-linux-androideabi28-clang x86_64-linux-android28-clang i686-linux-android28-clang)
export CARGO_TARGET_DIR="$runout_root/native/target"
# Android 15+ devices can use 16 KiB pages. NDK r27 needs an explicit linker flag.
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384"
for runout_index in "${!runout_targets[@]}"; do
    runout_target="${runout_targets[$runout_index]}"
    "$runout_rustup" target add --toolchain 1.91.0 "$runout_target"
    runout_linker_key="CARGO_TARGET_${runout_target^^}_LINKER"
    runout_linker_key="${runout_linker_key//-/_}"
    env "$runout_linker_key=$runout_toolchain/${runout_linkers[$runout_index]}" \
        "$runout_cargo" +1.91.0 build --manifest-path "$runout_root/native/Cargo.toml" \
        --locked --release -p runout-soulseek --target "$runout_target"
    mkdir -p "$runout_output/${runout_abis[$runout_index]}"
    cp "$CARGO_TARGET_DIR/$runout_target/release/librunout_soulseek.so" "$runout_output/${runout_abis[$runout_index]}/"
done
echo "Soulseek libraries: $runout_output"

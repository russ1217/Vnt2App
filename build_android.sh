#!/bin/bash
set -e

echo "========================================="
echo "🔧 VNT2App Android 编译脚本"
echo "========================================="

# 设置代理 (用于访问 GitHub)
export http_proxy="http://172.16.0.4:3128"
export https_proxy="http://172.16.0.4:3128"
export HTTP_PROXY="http://172.16.0.4:3128"
export HTTPS_PROXY="http://172.16.0.4:3128"
export NO_PROXY="localhost,127.0.0.1"

# NDK 路径
export NDK="/home/russ/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64"
export PATH="$NDK/bin:$PATH"

echo "✅ 代理配置: $https_proxy"
echo "✅ NDK 路径: $NDK"
echo ""

cd rust

# 清理旧配置
rm -f .cargo/config .cargo/config.toml

# 创建 cargo 配置
echo "📝 配置 Cargo NDK 工具链..."
cat >> .cargo/config <<EOF
[target.armv7-linux-androideabi]
ar = "$NDK/bin/llvm-ar"
linker = "$NDK/bin/armv7a-linux-androideabi21-clang"

[target.aarch64-linux-android]
ar = "$NDK/bin/llvm-ar"
linker = "$NDK/bin/aarch64-linux-android21-clang"

[target.i686-linux-android]
ar = "$NDK/bin/llvm-ar"
linker = "$NDK/bin/i686-linux-android21-clang"

[target.x86_64-linux-android]
ar = "$NDK/bin/llvm-ar"
linker = "$NDK/bin/x86_64-linux-android21-clang"
EOF

echo "✅ Cargo 配置完成"
cat .cargo/config
echo ""

# 检查 jniLibs 目录
mkdir -p ../android/app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}

echo "========================================="
echo "🚀 开始编译 Android Rust 库"
echo "========================================="

# aarch64
echo ""
echo "🔨 [1/4] 构建 aarch64-linux-android..."
cargo build --target aarch64-linux-android --release
cp target/aarch64-linux-android/release/librust_lib_vnt_app.so ../android/app/src/main/jniLibs/arm64-v8a/librust_lib_vnt_app.so
echo "✅ aarch64 完成"
ls -lh ../android/app/src/main/jniLibs/arm64-v8a/librust_lib_vnt_app.so

cargo clean

# x86_64
echo ""
echo "🔨 [2/4] 构建 x86_64-linux-android..."
cargo build --target x86_64-linux-android --release
cp target/x86_64-linux-android/release/librust_lib_vnt_app.so ../android/app/src/main/jniLibs/x86_64/librust_lib_vnt_app.so
echo "✅ x86_64 完成"
ls -lh ../android/app/src/main/jniLibs/x86_64/librust_lib_vnt_app.so

cargo clean

# armv7
echo ""
echo "🔨 [3/4] 构建 armv7-linux-androideabi..."
export CC_armv7_linux_androideabi="$NDK/bin/armv7a-linux-androideabi21-clang"
export CC="$NDK/bin/armv7a-linux-androideabi21-clang"
cargo build --target armv7-linux-androideabi --release
cp target/armv7-linux-androideabi/release/librust_lib_vnt_app.so ../android/app/src/main/jniLibs/armeabi-v7a/librust_lib_vnt_app.so
echo "✅ armv7 完成"
ls -lh ../android/app/src/main/jniLibs/armeabi-v7a/librust_lib_vnt_app.so

cargo clean

# i686
echo ""
echo "🔨 [4/4] 构建 i686-linux-android..."
export CC_i686_linux_android="$NDK/bin/i686-linux-android21-clang"
export CC="$NDK/bin/i686-linux-android21-clang"
cargo build --target i686-linux-android --release
cp target/i686-linux-android/release/librust_lib_vnt_app.so ../android/app/src/main/jniLibs/x86/librust_lib_vnt_app.so
echo "✅ i686 完成"
ls -lh ../android/app/src/main/jniLibs/x86/librust_lib_vnt_app.so

echo ""
echo "========================================="
echo "🎉 所有 Android 架构编译完成!"
echo "========================================="
echo ""
echo "📦 jniLibs 文件列表:"
find ../android/app/src/main/jniLibs -name "*.so" -exec ls -lh {} \;

echo ""
echo "✅ Rust 库编译完成,可以继续执行 Flutter APK 打包"

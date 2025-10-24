#!/bin/bash

# MiniTB 启动脚本

echo "========================================="
echo "   编译并启动 MiniTB"
echo "========================================="

cd "$(dirname "$0")"

# 强制使用Java 17（避免Maven使用错误的Java版本）
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home

# 编译项目
echo "正在编译..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "编译失败！"
    exit 1
fi

echo ""
echo "编译成功！正在启动MiniTB..."
echo ""

# 运行程序
mvn exec:java -Dexec.mainClass="com.minitb.MiniTBApplication"





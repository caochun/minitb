#!/bin/bash

# MiniTB 启动脚本

echo "========================================="
echo "   编译并启动 MiniTB"
echo "========================================="

cd "$(dirname "$0")"

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


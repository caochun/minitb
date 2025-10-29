#!/bin/bash

# MiniTB 快速启动脚本

cd "$(dirname "$0")"

echo "╔════════════════════════════════════════════════════════╗"
echo "║              MiniTB 启动中...                           ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

echo "🔧 配置:"
echo "  • 数据库: SQLite (data/minitb.db)"
echo "  • Web 端口: 8080"
echo "  • MQTT 端口: 1883"
echo "  • Prometheus 拉取: 每 2 秒"
echo ""

echo "🚀 启动 MiniTB..."
echo ""

# 启动 Spring Boot 应用
mvn spring-boot:run



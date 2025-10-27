#!/bin/bash

# GPU 监控启动脚本

cd "$(dirname "$0")"

echo "╔════════════════════════════════════════════════════════╗"
echo "║         MiniTB GPU 监控系统启动                         ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

echo "🔧 启动配置:"
echo "  - 存储: SQLite (data/minitb.db)"
echo "  - Prometheus 拉取: 每 2 秒"
echo "  - Web 端口: 8080"
echo "  - MQTT 端口: 1883"
echo ""

echo "🚀 启动 MiniTB..."
mvn spring-boot:run &
MINITB_PID=$!

echo "   进程 PID: $MINITB_PID"
echo ""

echo "⏳ 等待服务启动..."
sleep 12
echo ""

echo "╔════════════════════════════════════════════════════════╗"
echo "║         ✅ MiniTB 启动完成                              ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

echo "🌐 访问地址:"
echo "  📊 GPU 监控界面:  http://localhost:8080"
echo "  📡 设备 API:      http://localhost:8080/api/devices"
echo "  📈 遥测 API:      http://localhost:8080/api/telemetry/{deviceId}/latest"
echo ""

echo "🎯 监控设备:"
curl -s http://localhost:8080/api/devices 2>/dev/null | python3 -c "
import sys, json
try:
    devices = json.load(sys.stdin)
    for d in devices:
        if d['type'] == 'NVIDIA_GPU':
            print(f\"  ✓ {d['name']} [{d['prometheusLabel']}]\")
            print(f\"    ID: {d['id']}\")
except:
    print('  (设备数据加载中...)')
" || echo "  (设备数据加载中...)"

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "💡 提示:"
echo "  - 在浏览器中打开 http://localhost:8080 查看实时监控"
echo "  - 数据每 2 秒自动刷新"
echo "  - 按 Ctrl+C 停止服务"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 等待用户中断
wait $MINITB_PID


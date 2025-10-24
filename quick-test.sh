#!/bin/bash

echo "=========================================="
echo "  MiniTB 快速测试脚本"
echo "=========================================="
echo ""

# 检查 Prometheus 是否运行
echo "检查 Prometheus 状态..."
if curl -s http://localhost:9090/api/v1/query?query=up > /dev/null 2>&1; then
    echo "✅ Prometheus 运行正常"
else
    echo "❌ Prometheus 未运行，部分测试将跳过"
    echo "   请启动 Prometheus: http://localhost:9090"
fi

echo ""
echo "=========================================="
echo "  测试1: 编译项目"
echo "=========================================="
cd /Users/chun/Develop/thingsboard/minitb
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "  测试2: 启动 MiniTB"
echo "=========================================="
echo "启动中（15秒）..."

# 停止旧进程
pkill -f "com.minitb.MiniTBApplication" 2>/dev/null
sleep 2

# 启动新进程
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home
mvn exec:java -Dexec.mainClass="com.minitb.MiniTBApplication" > /tmp/minitb_test.log 2>&1 &
MINITB_PID=$!

sleep 12

# 检查是否启动成功
if grep -q "MiniTB运行中" /tmp/minitb_test.log; then
    echo "✅ MiniTB 启动成功"
else
    echo "❌ MiniTB 启动失败，查看日志: /tmp/minitb_test.log"
    exit 1
fi

echo ""
echo "=========================================="
echo "  测试3: MQTT 强类型数据推送"
echo "=========================================="
if command -v mosquitto_pub &> /dev/null; then
    mosquitto_pub -h localhost -p 1883 -u test-token-001 \
        -t v1/devices/me/telemetry \
        -m '{"temperature":25.5,"humidity":60,"online":true,"status":"running"}' 2>/dev/null
    
    sleep 2
    
    if grep -q "temperature=25.5 (DOUBLE)" /tmp/minitb_test.log; then
        echo "✅ MQTT 数据推送成功"
        echo "   - temperature=25.5 (DOUBLE)"
        echo "   - humidity=60 (LONG)"
        echo "   - online=true (BOOLEAN)"
        echo "   - status=running (STRING)"
    else
        echo "⚠️  MQTT 数据未找到，检查日志"
    fi
else
    echo "⚠️  mosquitto_pub 未安装，跳过 MQTT 测试"
fi

echo ""
echo "=========================================="
echo "  测试4: Prometheus 数据拉取"
echo "=========================================="
sleep 3

if grep -q "cpu_seconds_total" /tmp/minitb_test.log && \
   grep -q "memory_alloc_bytes" /tmp/minitb_test.log && \
   grep -q "goroutines" /tmp/minitb_test.log; then
    echo "✅ Prometheus 数据拉取成功"
    echo "   拉取的指标:"
    grep "保存遥测数据.*key=cpu_seconds_total" /tmp/minitb_test.log | tail -1 | sed 's/.*key=/   - CPU: /'
    grep "保存遥测数据.*key=memory_alloc_bytes" /tmp/minitb_test.log | tail -1 | sed 's/.*key=/   - 内存: /'
    grep "保存遥测数据.*key=goroutines" /tmp/minitb_test.log | tail -1 | sed 's/.*key=/   - 协程: /'
else
    echo "⚠️  Prometheus 数据未完全拉取"
fi

echo ""
echo "=========================================="
echo "  测试5: 数据文件验证"
echo "=========================================="
DATA_FILES=$(find minitb/data -name "telemetry_*.log" -type f 2>/dev/null | wc -l)
echo "✅ 找到 $DATA_FILES 个数据文件"

if [ $DATA_FILES -gt 0 ]; then
    echo ""
    echo "最新数据示例:"
    find minitb/data -name "telemetry_*.log" -type f 2>/dev/null | head -1 | xargs tail -3 2>/dev/null | sed 's/^/   /'
fi

echo ""
echo "=========================================="
echo "  测试完成"
echo "=========================================="
echo ""
echo "停止 MiniTB..."
pkill -f "com.minitb.MiniTBApplication" 2>/dev/null
sleep 1

echo ""
echo "📊 查看完整日志:"
echo "   cat /tmp/minitb_test.log"
echo ""
echo "📁 查看数据文件:"
echo "   ls -lh minitb/data/"
echo "   cat minitb/data/telemetry_*.log"
echo ""
echo "✅ 所有测试完成！"


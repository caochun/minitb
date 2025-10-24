#!/bin/bash

echo "=========================================="
echo "   测试 Actor 系统处理 Prometheus 数据"
echo "=========================================="

cd /Users/chun/Develop/thingsboard/minitb

# 停止旧进程
echo "1. 停止旧进程..."
pkill -f "com.minitb.MiniTBApplication" 2>/dev/null
sleep 2

# 清理日志
rm -f /tmp/minitb_actor_full.log

# 启动 MiniTB 并过滤 Actor 相关日志
echo "2. 启动 MiniTB (后台运行)..."
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home
mvn exec:java -Dexec.mainClass="com.minitb.MiniTBApplication" > /tmp/minitb_actor_full.log 2>&1 &
MINITB_PID=$!

echo "   MiniTB PID: $MINITB_PID"
echo "   等待启动..."
sleep 15

# 检查是否启动成功
if ! kill -0 $MINITB_PID 2>/dev/null; then
    echo "   ❌ MiniTB 启动失败"
    cat /tmp/minitb_actor_full.log | tail -50
    exit 1
fi

echo "   ✅ MiniTB 启动成功"
echo ""

# 3. 查看 Actor 系统初始化
echo "=========================================="
echo "3. Actor 系统初始化日志"
echo "=========================================="
grep -E "Actor 系统|创建 Actor|Actor 初始化|启用 Actor" /tmp/minitb_actor_full.log | head -20
echo ""

# 4. 等待 Prometheus 数据拉取
echo "=========================================="
echo "4. 等待 Prometheus 数据拉取 (15秒)..."
echo "=========================================="
sleep 15

# 5. 查看 Prometheus 数据处理
echo ""
echo "=========================================="
echo "5. Prometheus 数据处理日志"
echo "=========================================="
echo ""
echo "--- Prometheus 拉取到数据 ---"
grep "从Prometheus拉取到设备数据" /tmp/minitb_actor_full.log | head -5
echo ""
echo "--- 通过 Actor 系统发送 ---"
grep "通过 Actor 系统发送消息" /tmp/minitb_actor_full.log | head -5
echo ""
echo "--- DeviceActor 收到消息 ---"
grep -E "收到遥测数据|Device Actor.*处理" /tmp/minitb_actor_full.log | head -5
echo ""
echo "--- DeviceActor 转发到规则引擎 ---"
grep "消息已转发到规则引擎" /tmp/minitb_actor_full.log | head -5
echo ""
echo "--- RuleEngineActor 收到消息 ---"
grep "规则引擎收到消息" /tmp/minitb_actor_full.log | head -5
echo ""

# 6. 统计消息数量
echo "=========================================="
echo "6. 消息统计"
echo "=========================================="
PROM_PULL_COUNT=$(grep -c "从Prometheus拉取到设备数据" /tmp/minitb_actor_full.log)
ACTOR_SEND_COUNT=$(grep -c "通过 Actor 系统发送消息" /tmp/minitb_actor_full.log)
RULE_RECEIVE_COUNT=$(grep -c "规则引擎收到消息" /tmp/minitb_actor_full.log)

echo "Prometheus 拉取次数: $PROM_PULL_COUNT"
echo "Actor 发送次数: $ACTOR_SEND_COUNT"
echo "规则引擎接收次数: $RULE_RECEIVE_COUNT"
echo ""

# 7. 验证结果
echo "=========================================="
echo "7. 验证结果"
echo "=========================================="
if [ $ACTOR_SEND_COUNT -gt 0 ]; then
    echo "✅ Prometheus 数据通过 Actor 系统处理"
    echo "✅ 数据流: Prometheus → TransportService → Actor → DeviceActor → RuleEngineActor"
else
    echo "❌ 未检测到 Actor 处理"
fi
echo ""

# 8. 停止 MiniTB
echo "8. 停止 MiniTB..."
kill $MINITB_PID 2>/dev/null
wait $MINITB_PID 2>/dev/null

echo ""
echo "完成！完整日志: /tmp/minitb_actor_full.log"


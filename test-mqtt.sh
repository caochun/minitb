#!/bin/bash

# MQTT测试脚本
# 需要安装mosquitto客户端: brew install mosquitto (macOS)

HOST="localhost"
PORT="1883"
TOKEN="test-token-001"
TOPIC="v1/devices/me/telemetry"

echo "========================================="
echo "   MiniTB MQTT 测试脚本"
echo "========================================="
echo ""

# 测试1: 发送温度数据 (温度 > 20, 会被保存)
echo "测试1: 发送温度 25°C (会通过过滤)"
mosquitto_pub -h $HOST -p $PORT -u $TOKEN \
  -t $TOPIC \
  -m '{"temperature":25,"humidity":60}'
sleep 1

# 测试2: 发送温度数据 (温度 <= 20, 会被过滤)
echo ""
echo "测试2: 发送温度 15°C (会被过滤)"
mosquitto_pub -h $HOST -p $PORT -u $TOKEN \
  -t $TOPIC \
  -m '{"temperature":15,"humidity":70}'
sleep 1

# 测试3: 发送复杂数据
echo ""
echo "测试3: 发送复杂遥测数据"
mosquitto_pub -h $HOST -p $PORT -u $TOKEN \
  -t $TOPIC \
  -m '{"temperature":30,"humidity":55,"pressure":1013.25,"battery":85}'
sleep 1

# 测试4: 使用不同的设备token
echo ""
echo "测试4: 使用湿度传感器token"
mosquitto_pub -h $HOST -p $PORT -u test-token-002 \
  -t $TOPIC \
  -m '{"temperature":22,"humidity":65}'

echo ""
echo "========================================="
echo "测试完成！请查看MiniTB的日志输出"
echo "数据文件位置: minitb/data/telemetry_*.log"
echo "========================================="





#!/bin/bash

# GPU 数据验证脚本
# 启动 MiniTB 并验证数据是否正确保存到 TelemetryStorage

cd "$(dirname "$0")"

echo "╔════════════════════════════════════════════════════════╗"
echo "║         GPU 监控数据验证                                ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# 清理旧数据
echo "🧹 清理旧数据..."
rm -rf data/
echo ""

# 启动 MiniTB
echo "🚀 启动 MiniTB..."
mvn spring-boot:run > /tmp/minitb-verify.log 2>&1 &
MINITB_PID=$!
echo "   PID: $MINITB_PID"
echo ""

# 等待启动
echo "⏳ 等待启动完成..."
sleep 8

# 检查数据库
echo "📂 SQLite 数据库状态:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ls -lh data/minitb.db
echo ""

echo "📊 数据库内容:"
sqlite3 data/minitb.db << 'SQL'
.mode column
.headers on
SELECT name, type, prometheus_label FROM device;
SQL
echo ""

# 等待数据拉取
echo "⏳ 等待数据拉取 (15 秒，约 7-8 次)..."
sleep 15
echo ""

# 检查拉取次数
echo "📈 Prometheus 拉取统计:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
PULL_COUNT=$(grep "📊 开始拉取" /tmp/minitb-verify.log | wc -l | xargs)
SUCCESS_COUNT=$(grep "✅ Prometheus 数据拉取完成: 成功 2" /tmp/minitb-verify.log | wc -l | xargs)
echo "   总拉取次数: $PULL_COUNT"
echo "   成功次数: $SUCCESS_COUNT"
echo "   拉取间隔: ~2 秒"
echo ""

# 检查最近的拉取时间
echo "📊 最近 5 次拉取时间:"
grep "📊 开始拉取" /tmp/minitb-verify.log | tail -5 | awk '{print "  ", $1, $2}'
echo ""

# 检查数据接收
echo "📥 数据接收统计:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
GPU0_COUNT=$(grep "token=gpu-0-token" /tmp/minitb-verify.log | wc -l | xargs)
GPU1_COUNT=$(grep "token=gpu-1-token" /tmp/minitb-verify.log | wc -l | xargs)
echo "   GPU 0 数据接收: $GPU0_COUNT 次"
echo "   GPU 1 数据接收: $GPU1_COUNT 次"
echo ""

# 停止 MiniTB
echo "⏹️  停止 MiniTB..."
kill $MINITB_PID 2>/dev/null
sleep 2
echo "   ✅ 已停止"
echo ""

echo "╔════════════════════════════════════════════════════════╗"
echo "║         ✅ 验证完成                                     ║"
echo "╚════════════════════════════════════════════════════════╝"



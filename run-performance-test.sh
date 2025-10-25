#!/bin/bash

# MiniTB 性能压力测试脚本
# 执行各种性能测试场景

echo "========================================"
echo "    MiniTB 性能压力测试脚本"
echo "========================================"

# 设置 Java 环境
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home

# 进入项目目录
cd /Users/chun/Develop/thingsboard/minitb || exit

# 编译项目
echo "1. 编译 MiniTB 项目..."
chmod +x run.sh
./run.sh clean compile || { echo "编译失败"; exit 1; }
echo "   编译完成"

# 检查参数
TEST_TYPE=${1:-"full"}
echo "2. 执行测试类型: $TEST_TYPE"

# 创建日志目录
mkdir -p logs
LOG_FILE="logs/performance-test-$(date +%Y%m%d-%H%M%S).log"

echo "3. 开始性能测试..."
echo "   日志文件: $LOG_FILE"

# 执行性能测试
case $TEST_TYPE in
    "full")
        echo "   执行完整测试套件..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain full > "$LOG_FILE" 2>&1
        ;;
    "single")
        echo "   执行单设备吞吐量测试..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain single > "$LOG_FILE" 2>&1
        ;;
    "multi")
        echo "   执行多设备并发测试..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain multi > "$LOG_FILE" 2>&1
        ;;
    "comparison")
        echo "   执行 Actor vs 同步对比测试..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain comparison > "$LOG_FILE" 2>&1
        ;;
    "peak")
        echo "   执行消息峰值测试..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain peak > "$LOG_FILE" 2>&1
        ;;
    "fault")
        echo "   执行故障隔离测试..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain fault > "$LOG_FILE" 2>&1
        ;;
    "backpressure")
        echo "   执行背压测试..."
        java -cp target/test-classes:target/classes:target/dependency/* com.minitb.performance.PerformanceTestMain backpressure > "$LOG_FILE" 2>&1
        ;;
    *)
        echo "   未知测试类型: $TEST_TYPE"
        echo "   可用类型: full, single, multi, comparison, peak, fault, backpressure"
        exit 1
        ;;
esac

# 检查测试结果
if [ $? -eq 0 ]; then
    echo "4. 测试完成！"
    echo "   查看详细日志: cat $LOG_FILE"
    echo ""
    echo "=== 测试结果摘要 ==="
    grep -E "(吞吐量|延迟|成功率|内存)" "$LOG_FILE" | tail -10
    echo ""
    echo "=== 性能对比 ==="
    grep -E "(Actor.*模式|同步.*模式)" "$LOG_FILE" | tail -5
    echo ""
    echo "=== 测试结论 ==="
    grep -E "(性能表现|结论)" "$LOG_FILE" | tail -5
else
    echo "4. 测试失败！"
    echo "   查看错误日志: cat $LOG_FILE"
    exit 1
fi

echo "========================================"
echo "   性能测试完成"
echo "========================================"

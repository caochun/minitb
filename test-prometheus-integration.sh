#!/bin/bash

# Prometheus 集成测试脚本
# 用于测试从本地 Prometheus 拉取数据

echo "========================================"
echo "  Prometheus 集成测试"
echo "========================================"
echo ""

# 检查 Prometheus 是否运行
echo "1️⃣  检查 Prometheus 服务..."
if curl -s http://localhost:9090/api/v1/status/config > /dev/null 2>&1; then
    echo "   ✅ Prometheus 运行中 (http://localhost:9090)"
else
    echo "   ❌ Prometheus 未运行"
    echo ""
    echo "   启动方法:"
    echo "   docker run -d -p 9090:9090 --name prometheus prom/prometheus"
    echo ""
    exit 1
fi

# 检查 Node Exporter 是否运行
echo "2️⃣  检查 Node Exporter..."
if curl -s http://localhost:9100/metrics > /dev/null 2>&1; then
    echo "   ✅ Node Exporter 运行中 (http://localhost:9100)"
else
    echo "   ❌ Node Exporter 未运行"
    echo ""
    echo "   启动方法:"
    echo "   docker run -d -p 9100:9100 --name node-exporter prom/node-exporter"
    echo ""
    exit 1
fi

echo ""
echo "3️⃣  配置 Prometheus 抓取 Node Exporter..."
echo "   如果 Prometheus 查询不到数据，请确保 prometheus.yml 包含:"
echo ""
echo "   scrape_configs:"
echo "     - job_name: 'node'"
echo "       static_configs:"
echo "         - targets: ['host.docker.internal:9100']  # Mac/Windows"
echo "         # 或 targets: ['172.17.0.1:9100']         # Linux"
echo ""

echo "4️⃣  运行集成测试..."
echo ""

# 设置环境变量并运行测试
export PROMETHEUS_ENABLED=true
mvn test -Dtest=PrometheusDataPullerIntegrationTest

echo ""
echo "========================================"
echo "  测试完成"
echo "========================================"



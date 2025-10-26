#!/bin/bash

# 快速搭建 Prometheus + Node Exporter 测试环境

echo "========================================"
echo "  搭建 Prometheus 测试环境"
echo "========================================"
echo ""

# 1. 启动 Node Exporter
echo "1️⃣  启动 Node Exporter..."
if docker ps | grep -q node-exporter; then
    echo "   ℹ️  Node Exporter 已在运行"
else
    docker run -d \
        --name node-exporter \
        -p 9100:9100 \
        prom/node-exporter \
        > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo "   ✅ Node Exporter 启动成功 (http://localhost:9100)"
    else
        echo "   ❌ Node Exporter 启动失败"
        exit 1
    fi
fi

# 等待 Node Exporter 就绪
sleep 2

# 2. 创建 Prometheus 配置文件
echo "2️⃣  创建 Prometheus 配置..."
mkdir -p /tmp/prometheus

cat > /tmp/prometheus/prometheus.yml << 'EOF'
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'node'
    static_configs:
      - targets: ['host.docker.internal:9100']  # Mac/Windows
EOF

echo "   ✅ 配置文件创建: /tmp/prometheus/prometheus.yml"

# 3. 启动 Prometheus
echo "3️⃣  启动 Prometheus..."
if docker ps | grep -q prometheus-test; then
    echo "   ℹ️  Prometheus 已在运行"
else
    docker run -d \
        --name prometheus-test \
        -p 9090:9090 \
        -v /tmp/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml \
        prom/prometheus \
        --config.file=/etc/prometheus/prometheus.yml \
        > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        echo "   ✅ Prometheus 启动成功 (http://localhost:9090)"
    else
        echo "   ❌ Prometheus 启动失败"
        exit 1
    fi
fi

# 等待 Prometheus 就绪
echo ""
echo "⏳ 等待 Prometheus 抓取数据 (15秒)..."
sleep 15

# 4. 验证设置
echo ""
echo "4️⃣  验证环境..."

# 检查 Node Exporter
if curl -s http://localhost:9100/metrics | grep -q node_cpu_seconds_total; then
    echo "   ✅ Node Exporter 可访问"
else
    echo "   ❌ Node Exporter 不可访问"
fi

# 检查 Prometheus
if curl -s http://localhost:9090/api/v1/query?query=up | grep -q '"status":"success"'; then
    echo "   ✅ Prometheus 可访问"
else
    echo "   ❌ Prometheus 不可访问"
fi

# 检查 Prometheus 是否成功抓取 Node Exporter 数据
RESULT=$(curl -s "http://localhost:9090/api/v1/query?query=node_cpu_seconds_total")
if echo "$RESULT" | grep -q '"result":\['; then
    echo "   ✅ Prometheus 成功抓取 Node Exporter 数据"
else
    echo "   ⚠️  Prometheus 尚未抓取到数据（可能需要等待）"
fi

echo ""
echo "========================================"
echo "✅ 环境搭建完成！"
echo "========================================"
echo ""
echo "📊 Prometheus UI: http://localhost:9090"
echo "📡 Node Exporter: http://localhost:9100/metrics"
echo ""
echo "💡 运行集成测试:"
echo "   ./test-prometheus-integration.sh"
echo ""
echo "🧹 清理环境:"
echo "   docker stop prometheus-test node-exporter"
echo "   docker rm prometheus-test node-exporter"
echo ""


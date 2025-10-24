#!/bin/bash

# MiniTB Prometheus模拟测试脚本
# 由于没有真实的Prometheus服务器，这里提供一个简单的mock服务器

echo "========================================="
echo "   MiniTB Prometheus Mock Server"
echo "========================================="
echo ""
echo "这是一个简单的Prometheus API mock服务器"
echo "用于测试MiniTB的Prometheus数据拉取功能"
echo ""

# 检查是否安装了python3
if ! command -v python3 &> /dev/null; then
    echo "错误: 未找到python3，请先安装python3"
    exit 1
fi

# 创建临时Python脚本
cat > /tmp/prometheus_mock.py << 'EOF'
#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import time
import random
import urllib.parse

class PrometheusHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        # 解析查询参数
        parsed_path = urllib.parse.urlparse(self.path)
        query_params = urllib.parse.parse_qs(parsed_path.query)
        
        # /api/v1/query 端点
        if parsed_path.path == '/api/v1/query':
            query = query_params.get('query', [''])[0]
            print(f"接收到查询: {query}")
            
            # 模拟返回数据
            # 解析查询中的指标名称
            metric_name = query.split('{')[0] if '{' in query else query
            
            # 生成随机数据
            if 'temperature' in metric_name:
                value = random.uniform(18.0, 30.0)
            elif 'humidity' in metric_name:
                value = random.uniform(40.0, 80.0)
            else:
                value = random.uniform(0.0, 100.0)
            
            response = {
                "status": "success",
                "data": {
                    "resultType": "vector",
                    "result": [{
                        "metric": {
                            "__name__": metric_name,
                            "device_id": "prom-device-001",
                            "device_name": "温度传感器-Prom"
                        },
                        "value": [time.time(), str(round(value, 2))]
                    }]
                }
            }
            
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response).encode())
            print(f"  -> 返回: {metric_name} = {value:.2f}")
        
        # /metrics 端点（Prometheus自身指标）
        elif parsed_path.path == '/metrics':
            metrics = f"""# HELP temperature Device temperature
# TYPE temperature gauge
temperature{{device_id="prom-device-001"}} {random.uniform(18, 30):.2f}

# HELP humidity Device humidity
# TYPE humidity gauge
humidity{{device_id="prom-device-001"}} {random.uniform(40, 80):.2f}
"""
            self.send_response(200)
            self.send_header('Content-type', 'text/plain')
            self.end_headers()
            self.wfile.write(metrics.encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        # 简化日志输出
        pass

def run_server(port=9090):
    server_address = ('', port)
    httpd = HTTPServer(server_address, PrometheusHandler)
    print(f"Mock Prometheus服务器启动成功！")
    print(f"监听端口: {port}")
    print(f"API地址: http://localhost:{port}/api/v1/query")
    print(f"Metrics地址: http://localhost:{port}/metrics")
    print(f"\n服务器运行中，按Ctrl+C停止...\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n\n服务器已停止")

if __name__ == '__main__':
    run_server(9090)
EOF

# 启动mock服务器
python3 /tmp/prometheus_mock.py




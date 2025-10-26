#!/bin/bash

# MiniTB HTTP API 测试脚本

BASE_URL="http://localhost:8080/api"

echo "=========================================="
echo "   MiniTB HTTP API 测试"
echo "=========================================="
echo ""

# 1. 健康检查
echo "1. 健康检查"
echo "   GET $BASE_URL/health"
curl -s "$BASE_URL/health" | json_pp || curl -s "$BASE_URL/health"
echo -e "\n"

# 2. 获取所有设备
echo "2. 获取所有设备"
echo "   GET $BASE_URL/devices"
curl -s "$BASE_URL/devices" | json_pp || curl -s "$BASE_URL/devices"
echo -e "\n"

# 3. 创建新设备
echo "3. 创建新设备"
echo "   POST $BASE_URL/devices"
curl -s -X POST "$BASE_URL/devices" \
  -d "name=API-Test-Device" \
  -d "type=sensor" \
  -d "accessToken=api-test-token-001" | json_pp || \
curl -s -X POST "$BASE_URL/devices" \
  -d "name=API-Test-Device" \
  -d "type=sensor" \
  -d "accessToken=api-test-token-001"
echo -e "\n"

# 4. 再次获取所有设备（应该看到新创建的设备）
echo "4. 验证设备已创建"
echo "   GET $BASE_URL/devices"
curl -s "$BASE_URL/devices" | json_pp || curl -s "$BASE_URL/devices"
echo -e "\n"

echo "=========================================="
echo "   测试完成!"
echo "=========================================="
echo ""
echo "你可以使用以下命令继续测试："
echo ""
echo "# 获取单个设备（替换 {id} 为实际设备ID）"
echo "curl $BASE_URL/devices/{id}"
echo ""
echo "# 更新设备"
echo "curl -X PUT $BASE_URL/devices/{id} -d 'name=Updated-Device'"
echo ""
echo "# 删除设备"
echo "curl -X DELETE $BASE_URL/devices/{id}"
echo ""


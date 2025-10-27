// GPU 监控前端逻辑

let devices = [];
let currentDeviceId = null;
let charts = {};

// 初始化
window.addEventListener('DOMContentLoaded', async () => {
    console.log('🚀 初始化 GPU 监控界面...');
    
    try {
        // 加载设备列表
        console.log('步骤 1: 加载设备列表');
        await loadDevices();
        console.log('步骤 1 完成');
        
        // 初始化图表
        console.log('步骤 2: 初始化图表');
        initCharts();
        console.log('步骤 2 完成');
        
        // 开始定时刷新（每 2 秒）
        console.log('步骤 3: 启动定时器');
        setInterval(updateData, 2000);
        
        // 立即更新一次
        console.log('步骤 4: 首次更新数据');
        updateData();
        
        console.log('✅ 初始化完成');
        
    } catch (error) {
        console.error('❌ 初始化失败:', error);
        showError('初始化失败: ' + error.message);
    }
});

// 加载设备列表
async function loadDevices() {
    console.log('📡 加载设备列表...');
    
    const response = await fetch('/api/devices');
    devices = await response.json();
    
    console.log(`✅ 加载到 ${devices.length} 个设备`, devices);
    
    // 筛选出 GPU 设备
    const gpuDevices = devices.filter(d => d.type === 'NVIDIA_GPU');
    console.log(`筛选出 ${gpuDevices.length} 个 GPU 设备`, gpuDevices);
    
    if (gpuDevices.length === 0) {
        console.error('未找到 GPU 设备');
        showError('未找到 GPU 设备');
        throw new Error('未找到 GPU 设备');
    }
    
    // 默认选择第一个 GPU
    currentDeviceId = gpuDevices[0].id;
    console.log('默认选择设备:', currentDeviceId);
    
    // 渲染设备选择器
    console.log('渲染设备选择器...');
    renderDeviceTabs(gpuDevices);
    console.log('设备选择器渲染完成');
}

// 渲染设备选项卡
function renderDeviceTabs(gpuDevices) {
    const tabsContainer = document.getElementById('deviceTabs');
    tabsContainer.innerHTML = '';
    
    gpuDevices.forEach(device => {
        const tab = document.createElement('button');
        tab.className = 'device-tab';
        tab.textContent = device.name;
        tab.onclick = () => selectDevice(device.id);
        
        if (device.id === currentDeviceId) {
            tab.classList.add('active');
        }
        
        tabsContainer.appendChild(tab);
    });
}

// 选择设备
function selectDevice(deviceId) {
    console.log('📌 切换到设备:', deviceId);
    currentDeviceId = deviceId;
    
    // 更新选项卡样式
    document.querySelectorAll('.device-tab').forEach(tab => {
        tab.classList.remove('active');
    });
    event.target.classList.add('active');
    
    // 立即更新数据
    updateData();
}

// 更新数据
async function updateData() {
    if (!currentDeviceId) return;
    
    try {
        // 获取最新数据
        const latest = await fetch(`/api/telemetry/${currentDeviceId}/latest`).then(r => r.json());
        
        // 更新指标卡片
        updateMetricsCards(latest.telemetry);
        
        // 更新图表
        await updateCharts();
        
        // 更新时间
        updateTimestamp(latest.timestamp);
        
        // 更新状态
        document.getElementById('status').textContent = '● 实时监控';
        
    } catch (error) {
        console.error('❌ 更新数据失败:', error);
        document.getElementById('status').textContent = '● 连接失败';
    }
}

// 更新指标卡片
function updateMetricsCards(telemetry) {
    const grid = document.getElementById('metricsGrid');
    
    const metrics = [
        { key: 'gpu_utilization', label: 'GPU 利用率', unit: '%', icon: '🎮' },
        { key: 'gpu_temperature', label: 'GPU 温度', unit: '°C', icon: '🌡️' },
        { key: 'memory_temperature', label: '显存温度', unit: '°C', icon: '🌡️' },
        { key: 'power_usage', label: '功耗', unit: 'W', icon: '⚡' },
        { key: 'memory_used', label: '已用显存', unit: 'MiB', icon: '💾' },
        { key: 'memory_free', label: '空闲显存', unit: 'MiB', icon: '💾' },
        { key: 'memory_copy_utilization', label: 'PCIe 利用率', unit: '%', icon: '🔄' }
    ];
    
    grid.innerHTML = metrics.map(m => {
        const value = telemetry[m.key];
        const displayValue = value !== undefined ? 
            (Number.isInteger(value) ? value : value.toFixed(1)) : '--';
        
        return `
            <div class="metric-card">
                <div class="metric-label">${m.icon} ${m.label}</div>
                <div class="metric-value">
                    ${displayValue}
                    <span class="metric-unit">${m.unit}</span>
                </div>
            </div>
        `;
    }).join('');
    
    // 计算显存使用率
    if (telemetry.memory_used && telemetry.memory_free) {
        const used = telemetry.memory_used;
        const total = telemetry.memory_used + telemetry.memory_free;
        const percentage = ((used / total) * 100).toFixed(2);
        
        grid.innerHTML += `
            <div class="metric-card">
                <div class="metric-label">📊 显存使用率</div>
                <div class="metric-value">
                    ${percentage}
                    <span class="metric-unit">%</span>
                </div>
                <div style="color: #a0aec0; font-size: 14px; margin-top: 5px;">
                    ${used.toFixed(0)} / ${total.toFixed(0)} MiB
                </div>
            </div>
        `;
    }
}

// 初始化图表
function initCharts() {
    console.log('📊 初始化图表...');
    
    // 检查 Chart.js 是否加载
    if (typeof Chart === 'undefined') {
        console.error('❌ Chart.js 未加载！');
        showError('Chart.js 加载失败，请刷新页面');
        return;
    }
    
    const chartConfig = {
        type: 'line',
        options: {
            responsive: true,
            maintainAspectRatio: true,
            aspectRatio: 3,
            plugins: {
                legend: {
                    display: false
                }
            },
            scales: {
                x: {
                    type: 'linear',
                    title: {
                        display: true,
                        text: '时间'
                    },
                    ticks: {
                        callback: function(value, index, values) {
                            // 显示为相对时间（秒）
                            return value + 's';
                        }
                    }
                },
                y: {
                    beginAtZero: false
                }
            },
            animation: {
                duration: 0  // 禁用动画，提高性能
            }
        }
    };
    
    try {
        // 温度图表
        charts.temperature = new Chart(
        document.getElementById('temperatureChart'),
        {
            ...chartConfig,
            data: {
                datasets: [{
                    label: 'GPU 温度',
                    data: [],
                    borderColor: 'rgb(239, 68, 68)',
                    backgroundColor: 'rgba(239, 68, 68, 0.1)',
                    tension: 0.4
                }]
            }
        }
    );
    
    // 利用率图表
    charts.utilization = new Chart(
        document.getElementById('utilizationChart'),
        {
            ...chartConfig,
            data: {
                datasets: [{
                    label: 'GPU 利用率',
                    data: [],
                    borderColor: 'rgb(59, 130, 246)',
                    backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    tension: 0.4
                }]
            },
            options: {
                ...chartConfig.options,
                scales: {
                    ...chartConfig.options.scales,
                    y: {
                        min: 0,
                        max: 100,
                        title: {
                            display: true,
                            text: '利用率 (%)'
                        }
                    }
                }
            }
        }
    );
    
    // 功耗图表
    charts.power = new Chart(
        document.getElementById('powerChart'),
        {
            ...chartConfig,
            data: {
                datasets: [{
                    label: '功耗',
                    data: [],
                    borderColor: 'rgb(16, 185, 129)',
                    backgroundColor: 'rgba(16, 185, 129, 0.1)',
                    tension: 0.4
                }]
            }
        }
    );
    
    console.log('✅ 图表初始化成功');
    } catch (error) {
        console.error('❌ 图表初始化失败:', error);
        showError('图表初始化失败: ' + error.message);
    }
}

// 更新图表
async function updateCharts() {
    if (!currentDeviceId) return;
    if (!charts.temperature || !charts.utilization || !charts.power) {
        console.warn('⚠️ 图表未初始化，跳过更新');
        return;
    }
    
    try {
        // 获取历史数据（最近 60 秒）
        const [tempHistory, utilHistory, powerHistory] = await Promise.all([
            fetch(`/api/telemetry/${currentDeviceId}/history/gpu_temperature?duration=60`).then(r => r.json()),
            fetch(`/api/telemetry/${currentDeviceId}/history/gpu_utilization?duration=60`).then(r => r.json()),
            fetch(`/api/telemetry/${currentDeviceId}/history/power_usage?duration=60`).then(r => r.json())
        ]);
        
        // 计算相对时间（从最早的点开始，单位：秒）
        const now = Date.now();
        
        // 更新温度图表
        charts.temperature.data.datasets[0].data = tempHistory.map((d, idx) => ({
            x: -(tempHistory.length - idx - 1) * 2,  // 相对时间（秒前）
            y: d.value
        }));
        charts.temperature.update();
        
        // 更新利用率图表
        charts.utilization.data.datasets[0].data = utilHistory.map((d, idx) => ({
            x: -(utilHistory.length - idx - 1) * 2,
            y: d.value
        }));
        charts.utilization.update();
        
        // 更新功耗图表
        charts.power.data.datasets[0].data = powerHistory.map((d, idx) => ({
            x: -(powerHistory.length - idx - 1) * 2,
            y: d.value
        }));
        charts.power.update();
        
    } catch (error) {
        console.error('❌ 更新图表失败:', error);
    }
}

// 更新时间戳
function updateTimestamp(timestamp) {
    if (!timestamp) return;
    
    const date = new Date(timestamp);
    const timeStr = date.toLocaleString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
    
    document.getElementById('updateTime').textContent = `最后更新: ${timeStr}`;
}

// 显示错误
function showError(message) {
    const errorDiv = document.getElementById('error');
    errorDiv.textContent = '❌ ' + message;
    errorDiv.style.display = 'block';
    
    setTimeout(() => {
        errorDiv.style.display = 'none';
    }, 5000);
}


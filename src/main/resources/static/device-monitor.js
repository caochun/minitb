// 通用设备监控前端逻辑 (支持 GPU 和 BMC)

let devices = [];
let currentDevice = null;
let charts = {};

// 设备类型配置
const DEVICE_CONFIGS = {
    NVIDIA_GPU: {
        icon: '🎮',
        title: 'GPU 监控',
        subtitle: 'NVIDIA GPU 实时监控',
        updateInterval: 2000,
        metrics: [
            { key: 'gpu_utilization', label: 'GPU 利用率', unit: '%', icon: '📊' },
            { key: 'gpu_temperature', label: 'GPU 温度', unit: '°C', icon: '🌡️' },
            { key: 'memory_temperature', label: '显存温度', unit: '°C', icon: '🌡️' },
            { key: 'power_usage', label: '功耗', unit: 'W', icon: '⚡' },
            { key: 'memory_used', label: '已用显存', unit: 'MiB', icon: '💾' },
            { key: 'memory_free', label: '空闲显存', unit: 'MiB', icon: '💾' },
            { key: 'sm_clock', label: 'SM 时钟', unit: 'MHz', icon: '🔧' },
            { key: 'memory_clock', label: '显存时钟', unit: 'MHz', icon: '🔧' }
        ],
        charts: [
            {
                id: 'temperatureChart',
                title: '📈 温度趋势（最近 5 分钟）',
                keys: ['gpu_temperature', 'memory_temperature'],
                labels: ['GPU 温度', '显存温度'],
                colors: ['rgb(255, 99, 132)', 'rgb(255, 159, 64)']
            },
            {
                id: 'utilizationChart',
                title: '📊 利用率（最近 5 分钟）',
                keys: ['gpu_utilization', 'memory_copy_utilization'],
                labels: ['GPU 利用率', '内存拷贝利用率'],
                colors: ['rgb(54, 162, 235)', 'rgb(153, 102, 255)']
            },
            {
                id: 'powerChart',
                title: '⚡ 功耗趋势（最近 5 分钟）',
                keys: ['power_usage'],
                labels: ['功耗'],
                colors: ['rgb(75, 192, 192)']
            }
        ]
    },
    SERVER: {
        icon: '🖥️',
        title: 'BMC 监控',
        subtitle: '服务器 BMC 实时监控',
        updateInterval: 30000,
        metrics: [
            { key: 'cpu0_temperature', label: 'CPU0 温度', unit: '°C', icon: '🌡️' },
            { key: 'cpu1_temperature', label: 'CPU1 温度', unit: '°C', icon: '🌡️' },
            { key: 'motherboard_temperature', label: 'M.2 温度', unit: '°C', icon: '🌡️' },
            { key: 'memory_temperature', label: '内存温度', unit: '°C', icon: '🌡️' },
            { key: 'cpu0_fan_speed', label: 'CPU0 风扇', unit: 'RPM', icon: '💨' },
            { key: 'cpu1_fan_speed', label: 'CPU1 风扇', unit: 'RPM', icon: '💨' },
            { key: 'voltage_12v', label: '12V 电压', unit: 'V', icon: '⚡' },
            { key: 'voltage_5v', label: '5V 电压', unit: 'V', icon: '⚡' },
            { key: 'voltage_3_3v', label: '3.3V 电压', unit: 'V', icon: '⚡' }
        ],
        charts: [
            {
                id: 'cpuTempChart',
                title: '🌡️ CPU 温度（最近 5 分钟）',
                keys: ['cpu0_temperature', 'cpu1_temperature'],
                labels: ['CPU0', 'CPU1'],
                colors: ['rgb(255, 99, 132)', 'rgb(255, 159, 64)']
            },
            {
                id: 'systemTempChart',
                title: '🌡️ 系统温度（最近 5 分钟）',
                keys: ['motherboard_temperature', 'memory_temperature'],
                labels: ['M.2', '内存'],
                colors: ['rgb(54, 162, 235)', 'rgb(153, 102, 255)']
            },
            {
                id: 'fanSpeedChart',
                title: '💨 风扇转速（最近 5 分钟）',
                keys: ['cpu0_fan_speed', 'cpu1_fan_speed'],
                labels: ['CPU0 风扇', 'CPU1 风扇'],
                colors: ['rgb(75, 192, 192)', 'rgb(255, 205, 86)']
            },
            {
                id: 'voltageChart',
                title: '⚡ 电压监控（最近 5 分钟）',
                keys: ['voltage_12v', 'voltage_5v', 'voltage_3_3v'],
                labels: ['12V', '5V', '3.3V'],
                colors: ['rgb(255, 99, 132)', 'rgb(54, 162, 235)', 'rgb(75, 192, 192)']
            }
        ]
    }
};

// 初始化
window.addEventListener('DOMContentLoaded', async () => {
    console.log('🚀 初始化设备监控界面...');
    
    try {
        await loadDevices();
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
    
    if (devices.length === 0) {
        showError('未找到任何设备');
        throw new Error('未找到任何设备');
    }
    
    // 默认选择第一个设备
    currentDevice = devices[0];
    console.log('默认选择设备:', currentDevice.name);
    
    // 渲染设备选择器
    renderDeviceTabs();
    
    // 初始化当前设备的监控
    initializeDevice();
}

// 渲染设备选项卡
function renderDeviceTabs() {
    const tabsContainer = document.getElementById('deviceTabs');
    tabsContainer.innerHTML = '';
    
    devices.forEach(device => {
        const tab = document.createElement('button');
        tab.className = 'device-tab';
        
        // 添加设备类型图标
        const config = DEVICE_CONFIGS[device.type] || DEVICE_CONFIGS.SERVER;
        tab.textContent = `${config.icon} ${device.name}`;
        tab.onclick = () => selectDevice(device);
        
        if (device.id === currentDevice.id) {
            tab.classList.add('active');
        }
        
        tabsContainer.appendChild(tab);
    });
}

// 选择设备
function selectDevice(device) {
    console.log('📌 切换到设备:', device.name);
    
    currentDevice = device;
    
    // 更新选项卡样式
    document.querySelectorAll('.device-tab').forEach(tab => {
        tab.classList.remove('active');
    });
    event.target.classList.add('active');
    
    // 清理旧的定时器
    if (window.updateTimer) {
        clearInterval(window.updateTimer);
    }
    
    // 清理旧的图表
    Object.values(charts).forEach(chart => chart.destroy());
    charts = {};
    
    // 初始化新设备
    initializeDevice();
}

// 初始化设备监控
function initializeDevice() {
    const config = DEVICE_CONFIGS[currentDevice.type] || DEVICE_CONFIGS.SERVER;
    
    // 更新页面标题
    document.getElementById('headerIcon').textContent = config.icon;
    document.getElementById('headerTitle').textContent = config.title;
    document.getElementById('headerSubtitle').textContent = `${currentDevice.name} - ${config.subtitle}`;
    
    // 渲染指标卡片
    renderMetrics(config);
    
    // 渲染图表
    renderCharts(config);
    
    // 立即更新一次数据
    updateData();
    
    // 设置定时更新
    window.updateTimer = setInterval(updateData, config.updateInterval);
}

// 指标分组配置
const METRIC_GROUPS = {
    SERVER: [
        {
            label: 'CPU 温度',
            icon: '🌡️',
            metrics: [
                { key: 'cpu0_temperature', label: 'CPU0' },
                { key: 'cpu1_temperature', label: 'CPU1' }
            ],
            unit: '°C'
        },
        {
            label: '系统温度',
            icon: '🌡️',
            metrics: [
                { key: 'motherboard_temperature', label: 'M.2' },
                { key: 'memory_temperature', label: '内存' }
            ],
            unit: '°C'
        },
        {
            label: 'CPU 风扇',
            icon: '💨',
            metrics: [
                { key: 'cpu0_fan_speed', label: 'CPU0' },
                { key: 'cpu1_fan_speed', label: 'CPU1' }
            ],
            unit: 'RPM'
        },
        {
            label: '电压',
            icon: '⚡',
            metrics: [
                { key: 'voltage_12v', label: '12V' },
                { key: 'voltage_5v', label: '5V' },
                { key: 'voltage_3_3v', label: '3.3V' }
            ],
            unit: 'V'
        }
    ],
    NVIDIA_GPU: [
        {
            label: 'GPU 性能',
            icon: '📊',
            metrics: [
                { key: 'gpu_utilization', label: 'GPU利用率' },
                { key: 'power_usage', label: '功耗' }
            ],
            unit: { 'gpu_utilization': '%', 'power_usage': 'W' }
        },
        {
            label: 'GPU 温度',
            icon: '🌡️',
            metrics: [
                { key: 'gpu_temperature', label: '核心温度' },
                { key: 'memory_temperature', label: '显存温度' }
            ],
            unit: '°C'
        },
        {
            label: '显存',
            icon: '💾',
            metrics: [
                { key: 'memory_used', label: '已用' },
                { key: 'memory_free', label: '空闲' }
            ],
            unit: 'MiB'
        },
        {
            label: '时钟频率',
            icon: '🔧',
            metrics: [
                { key: 'sm_clock', label: 'SM时钟' },
                { key: 'memory_clock', label: '显存时钟' }
            ],
            unit: 'MHz'
        }
    ]
};

// 渲染指标卡片
function renderMetrics(config) {
    const grid = document.getElementById('metricsGrid');
    grid.innerHTML = '';
    
    // 如果有分组配置，使用分组渲染
    const groups = METRIC_GROUPS[currentDevice.type] || null;
    
    if (groups) {
        // 使用分组渲染
        groups.forEach(group => {
            const card = document.createElement('div');
            card.className = 'metric-card grouped';
            
            let rowsHtml = '';
            group.metrics.forEach(metric => {
                rowsHtml += `
                    <div class="metric-row">
                        <span class="metric-row-label">${metric.label}</span>
                        <span class="metric-value-inline" id="metric-${metric.key}">
                            --
                            <span class="metric-unit-inline">${typeof group.unit === 'string' ? group.unit : (group.unit[metric.key] || '')}</span>
                        </span>
                    </div>
                `;
            });
            
            card.innerHTML = `
                <div class="metric-label">${group.icon} ${group.label}</div>
                ${rowsHtml}
            `;
            grid.appendChild(card);
        });
        
        // 渲染未分组的其他指标（如果有）
        const groupedKeys = new Set();
        groups.forEach(group => {
            group.metrics.forEach(m => groupedKeys.add(m.key));
        });
        
        config.metrics.forEach(metric => {
            if (!groupedKeys.has(metric.key)) {
                const card = document.createElement('div');
                card.className = 'metric-card';
                card.innerHTML = `
                    <div class="metric-label">${metric.icon} ${metric.label}</div>
                    <div class="metric-value" id="metric-${metric.key}">
                        --
                        <span class="metric-unit">${metric.unit}</span>
                    </div>
                `;
                grid.appendChild(card);
            }
        });
    } else {
        // 原来的单指标渲染
        config.metrics.forEach(metric => {
            const card = document.createElement('div');
            card.className = 'metric-card';
            card.innerHTML = `
                <div class="metric-label">${metric.icon} ${metric.label}</div>
                <div class="metric-value" id="metric-${metric.key}">
                    --
                    <span class="metric-unit">${metric.unit}</span>
                </div>
            `;
            grid.appendChild(card);
        });
    }
}

// 渲染图表
function renderCharts(config) {
    const container = document.getElementById('chartsContainer');
    container.innerHTML = '';
    
    config.charts.forEach(chartConfig => {
        const chartDiv = document.createElement('div');
        chartDiv.className = 'chart-container';
        chartDiv.innerHTML = `
            <div class="chart-title">${chartConfig.title}</div>
            <canvas id="${chartConfig.id}"></canvas>
        `;
        container.appendChild(chartDiv);
        
        // 创建图表
        const ctx = document.getElementById(chartConfig.id).getContext('2d');
        const datasets = chartConfig.keys.map((key, index) => ({
            label: chartConfig.labels[index],
            data: [],
            borderColor: chartConfig.colors[index],
            backgroundColor: chartConfig.colors[index].replace('rgb', 'rgba').replace(')', ', 0.1)'),
            tension: 0.4,
            fill: true
        }));
        
        charts[chartConfig.id] = new Chart(ctx, {
            type: 'line',
            data: { datasets },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                aspectRatio: 2.5,
                scales: {
                    x: {
                        type: 'linear',
                        title: { display: true, text: '时间' },
                        ticks: {
                            callback: function(value) {
                                if (value === 0) return 'now';
                                const absValue = Math.abs(value);
                                if (absValue >= 60) {
                                    const minutes = Math.floor(absValue / 60);
                                    const seconds = absValue % 60;
                                    return seconds === 0 ? `-${minutes}分钟` : `-${minutes}分${seconds}秒`;
                                }
                                return `-${absValue}秒`;
                            }
                        }
                    },
                    y: {
                        beginAtZero: false,
                        title: { display: true, text: chartConfig.labels[0].includes('温度') ? '温度 (°C)' : 
                                                        chartConfig.labels[0].includes('风扇') ? '转速 (RPM)' :
                                                        chartConfig.labels[0].includes('电压') ? '电压 (V)' : '值' }
                    }
                },
                plugins: {
                    legend: { display: true, position: 'top' }
                }
            }
        });
    });
}

// 更新数据
async function updateData() {
    if (!currentDevice) return;
    
    try {
        const config = DEVICE_CONFIGS[currentDevice.type] || DEVICE_CONFIGS.SERVER;
        
        // 获取最新数据
        const response = await fetch(`/api/telemetry/${currentDevice.id}/latest`);
        const data = await response.json();
        
        if (!data.telemetry) {
            console.warn('无遥测数据');
            return;
        }
        
        // 更新指标卡片
        const groups = METRIC_GROUPS[currentDevice.type] || null;
        
        if (groups) {
            // 更新分组指标
            groups.forEach(group => {
                group.metrics.forEach(metric => {
                    const value = data.telemetry[metric.key];
                    const element = document.getElementById(`metric-${metric.key}`);
                    if (element && value !== undefined) {
                        const formattedValue = typeof value === 'number' ? value.toFixed(1) : value;
                        const unit = typeof group.unit === 'string' ? group.unit : (group.unit[metric.key] || '');
                        element.innerHTML = `${formattedValue} <span class="metric-unit-inline">${unit}</span>`;
                    }
                });
            });
            
            // 更新未分组的指标
            const groupedKeys = new Set();
            groups.forEach(group => {
                group.metrics.forEach(m => groupedKeys.add(m.key));
            });
            
            config.metrics.forEach(metric => {
                if (!groupedKeys.has(metric.key)) {
                    const value = data.telemetry[metric.key];
                    const element = document.getElementById(`metric-${metric.key}`);
                    if (element && value !== undefined) {
                        const formattedValue = typeof value === 'number' ? value.toFixed(1) : value;
                        element.innerHTML = `${formattedValue} <span class="metric-unit">${metric.unit}</span>`;
                    }
                }
            });
        } else {
            // 原来的更新方式
            config.metrics.forEach(metric => {
                const value = data.telemetry[metric.key];
                const element = document.getElementById(`metric-${metric.key}`);
                if (element && value !== undefined) {
                    const formattedValue = typeof value === 'number' ? value.toFixed(1) : value;
                    element.innerHTML = `${formattedValue} <span class="metric-unit">${metric.unit}</span>`;
                }
            });
        }
        
        // 更新图表
        await updateCharts(config);
        
        // 更新时间戳
        updateTimestamp();
        
    } catch (error) {
        console.error('更新数据失败:', error);
    }
}

// 更新图表
async function updateCharts(config) {
    const now = Date.now();
    const duration = 300; // 所有图表统一显示最近 5 分钟的数据
    
    for (const chartConfig of config.charts) {
        const chart = charts[chartConfig.id];
        if (!chart) continue;
        
        try {
            // 获取历史数据
            const datasets = await Promise.all(
                chartConfig.keys.map(async key => {
                    const response = await fetch(
                        `/api/telemetry/${currentDevice.id}/history/${key}?startTime=${now - duration * 1000}&endTime=${now}`
                    );
                    return await response.json();
                })
            );
            
            // 更新图表数据
            chart.data.datasets.forEach((dataset, index) => {
                const history = datasets[index] || [];
                dataset.data = history.map(item => ({
                    x: -(now - item.timestamp) / 1000, // 相对时间（秒）
                    y: item.value
                }));
            });
            
            chart.update('none');
            
        } catch (error) {
            console.error(`更新图表 ${chartConfig.id} 失败:`, error);
        }
    }
}

// 更新时间戳
function updateTimestamp() {
    const now = new Date();
    document.getElementById('updateTime').textContent = 
        `最后更新: ${now.toLocaleTimeString('zh-CN')}`;
}

// 显示错误
function showError(message) {
    const errorDiv = document.getElementById('error');
    errorDiv.textContent = message;
    errorDiv.style.display = 'block';
    
    setTimeout(() => {
        errorDiv.style.display = 'none';
    }, 5000);
}


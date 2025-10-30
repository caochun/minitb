let devices = [];
let currentDeviceId = null;

window.addEventListener('DOMContentLoaded', async () => {
    try {
        await loadDevices();
        setInterval(updateData, 3000);
        updateData();
    } catch (e) {
        showError('初始化失败: ' + e.message);
    }
});

async function loadDevices() {
    const res = await fetch('/api/devices');
    devices = await res.json();

    // 过滤 WEBSITE 类型；若无该类型，则回退到名称包含 Website 的设备
    let websiteDevices = devices.filter(d => d.type === 'WEBSITE');
    if (websiteDevices.length === 0) {
        websiteDevices = devices.filter(d => /website/i.test(d.name));
    }
    if (websiteDevices.length === 0) {
        throw new Error('未找到 Website 设备');
    }

    // 支持 query 指定 deviceId
    const q = window.__QUERY__;
    const qId = q && q.get('deviceId');
    if (qId && websiteDevices.some(d => d.id === qId)) {
        currentDeviceId = qId;
    } else {
        currentDeviceId = websiteDevices[0].id;
    }

    renderTabs(websiteDevices);
    // 渲染目标信息
    const cur = websiteDevices.find(d => d.id === currentDeviceId) || websiteDevices[0];
    document.getElementById('deviceName').textContent = cur.name;
    // 从名称推断目标（如果名称包含 URL）；实际目标显示由最新数据填充
}

function renderTabs(devs) {
    const el = document.getElementById('deviceTabs');
    el.innerHTML = '';
    devs.forEach(d => {
        const btn = document.createElement('button');
        btn.className = 'tab' + (d.id === currentDeviceId ? ' active' : '');
        btn.textContent = d.name;
        btn.onclick = () => { currentDeviceId = d.id; updateData(); updateActive(el, d.id); };
        el.appendChild(btn);
    });
}

function updateActive(el, id) {
    el.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
    const idx = Array.from(el.children).findIndex(b => b.textContent === (devices.find(x => x.id === id) || {}).name);
    if (idx >= 0) el.children[idx].classList.add('active');
}

async function updateData() {
    if (!currentDeviceId) return;
    try {
        const latest = await fetch(`/api/telemetry/${currentDeviceId}/latest`).then(r => r.json());
        const tel = latest.telemetry || {};

        const alive = Number(tel.website_alive);
        const httpCode = tel.http_status_code;

        // 状态点
        const dot = document.getElementById('aliveDot');
        dot.classList.remove('up', 'down');
        if (alive === 1) { dot.classList.add('up'); }
        else { dot.classList.add('down'); }
        document.getElementById('aliveText').textContent = alive === 1 ? 'Up' : 'Down';

        // HTTP code
        document.getElementById('httpCode').textContent = httpCode !== undefined ? httpCode : '--';

        // 目标展示：优先从 telemetry 的标签推断；否则展示设备名
        document.getElementById('target').textContent = (latest.deviceName || '').includes('http') ? latest.deviceName : 'http://www.js.sgcc.com.cn';
        document.getElementById('deviceName').textContent = latest.deviceName || '--';

        // 更新时间
        document.getElementById('updateTime').textContent = latest.timestamp ? new Date(latest.timestamp).toLocaleString() : '--';
        document.getElementById('status').textContent = '● 实时监控中';
    } catch (e) {
        document.getElementById('status').textContent = '● 连接失败';
        showError('拉取失败: ' + e.message);
    }
}

function showError(msg) {
    const el = document.getElementById('error');
    el.textContent = '❌ ' + msg;
    el.style.display = 'block';
    setTimeout(() => el.style.display = 'none', 4000);
}



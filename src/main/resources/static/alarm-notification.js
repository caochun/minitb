/**
 * 告警实时通知服务
 * 
 * 功能：
 * - 通过 SSE 接收后端推送的告警
 * - 在页面右上角显示弹窗通知
 * - 1 分钟后自动消失（或手动关闭）
 */

class AlarmNotificationService {
    constructor() {
        this.eventSource = null;
        this.notificationContainer = null;
        // 以 设备ID+告警类型 作为唯一键，确保同一设备同一类型的告警持久且不重复
        this.activeNotifications = new Map();
        this.init();
    }

    /**
     * 初始化通知服务
     */
    init() {
        // 创建通知容器
        this.createNotificationContainer();
        
        // 连接 SSE
        this.connectSSE();
        
        console.log('✅ 告警通知服务已启动');
    }

    /**
     * 创建通知容器（右上角）
     */
    createNotificationContainer() {
        this.notificationContainer = document.createElement('div');
        this.notificationContainer.id = 'alarm-notifications';
        this.notificationContainer.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            z-index: 9999;
            max-width: 400px;
        `;
        document.body.appendChild(this.notificationContainer);
    }

    /**
     * 连接 SSE 服务
     */
    connectSSE() {
        this.eventSource = new EventSource('/api/alarms/notifications/stream');

        this.eventSource.addEventListener('alarm', (event) => {
            const alarm = JSON.parse(event.data);
            this.showNotification(alarm);
        });

        this.eventSource.onerror = (error) => {
            console.error('SSE 连接错误:', error);
            // 5 秒后自动重连
            setTimeout(() => {
                console.log('尝试重新连接 SSE...');
                this.eventSource.close();
                this.connectSSE();
            }, 5000);
        };

        this.eventSource.onopen = () => {
            console.log('✅ SSE 连接已建立');
        };
    }

    /**
     * 显示告警通知
     */
    showNotification(alarm) {
        console.log('收到告警通知:', alarm);

        // 对于同一设备同一类型的告警，使用同一条通知进行更新，不创建重复项
        const key = `${alarm.deviceId || alarm.deviceName}:${alarm.type}`;
        let notification = this.activeNotifications.get(key);
        const isUpdate = Boolean(notification);
        if (!notification) {
            // 创建通知元素
            notification = document.createElement('div');
            notification.className = `alarm-notification alarm-${alarm.severity.toLowerCase()} alarm-${alarm.action}`;
            // 存储键值用于后续查找
            notification.dataset.alarmKey = key;
        } else {
            // 更新严重程度样式类
            notification.className = `alarm-notification alarm-${alarm.severity.toLowerCase()} alarm-${alarm.action}`;
        }
        
        // 根据 action 生成不同的图标和文本
        const actionInfo = this.getActionInfo(alarm.action, alarm.notificationCount || 1);
        
        // 根据严重程度设置背景色
        const bgColor = this.getSeverityColor(alarm.severity);
        
        notification.style.cssText = `
            background: ${bgColor};
            color: white;
            padding: 16px 20px;
            border-radius: 8px;
            margin-bottom: 12px;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
            animation: slideInRight 0.3s ease-out;
            cursor: pointer;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
        `;

        notification.innerHTML = `
            <div style="display: flex; flex-direction: column; gap: 12px;">
                <div style="display: flex; align-items: start; gap: 12px;">
                    <div style="font-size: 24px; flex-shrink: 0;">
                        ${actionInfo.icon}
                    </div>
                    <div style="flex: 1; min-width: 0;">
                        <div style="font-weight: 600; font-size: 15px; margin-bottom: 4px;">
                            ${actionInfo.title}
                        </div>
                        <div style="font-size: 14px; opacity: 0.95; margin-bottom: 6px;">
                            ${alarm.type}
                        </div>
                        <div style="font-size: 13px; opacity: 0.85;">
                            ${alarm.deviceName}
                        </div>
                    </div>
                    <!-- 移除手动关闭按钮，要求必须确认或忽略才消失 -->
                </div>
                <div style="display: flex; gap: 8px; padding-top: 8px; border-top: 1px solid rgba(255,255,255,0.2);">
                    <button onclick="handleAcknowledge('${alarm.id}', this)" 
                            style="flex: 1; padding: 8px 16px; background: rgba(255,255,255,0.9); 
                                   color: #2d3748; border: none; border-radius: 6px; 
                                   font-weight: 600; font-size: 13px; cursor: pointer;
                                   transition: all 0.2s;">
                        ✓ 已处理
                    </button>
                    <button onclick="handleIgnore('${alarm.id}', this)"
                            style="flex: 1; padding: 8px 16px; background: rgba(255,255,255,0.3); 
                                   color: white; border: 1px solid rgba(255,255,255,0.5); 
                                   border-radius: 6px; font-weight: 600; font-size: 13px; 
                                   cursor: pointer; transition: all 0.2s;">
                        ⊗ 忽略
                    </button>
                </div>
            </div>
        `;

        // 添加动画样式
        if (!document.getElementById('alarm-notification-styles')) {
            const style = document.createElement('style');
            style.id = 'alarm-notification-styles';
            style.textContent = `
                @keyframes slideInRight {
                    from {
                        transform: translateX(400px);
                        opacity: 0;
                    }
                    to {
                        transform: translateX(0);
                        opacity: 1;
                    }
                }
                @keyframes slideOutRight {
                    from {
                        transform: translateX(0);
                        opacity: 1;
                    }
                    to {
                        transform: translateX(400px);
                        opacity: 0;
                    }
                }
                .alarm-notification:hover {
                    transform: scale(1.02);
                    transition: transform 0.2s ease;
                }
                .alarm-notification button:hover {
                    transform: translateY(-1px);
                    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
                }
                .alarm-notification button:active {
                    transform: translateY(0);
                }
                .alarm-notification button:disabled {
                    opacity: 0.6;
                    cursor: not-allowed;
                }
            `;
            document.head.appendChild(style);
        }

        // 添加或更新到容器，且不自动消失（直到用户确认或忽略）
        if (!isUpdate) {
            this.notificationContainer.appendChild(notification);
        }
        // 记录活动通知
        this.activeNotifications.set(key, notification);
    }

    /**
     * 处理用户确认告警
     */
    acknowledgeAlarm(alarmId, notificationElement) {
        console.log('确认告警:', alarmId);
        
        // 调用后端 API 确认告警
        fetch(`/api/alarms/${alarmId}/ack`, {
            method: 'POST'
        })
        .then(response => {
            if (response.ok) {
                console.log('✅ 告警已确认');
                // 显示成功反馈
                this.showSuccessFeedback(notificationElement, '✓ 已确认');
                // 2秒后关闭弹窗
                setTimeout(() => {
                    this.closeNotification(notificationElement);
                }, 2000);
            } else {
                console.error('确认告警失败');
                alert('确认告警失败，请重试');
            }
        })
        .catch(error => {
            console.error('确认告警错误:', error);
            alert('确认告警失败: ' + error.message);
        });
    }

    /**
     * 处理用户忽略告警（清除）
     */
    ignoreAlarm(alarmId, notificationElement) {
        console.log('忽略告警:', alarmId);
        
        // 调用后端 API 清除告警
        fetch(`/api/alarms/${alarmId}/clear`, {
            method: 'POST'
        })
        .then(response => {
            if (response.ok) {
                console.log('✅ 告警已忽略（清除）');
                // 显示成功反馈
                this.showSuccessFeedback(notificationElement, '⊗ 已忽略');
                // 2秒后关闭弹窗
                setTimeout(() => {
                    this.closeNotification(notificationElement);
                }, 2000);
            } else {
                console.error('忽略告警失败');
                alert('忽略告警失败，请重试');
            }
        })
        .catch(error => {
            console.error('忽略告警错误:', error);
            alert('忽略告警失败: ' + error.message);
        });
    }

    /**
     * 显示成功反馈
     */
    showSuccessFeedback(notificationElement, message) {
        const buttons = notificationElement.querySelectorAll('button');
        buttons.forEach(btn => btn.disabled = true);
        
        const feedback = document.createElement('div');
        feedback.style.cssText = `
            text-align: center;
            padding: 8px;
            font-weight: 600;
            font-size: 14px;
            color: white;
        `;
        feedback.textContent = message;
        
        const buttonContainer = notificationElement.querySelector('[style*="border-top"]');
        buttonContainer.innerHTML = '';
        buttonContainer.appendChild(feedback);
    }

    /**
     * 关闭通知弹窗
     */
    closeNotification(notificationElement) {
        // 从活动集合中移除
        const key = notificationElement.dataset.alarmKey;
        if (key && this.activeNotifications.has(key)) {
            this.activeNotifications.delete(key);
        }

        notificationElement.style.animation = 'slideOutRight 0.3s ease-out';
        setTimeout(() => {
            notificationElement.remove();
        }, 300);
    }

    /**
     * 获取 action 对应的图标和标题
     */
    getActionInfo(action, notificationCount) {
        switch (action) {
            case 'created':
                return {
                    icon: '⚠️',
                    title: '新告警'
                };
            case 'updated':
                return {
                    icon: '🔄',
                    title: '告警已更新'
                };
            case 'cleared':
                return {
                    icon: '✅',
                    title: '告警已清除'
                };
            case 'repeat':
                return {
                    icon: '🔔',
                    title: `告警提醒（第 ${notificationCount} 次）`
                };
            default:
                return {
                    icon: '📢',
                    title: '告警通知'
                };
        }
    }

    /**
     * 获取严重程度对应的颜色
     */
    getSeverityColor(severity) {
        switch (severity) {
            case 'CRITICAL':
                return 'linear-gradient(135deg, #dc2626 0%, #991b1b 100%)';  // 红色
            case 'MAJOR':
                return 'linear-gradient(135deg, #ea580c 0%, #c2410c 100%)';  // 橙色
            case 'MINOR':
                return 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)';  // 黄色
            case 'WARNING':
                return 'linear-gradient(135deg, #3b82f6 0%, #2563eb 100%)';  // 蓝色
            default:
                return 'linear-gradient(135deg, #6b7280 0%, #4b5563 100%)';  // 灰色
        }
    }

    /**
     * 销毁服务
     */
    destroy() {
        if (this.eventSource) {
            this.eventSource.close();
        }
        if (this.notificationContainer) {
            this.notificationContainer.remove();
        }
        console.log('告警通知服务已停止');
    }
}

// 全局实例
let alarmNotificationService = null;

// 全局处理函数（供 HTML onclick 调用）
function handleAcknowledge(alarmId, buttonElement) {
    const notification = buttonElement.closest('.alarm-notification');
    if (alarmNotificationService && notification) {
        alarmNotificationService.acknowledgeAlarm(alarmId, notification);
    }
}

function handleIgnore(alarmId, buttonElement) {
    const notification = buttonElement.closest('.alarm-notification');
    if (alarmNotificationService && notification) {
        alarmNotificationService.ignoreAlarm(alarmId, notification);
    }
}

// 页面加载完成后自动启动
document.addEventListener('DOMContentLoaded', () => {
    alarmNotificationService = new AlarmNotificationService();
});

// 页面卸载时清理
window.addEventListener('beforeunload', () => {
    if (alarmNotificationService) {
        alarmNotificationService.destroy();
    }
});


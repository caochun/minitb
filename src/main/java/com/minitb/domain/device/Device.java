package com.minitb.domain.device;

import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备实体
 * 
 * 职责：
 * - 代表一个具体的物理或逻辑设备实例
 * - 存储设备级别的连接信息（通过 DeviceConfiguration）
 * - 关联到 DeviceProfile（定义监控能力）
 * 
 * 设计原则：
 * - Device 保持简洁，不为每种数据源类型添加字段
 * - 使用 DeviceConfiguration 策略模式存储不同类型的配置
 * - 配置存储为 JSON，运行时反序列化为具体类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    private DeviceId id;
    private String name;
    private String type;
    private String accessToken;
    private long createdTime;
    
    /**
     * 设备配置文件ID（强类型）
     * 定义了设备应该有哪些遥测数据
     */
    private DeviceProfileId deviceProfileId;
    
    /**
     * 设备配置（策略模式）
     * 
     * 不同类型的设备需要不同的连接配置：
     * - Prometheus 设备: PrometheusDeviceConfiguration（endpoint, label）
     * - IPMI 设备: IpmiDeviceConfiguration（host, username, password, driver）
     * - MQTT 设备: MqttDeviceConfiguration（broker, topic, clientId）
     * - HTTP 设备: HttpDeviceConfiguration（endpoint, authToken）
     * 
     * 序列化为 JSON 存储到数据库，反序列化时根据 type 字段识别具体类型
     * 类型信息已在 DeviceConfiguration 接口上配置
     */
    private DeviceConfiguration configuration;

    public Device(String name, String type, String accessToken) {
        this.id = DeviceId.random();
        this.name = name;
        this.type = type;
        this.accessToken = accessToken;
        this.createdTime = System.currentTimeMillis();
    }
    
    public Device(String name, String type, String accessToken, DeviceProfileId deviceProfileId) {
        this(name, type, accessToken);
        this.deviceProfileId = deviceProfileId;
    }
}

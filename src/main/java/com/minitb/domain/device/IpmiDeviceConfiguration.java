package com.minitb.domain.device;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IPMI 设备配置
 * 
 * 存储连接到具体 BMC（Baseboard Management Controller）所需的信息：
 * - host: BMC IP 地址
 * - username: IPMI 用户名
 * - password: IPMI 密码
 * - driver: IPMI 驱动类型
 * 
 * 示例：
 * <pre>
 * IpmiDeviceConfiguration config = IpmiDeviceConfiguration.builder()
 *     .host("114.212.81.58")
 *     .username("admin")
 *     .password("OGC61700147")
 *     .driver("LAN_2_0")
 *     .build();
 * </pre>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IpmiDeviceConfiguration implements DeviceConfiguration {
    
    /**
     * BMC IP 地址
     * 例如: "114.212.81.58", "192.168.1.116"
     */
    private String host;
    
    /**
     * IPMI 用户名
     * 常见默认值:
     * - Supermicro: "ADMIN"
     * - Dell iDRAC: "root"
     * - HP iLO: "Administrator"
     */
    private String username;
    
    /**
     * IPMI 密码
     */
    private String password;
    
    /**
     * IPMI 驱动类型
     * 常见值:
     * - "LAN_2_0" : IPMI v2.0 over LAN（推荐）
     * - "LAN" : IPMI v1.5 over LAN
     * - "OPEN" : 本地 OpenIPMI
     */
    private String driver;
    
    @Override
    @JsonIgnore  // 不序列化此方法，避免与 @JsonTypeInfo 的 type 字段冲突
    public String getConfigurationType() {
        return "IPMI";
    }
}


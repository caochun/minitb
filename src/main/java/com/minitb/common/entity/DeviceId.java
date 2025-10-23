package com.minitb.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 设备ID
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceId {
    private UUID id;

    public static DeviceId fromUUID(UUID uuid) {
        return new DeviceId(uuid);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}

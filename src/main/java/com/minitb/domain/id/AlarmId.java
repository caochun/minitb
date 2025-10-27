package com.minitb.domain.id;

import java.util.UUID;

/**
 * 告警ID
 */
public class AlarmId extends EntityId {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 构造器：使用指定UUID
     */
    public AlarmId(UUID id) {
        super(id);
    }
    
    /**
     * 工厂方法：从UUID创建
     */
    public static AlarmId fromUUID(UUID uuid) {
        return new AlarmId(uuid);
    }
    
    /**
     * 工厂方法：从字符串创建
     */
    public static AlarmId fromString(String alarmId) {
        return new AlarmId(UUID.fromString(alarmId));
    }
    
    /**
     * 工厂方法：生成随机ID
     */
    public static AlarmId random() {
        return new AlarmId(UUID.randomUUID());
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.ALARM;
    }
}



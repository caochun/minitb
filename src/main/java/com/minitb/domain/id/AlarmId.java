package com.minitb.domain.id;

import java.util.UUID;

/**
 * 告警ID（强类型）
 */
public class AlarmId extends EntityId {
    
    public AlarmId(UUID id) {
        super(id);
    }
    
    public static AlarmId random() {
        return new AlarmId(UUID.randomUUID());
    }
    
    public static AlarmId fromString(String id) {
        return new AlarmId(UUID.fromString(id));
    }
    
    @Override
    public EntityType getEntityType() {
        return EntityType.ALARM;
    }
}

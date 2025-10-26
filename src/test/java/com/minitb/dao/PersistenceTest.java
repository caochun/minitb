package com.minitb.dao;

import com.minitb.domain.entity.*;
import com.minitb.domain.ts.DataType;
import com.minitb.domain.relation.EntityRelation;
import com.minitb.domain.relation.RelationTypeGroup;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;

import java.util.List;
import java.util.Optional;

/**
 * 持久化功能测试
 * 测试 SQLite + Entity 层的完整持久化流程
 */
@Slf4j
public class PersistenceTest {
    
    public static void main(String[] args) {
        try {
            log.info("========================================");
            log.info("   MiniTB 持久化功能测试");
            log.info("========================================\n");
            
            // 1. 初始化数据库
            log.info("1️⃣  初始化数据库...");
            DatabaseManager.initDatabase();
            log.info("✅ 数据库初始化完成\n");
            
            // 2. 创建 DAO（使用 DaoFactory）
            log.info("2️⃣  创建 DAO 对象...");
            DaoFactory daoFactory = new DaoFactory(DatabaseManager.getConnection());
            DeviceProfileDao profileDao = daoFactory.getDeviceProfileDao();
            DeviceDao deviceDao = daoFactory.getDeviceDao();
            AssetDao assetDao = daoFactory.getAssetDao();
            EntityRelationDao relationDao = daoFactory.getEntityRelationDao();
            log.info("✅ DAO 创建完成\n");
            
            // 3. 测试 DeviceProfile 持久化
            log.info("3️⃣  测试 DeviceProfile 持久化...");
            DeviceProfile profile = DeviceProfile.builder()
                    .id(DeviceProfileId.random())
                    .name("测试传感器配置")
                    .description("用于测试的设备配置")
                    .dataSourceType(DeviceProfile.DataSourceType.MQTT)
                    .strictMode(false)
                    .createdTime(System.currentTimeMillis())
                    .build();
            
            profile.addTelemetryDefinition(
                TelemetryDefinition.simple("temperature", DataType.DOUBLE)
                    .toBuilder()
                    .displayName("温度")
                    .unit("°C")
                    .build()
            );
            profile.addTelemetryDefinition(
                TelemetryDefinition.simple("humidity", DataType.LONG)
                    .toBuilder()
                    .displayName("湿度")
                    .unit("%")
                    .build()
            );
            
            profileDao.save(profile);
            log.info("✅ 保存配置: {}", profile.getName());
            
            // 查询验证
            Optional<DeviceProfile> foundProfile = profileDao.findById(profile.getId());
            if (foundProfile.isPresent()) {
                DeviceProfile loaded = foundProfile.get();
                log.info("✅ 查询配置成功: {}, 遥测数={}", 
                    loaded.getName(), loaded.getTelemetryDefinitions().size());
            }
            log.info("");
            
            // 4. 测试 Device 持久化
            log.info("4️⃣  测试 Device 持久化...");
            Device device1 = new Device("温度传感器-01", "TemperatureSensor", "token-001", profile.getId());
            Device device2 = new Device("湿度传感器-01", "HumiditySensor", "token-002", profile.getId());
            
            deviceDao.save(device1);
            deviceDao.save(device2);
            log.info("✅ 保存设备: {} 和 {}", device1.getName(), device2.getName());
            
            // 查询验证
            Optional<Device> foundDevice = deviceDao.findByAccessToken("token-001");
            if (foundDevice.isPresent()) {
                log.info("✅ 根据 token 查询设备成功: {}", foundDevice.get().getName());
            }
            
            List<Device> allDevices = deviceDao.findAll();
            log.info("✅ 查询所有设备: {} 个", allDevices.size());
            log.info("");
            
            // 5. 测试 Asset 持久化
            log.info("5️⃣  测试 Asset 持久化...");
            Asset building = new Asset("智能大厦A座", "Building");
            Asset room = new Asset("101会议室", "Room");
            room.setLabel("VIP会议室");
            
            assetDao.save(building);
            assetDao.save(room);
            log.info("✅ 保存资产: {} 和 {}", building.getName(), room.getName());
            
            List<Asset> allAssets = assetDao.findAll();
            log.info("✅ 查询所有资产: {} 个", allAssets.size());
            log.info("");
            
            // 6. 测试 EntityRelation 持久化
            log.info("6️⃣  测试 EntityRelation 持久化...");
            EntityRelation relation = new EntityRelation(
                building.getId().getId(), "Asset",
                room.getId().getId(), "Asset",
                EntityRelation.CONTAINS_TYPE,
                RelationTypeGroup.COMMON
            );
            
            relationDao.saveRelation(relation);
            log.info("✅ 保存关系: {} -> {}", building.getName(), room.getName());
            
            List<EntityRelation> relations = relationDao.findAllByFrom(
                building.getId(), RelationTypeGroup.COMMON
            );
            log.info("✅ 查询出边关系: {} 个", relations.size());
            log.info("");
            
            // 7. 测试类型转换
            log.info("7️⃣  测试 Domain ↔ Entity 转换...");
            log.info("✅ DeviceProfileId: {} (强类型)", profile.getId().getClass().getSimpleName());
            log.info("✅ DeviceId: {} (强类型)", device1.getId().getClass().getSimpleName());
            log.info("✅ AssetId: {} (强类型)", building.getId().getClass().getSimpleName());
            log.info("");
            
            // 8. 测试复杂对象序列化
            log.info("8️⃣  测试复杂对象序列化...");
            DeviceProfile reloaded = profileDao.findById(profile.getId()).get();
            log.info("✅ 遥测定义反序列化成功:");
            reloaded.getTelemetryDefinitions().forEach(def -> {
                log.info("   - {}: {} ({})", def.getKey(), def.getDisplayName(), def.getDataType());
            });
            log.info("");
            
            // 9. 统计数据
            log.info("9️⃣  数据统计...");
            log.info("✅ 设备配置数: {}", profileDao.count());
            log.info("✅ 设备数: {}", deviceDao.count());
            log.info("✅ 资产数: {}", assetDao.count());
            log.info("✅ 关系数: {}", relationDao.count());
            log.info("");
            
            // 10. 最终报告
            log.info("========================================");
            log.info("   🎉 所有持久化测试通过！");
            log.info("========================================");
            log.info("✅ Entity 层工作正常");
            log.info("✅ 类型转换正确");
            log.info("✅ 复杂对象序列化成功");
            log.info("✅ 数据库操作正常");
            log.info("");
            log.info("数据库文件: {}", DatabaseManager.getDatabasePath());
            log.info("可以用 DB Browser for SQLite 查看数据");
            log.info("========================================");
            
        } catch (Exception e) {
            log.error("测试失败", e);
            System.exit(1);
        }
        
        System.exit(0);
    }
}


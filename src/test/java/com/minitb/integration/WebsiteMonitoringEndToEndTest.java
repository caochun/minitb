package com.minitb.integration;

import com.minitb.actor.MiniTbActorSystem;
import com.minitb.application.service.DeviceService;
import com.minitb.datasource.prometheus.PrometheusDataPuller;
import com.minitb.domain.device.Device;
import com.minitb.domain.device.DeviceProfile;
import com.minitb.domain.device.PrometheusDeviceConfiguration;
import com.minitb.domain.device.TelemetryDefinition;
import com.minitb.domain.id.DeviceId;
import com.minitb.domain.id.DeviceProfileId;
import com.minitb.domain.protocol.PrometheusConfig;
import com.minitb.domain.telemetry.DataType;
import com.minitb.domain.telemetry.TsKvEntry;
import com.minitb.storage.TelemetryStorage;
import org.junit.jupiter.api.BeforeEach;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Website 可用性监控 端到端测试
 * 
 * 测试链路：
 * Prometheus (blackbox_exporter) → PrometheusDataPuller → TransportService → DeviceActor → RuleEngine → TelemetryStorage
 * 
 * 前置条件：
 * - 本地运行 Prometheus (http://localhost:9090)，并已按项目根目录 prometheus-blackbox.yml 配置 blackbox 目标
 * - 本地运行 blackbox_exporter (http://localhost:9115)
 * - 设置环境变量 PROMETHEUS_ENABLED=true
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "PROMETHEUS_ENABLED", matches = "true")
@Slf4j
class WebsiteMonitoringEndToEndTest {

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private PrometheusDataPuller prometheusDataPuller;


    @Autowired
    private MiniTbActorSystem actorSystem;

    @Autowired
    private TelemetryStorage telemetryStorage;

    private static DeviceProfileId profileId;
    private static DeviceId deviceId;
    private static boolean initialized = false;

    private static final String PROMETHEUS_ENDPOINT = "http://localhost:9090";
    private static final String TARGET_INSTANCE = "http://www.js.sgcc.com.cn"; // 与 prometheus-blackbox.yml 对齐

    @BeforeEach
    void setUp() throws Exception {
        if (initialized) {
            return;
        }

        assertTrue(checkPrometheusAvailable(), "Prometheus 不可用，或未启动于 " + PROMETHEUS_ENDPOINT);

        // 1) 创建 Website 监控 Profile
        DeviceProfile profile = DeviceProfile.builder()
                .id(DeviceProfileId.random())
                .name("Website Uptime Monitor (Test)")
                .description("端到端测试用网站可用性监控配置")
                .dataSourceType(DeviceProfile.DataSourceType.PROMETHEUS)
                .prometheusDeviceLabelKey("instance")
                .strictMode(true)
                .telemetryDefinitions(createWebsiteTelemetryDefinitions())
                .createdTime(System.currentTimeMillis())
                .build();

        profileId = deviceService.saveProfile(profile).getId();

        // 2) 创建设备，使用 instance 标签绑定到 blackbox 目标
        Device device = Device.builder()
                .id(DeviceId.random())
                .name("JS SGCC Website (E2E)")
                .type("WEBSITE")
                .deviceProfileId(profileId)
                .accessToken("website-e2e-token-" + System.currentTimeMillis())
                .configuration(PrometheusDeviceConfiguration.builder()
                        .endpoint(PROMETHEUS_ENDPOINT)
                        .label("instance=" + TARGET_INSTANCE)
                        .build())
                .createdTime(System.currentTimeMillis())
                .build();

        Device saved = deviceService.save(device);
        deviceId = saved.getId();

        // 3) 注册 DeviceActor
        com.minitb.actor.device.DeviceActor deviceActor =
                new com.minitb.actor.device.DeviceActor(saved.getId(), saved);
        actorSystem.createActor(deviceActor.getActorId(), deviceActor);

        initialized = true;
    }

    @Test
    void testWebsiteUptimeEndToEnd() throws Exception {
        long t0 = System.currentTimeMillis();

        // 第 1 步：确认 Prometheus 能查到 blackbox 的 probe_success
        String query = "probe_success{job=\"blackbox-http\", instance=\"" + TARGET_INSTANCE + "\"}";
        HttpClient client = HttpClient.newHttpClient();
        String url = PROMETHEUS_ENDPOINT + "/api/v1/query?query=" + java.net.URLEncoder.encode(query, "UTF-8");
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "Prometheus 查询应该成功");
        assertTrue(resp.body().contains("\"result\":"), "应该有查询结果");
        log.info("===== Prometheus 查询结果 (probe_success) =====\n{}\n===============================================", resp.body());

        // 第 2 步：触发拉取
        prometheusDataPuller.pullAllPrometheusDevices();
        Thread.sleep(150);

        long t1 = System.currentTimeMillis();

        // 第 3 步：验证三个指标都写入存储并打印
        printLatest("website_alive", t0, t1);
        printLatest("http_status_code", t0, t1);
        // 证书剩余天数：HTTP 目标可能没有，此处不强制断言有值，但若存在则应为 DOUBLE
        List<TsKvEntry> sslDays = telemetryStorage.query(deviceId, "ssl_days_to_expiry", t0, t1);
        if (!sslDays.isEmpty()) {
            TsKvEntry latest = sslDays.get(sslDays.size() - 1);
            assertEquals(DataType.DOUBLE, latest.getDataType());
            log.info("ssl_days_to_expiry => ts={}, value={}", latest.getTs(), latest.getValue());
        }
    }

    // ==================== Helper ====================

    private List<TelemetryDefinition> createWebsiteTelemetryDefinitions() {
        List<TelemetryDefinition> defs = new ArrayList<>();

        defs.add(TelemetryDefinition.builder()
                .key("website_alive")
                .displayName("网站可达")
                .dataType(DataType.DOUBLE)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("probe_success{job=\\\"blackbox-http\\\", instance=\\\"" + TARGET_INSTANCE + "\\\"}")
                        .build())
                .build());

        defs.add(TelemetryDefinition.builder()
                .key("http_status_code")
                .displayName("HTTP状态码")
                .dataType(DataType.DOUBLE)
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("probe_http_status_code{job=\\\"blackbox-http\\\", instance=\\\"" + TARGET_INSTANCE + "\\\"}")
                        .build())
                .build());

        defs.add(TelemetryDefinition.builder()
                .key("ssl_days_to_expiry")
                .displayName("证书剩余天数")
                .dataType(DataType.DOUBLE)
                .unit("days")
                .protocolConfig(PrometheusConfig.builder()
                        .promQL("(probe_ssl_earliest_cert_expiry{job=\\\"blackbox-http\\\", instance=\\\"" + TARGET_INSTANCE + "\\\"} - time()) / 86400")
                        .build())
                .build());

        return defs;
    }

    private void printLatest(String key, long from, long to) {
        List<TsKvEntry> data = telemetryStorage.query(deviceId, key, from, to);
        assertFalse(data.isEmpty(), key + " 应该被持久化");
        TsKvEntry latest = data.get(data.size() - 1);
        assertNotNull(latest.getValue());
        assertEquals(DataType.DOUBLE, latest.getDataType());
        log.info("{} => ts={}, value={}", key, latest.getTs(), latest.getValue());
    }

    private boolean checkPrometheusAvailable() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PROMETHEUS_ENDPOINT + "/api/v1/status/config"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}



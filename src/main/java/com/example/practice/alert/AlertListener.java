package com.example.practice.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 簡易告警服務：偵測「A 事件後 X 分鐘內 B 事件」。
 *
 * 規則（可自行調整）：
 * - eventType=MAIN_FAIL 視為 A，記錄時間戳。
 * - eventType=BACKUP_FAIL 視為 B，若在 WINDOW_MS 內曾收到同 deviceId 的 A，則觸發告警。
 *
 * 注意：
 * - 解析欄位採用 GELF 自訂欄位慣例，優先取 _deviceId/_eventType，若沒有再取 deviceId/eventType。
 * - 只在 kafka profile 啟用。
 */
@Component
@Profile("kafka")
public class AlertListener {

    private static final Logger log = LoggerFactory.getLogger(AlertListener.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 窗口 5 分鐘（毫秒），可依需求調整
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    // 記錄 A 事件時間戳：key=deviceId, value=ts
    private final ConcurrentMap<String, Long> mainFailCache = new ConcurrentHashMap<>();

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.topic:gelf-logs}")
    private String topic;

    @Value("${spring.kafka.consumer.group-id:alert-service}")
    private String groupId;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private KafkaConsumer<String, String> consumer;

    @PostConstruct
    public void start() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(topic));

        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "alert-listener");
            t.setDaemon(true);
            return t;
        });
        executor.submit(this::pollLoop);
        log.info("AlertListener started. bootstrapServers={}, topic={}, groupId={}", bootstrapServers, topic, groupId);
    }

    private void pollLoop() {
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                long now = System.currentTimeMillis();
                // 清除過期的 A 事件
                mainFailCache.entrySet().removeIf(e -> now - e.getValue() > WINDOW_MS);

                for (ConsumerRecord<String, String> rec : records) {
                    Event evt = parseEvent(rec.value());
                    if (evt == null) continue;
                    handleEvent(evt, now);
                }
                consumer.commitAsync();
            }
        } catch (WakeupException ignore) {
            // shutting down
        } catch (Exception e) {
            log.error("AlertListener poll loop error", e);
        } finally {
            try {
                consumer.close();
            } catch (Exception ignore) {}
        }
    }

    private void handleEvent(Event evt, long now) {
        switch (evt.eventType) {
            case "MAIN_FAIL":
                mainFailCache.put(evt.deviceId, now);
                log.info("記錄 MAIN_FAIL, deviceId={}, ts={}", evt.deviceId, Instant.ofEpochMilli(now));
                break;
            case "BACKUP_FAIL":
                Long ts = mainFailCache.get(evt.deviceId);
                if (ts != null && (now - ts <= WINDOW_MS)) {
                    log.warn("【告警】主電 + 備用電同時故障 deviceId={} (within {} ms)", evt.deviceId, WINDOW_MS);
                    // TODO: 在這裡觸發 webhook/email 或寫回 Graylog
                } else {
                    log.info("收到 BACKUP_FAIL，但窗口內沒有 MAIN_FAIL, deviceId={}", evt.deviceId);
                }
                break;
            default:
                // 其他事件略過
        }
    }

    private Event parseEvent(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String deviceId = text(root, "_deviceId", "deviceId");
            String eventType = text(root, "_eventType", "eventType");
            if (deviceId == null || eventType == null) {
                return null;
            }
            return new Event(deviceId, eventType);
        } catch (Exception e) {
            log.debug("解析事件失敗，略過: {}", e.getMessage());
            return null;
        }
    }

    private String text(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull()) {
                String v = n.asText(null);
                if (v != null && !v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    private record Event(String deviceId, String eventType) {}
}


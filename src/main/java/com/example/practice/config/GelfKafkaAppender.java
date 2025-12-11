package com.example.practice.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.WarnStatus;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Future;

/**
 * 自訂的 Logback Appender，將 GELF 格式的日誌發送到 Kafka
 * 
 * <p><strong>為什麼使用自訂 Appender？</strong>
 * <ul>
 *   <li>✅ <strong>避免依賴已歸檔庫</strong>：logback-kafka-appender 已於 2022 年停止維護，有長期風險</li>
 *   <li>✅ <strong>完全控制</strong>：可以根據需求自訂 Kafka Producer 配置和錯誤處理</li>
 *   <li>✅ <strong>學習價值高</strong>：深入理解 Kafka Producer API 和 GELF 格式</li>
 *   <li>✅ <strong>與 GELF Encoder 完美整合</strong>：直接使用 de.siegmar.logbackgelf.GelfEncoder</li>
 * </ul>
 * 
 * <p><strong>效能優化建議</strong>：
 * <ul>
 *   <li>使用 {@link ch.qos.logback.classic.AsyncAppender} 包裝此 Appender，避免阻塞應用程式</li>
 *   <li>配置適當的 Kafka Producer 批次大小和壓縮</li>
 *   <li>設定 ignoreExceptions=true（預設），避免日誌發送失敗影響應用程式</li>
 * </ul>
 * 
 * <p>使用方式：
 * 在 logback-spring.xml 中配置：
 * <pre>{@code
 * <appender name="GELF_KAFKA" class="com.example.practice.config.GelfKafkaAppender">
 *     <encoder class="de.siegmar.logbackgelf.GelfEncoder">
 *         ...
 *     </encoder>
 *     <topic>gelf-logs</topic>
 *     <bootstrapServers>localhost:9092</bootstrapServers>
 * </appender>
 * }</pre>
 * 
 * @author Graylog Practice Project
 * @version 1.0
 */
public class GelfKafkaAppender extends AppenderBase<ILoggingEvent> {

    private Encoder<ILoggingEvent> encoder;
    private String topic;
    private String bootstrapServers = "localhost:9092";
    private Producer<String, String> producer;
    private boolean ignoreExceptions = true; // 預設忽略異常，避免影響應用程式

    @Override
    public void start() {
        if (encoder == null) {
            addStatus(new ErrorStatus("No encoder set for the appender named [" + name + "].", this));
            return;
        }
        if (topic == null || topic.isEmpty()) {
            addStatus(new ErrorStatus("No topic set for the appender named [" + name + "].", this));
            return;
        }
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            addStatus(new ErrorStatus("No bootstrapServers set for the appender named [" + name + "].", this));
            return;
        }

        try {
            // 初始化 Kafka Producer
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            
            // 生產環境推薦配置
            props.put(ProducerConfig.ACKS_CONFIG, "1"); // 等待 leader 確認
            props.put(ProducerConfig.RETRIES_CONFIG, 3); // 重試 3 次
            props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // 允許最多 5 個未確認的請求
            props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // 使用 snappy 壓縮
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 批次大小 16KB
            props.put(ProducerConfig.LINGER_MS_CONFIG, 1); // 等待 1ms 以批次發送
            props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 緩衝區 32MB
            
            // 客戶端 ID（用於識別）
            props.put(ProducerConfig.CLIENT_ID_CONFIG, "gelf-kafka-appender-" + name);
            
            // 連接超時設定
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
            props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);

            producer = new KafkaProducer<>(props);
            encoder.start();
            
            addStatus(new InfoStatus("Kafka producer started for appender [" + name + "] with topic [" + topic + "]", this));
            super.start();
        } catch (Exception e) {
            addStatus(new ErrorStatus("Failed to start Kafka producer for appender named [" + name + "].", this, e));
            if (!ignoreExceptions) {
                throw new RuntimeException("Failed to start Kafka producer", e);
            }
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        try {
            // 使用 encoder 將日誌事件編碼為 GELF 格式（JSON 字串）
            byte[] encodedBytes = encoder.encode(event);
            if (encodedBytes == null || encodedBytes.length == 0) {
                addStatus(new WarnStatus("Encoded message is empty for appender [" + name + "]", this));
                return;
            }
            
            String gelfMessage = new String(encodedBytes, StandardCharsets.UTF_8);

            // 發送到 Kafka（異步發送，不阻塞）
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, gelfMessage);
            Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    // 只在非 ignoreExceptions 模式下記錄錯誤
                    if (!ignoreExceptions) {
                        addStatus(new ErrorStatus(
                            "Failed to send log message to Kafka topic [" + topic + "].", 
                            this, exception));
                    } else {
                        // 在 ignoreExceptions 模式下，只記錄警告
                        addStatus(new WarnStatus(
                            "Failed to send log message to Kafka topic [" + topic + "]: " + exception.getMessage(), 
                            this));
                    }
                }
            });
            
            // 可選：如果需要同步確認，可以調用 future.get()（不推薦，會阻塞）
            // future.get();
            
        } catch (Exception e) {
            if (!ignoreExceptions) {
                addStatus(new ErrorStatus(
                    "Error appending log event to Kafka for appender named [" + name + "].", 
                    this, e));
                throw new RuntimeException("Failed to append log to Kafka", e);
            } else {
                addStatus(new WarnStatus(
                    "Error appending log event to Kafka for appender named [" + name + "]: " + e.getMessage(), 
                    this));
            }
        }
    }

    @Override
    public void stop() {
        if (producer != null) {
            try {
                // 刷新所有待發送的訊息
                producer.flush();
                // 關閉 producer（會等待所有待發送的訊息完成）
                producer.close();
                addStatus(new InfoStatus("Kafka producer closed for appender [" + name + "]", this));
            } catch (Exception e) {
                addStatus(new ErrorStatus(
                    "Error closing Kafka producer for appender named [" + name + "].", 
                    this, e));
            } finally {
                producer = null;
            }
        }
        if (encoder != null) {
            encoder.stop();
        }
        super.stop();
    }

    // Getters and Setters
    public Encoder<ILoggingEvent> getEncoder() {
        return encoder;
    }

    public void setEncoder(Encoder<ILoggingEvent> encoder) {
        this.encoder = encoder;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public boolean isIgnoreExceptions() {
        return ignoreExceptions;
    }

    /**
     * 設定是否忽略異常
     * 
     * @param ignoreExceptions true：忽略異常，不影響應用程式（預設）
     *                        false：拋出異常，可能影響應用程式
     */
    public void setIgnoreExceptions(boolean ignoreExceptions) {
        this.ignoreExceptions = ignoreExceptions;
    }
}


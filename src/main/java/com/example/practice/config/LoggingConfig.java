package com.example.practice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 日誌配置類
 * 
 * 用於在應用啟動時記錄配置資訊和環境變數
 */
@Slf4j
@Component
public class LoggingConfig {

    /**
     * 應用啟動完成後記錄配置資訊
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logApplicationStartup() {
        log.info("========================================");
        log.info("應用程式配置資訊：");
        log.info("  - Graylog Host: {}", 
            System.getenv().getOrDefault("GRAYLOG_HOST", "localhost"));
        log.info("  - Graylog Port: {}", 
            System.getenv().getOrDefault("GRAYLOG_PORT", "12201"));
        log.info("  - Spring Profile: {}", 
            System.getProperty("spring.profiles.active", "default"));
        log.info("========================================");
        log.info("日誌配置已載入，日誌將發送到 Graylog");
        log.info("請在 Graylog Web UI (http://localhost:9000) 中查看日誌");
        log.info("========================================");
    }
}


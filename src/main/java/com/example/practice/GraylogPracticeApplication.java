package com.example.practice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Graylog 練習應用程式主類
 * 
 * 這是一個用於學習和練習 Graylog 日誌整合的 Spring Boot 應用程式。
 * 
 * 主要功能：
 * - 展示如何配置 Logback 將日誌發送到 Graylog
 * - 演示結構化日誌的使用（MDC）
 * - 提供多種日誌測試場景
 * 
 * 啟動後訪問：
 * - http://localhost:8080/api/logs/test - 基本日誌測試
 * - http://localhost:8080/api/logs/test/exception - 異常日誌測試
 * - http://localhost:8080/api/logs/test/structured - 結構化日誌測試
 * - http://localhost:8080/api/logs/test/service - 服務層日誌測試
 * - http://localhost:8080/api/logs/health - 健康檢查
 */
@Slf4j
@SpringBootApplication
public class GraylogPracticeApplication {

    public static void main(String[] args) {
        // 應用啟動時的日誌
        log.info("========================================");
        log.info("Graylog Practice Application 啟動中...");
        log.info("========================================");
        
        SpringApplication.run(GraylogPracticeApplication.class, args);
        
        log.info("========================================");
        log.info("應用程式啟動完成！");
        log.info("請訪問 http://localhost:8080/api/logs/test 開始測試");
        log.info("請確保 Graylog 服務正在運行（docker-compose up）");
        log.info("========================================");
    }
}

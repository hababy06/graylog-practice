# Graylog Practice - 日誌管理練習專案

這是一個用於學習和練習 Graylog 日誌整合的 Spring Boot 應用程式。

## 📋 專案簡介

本專案展示了如何：
- 配置 Logback 將日誌發送到 Graylog
- 使用 GELF UDP 直接發送日誌到 Graylog
- 使用 GELF Kafka 通過 Kafka 傳輸日誌到 Graylog
- 使用 MDC (Mapped Diagnostic Context) 添加結構化欄位
- 記錄不同級別的日誌（DEBUG, INFO, WARN, ERROR）
- 處理異常日誌和堆疊追蹤
- 實現請求追蹤（Request ID）

## 🚀 快速開始

### 前置需求

- Java 17+
- Maven 3.6+
- Docker 和 Docker Compose

### 1. 啟動 Graylog 服務

```bash
docker-compose up -d
```

等待服務啟動完成（約 1-2 分鐘），然後訪問：
- Graylog Web UI: http://localhost:9000
  - 預設帳號：`admin`
  - 預設密碼：`admin`

### 2. 啟動應用程式

#### 使用 GELF UDP（預設）

```bash
# 使用 Maven Wrapper
./mvnw spring-boot:run

# 或使用 Maven
mvn spring-boot:run
```

#### 使用 GELF Kafka

```bash
# 使用 kafka profile 啟動
./mvnw spring-boot:run -Dspring-boot.run.profiles=kafka

# 或使用 Maven
mvn spring-boot:run -Dspring-boot.run.profiles=kafka
```

**注意**：使用 GELF Kafka 前，需要：
1. 啟動 Kafka 服務（已包含在 `docker-compose.yml` 中）
2. 在 Graylog 中配置 GELF Kafka Input（參考 [[Graylog練習專案_GELF_Kafka配置指南]]）

### 3. 測試日誌功能

#### 基本日誌測試
```bash
curl "http://localhost:8080/api/logs/test?user=testuser&type=normal"
```

#### 異常日誌測試
```bash
curl "http://localhost:8080/api/logs/test/exception"
```

#### 結構化日誌測試
```bash
curl "http://localhost:8080/api/logs/test/structured?userId=user123&operation=view"
```

#### 服務層日誌測試
```bash
curl "http://localhost:8080/api/logs/test/service?data=test-data"
```

#### 健康檢查
```bash
curl "http://localhost:8080/api/logs/health"
```

## 📊 在 Graylog 中查看日誌

1. 登入 Graylog Web UI (http://localhost:9000)
2. 進入 **Search** 頁面
3. 使用以下搜尋條件：
   - `app_name:graylog-practice` - 查看所有應用日誌
   - `level:ERROR` - 查看錯誤日誌
   - `user_id:user123` - 查看特定用戶的日誌
   - `request_id:xxx` - 追蹤特定請求的所有日誌

## 🔧 配置說明

### 環境變數

可以通過環境變數配置 Graylog 連線：

```bash
# GELF UDP 配置
export GRAYLOG_HOST=localhost
export GRAYLOG_PORT=12201
export SPRING_PROFILES_ACTIVE=dev

# GELF Kafka 配置
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_TOPIC=gelf-logs
export SPRING_PROFILES_ACTIVE=kafka
```

### 日誌級別

在 `application.yml` 中配置：

```yaml
logging:
  level:
    root: INFO
    com.example.practice: DEBUG
```

## 📁 專案結構

```
src/main/java/com/example/practice/
├── GraylogPracticeApplication.java  # 主應用類
├── config/
│   └── LoggingConfig.java           # 日誌配置類
├── controller/
│   └── LogTestController.java       # 日誌測試 Controller
└── service/
    └── LogTestService.java          # 日誌測試 Service
```

## 🎯 重點功能展示

### 1. MDC (Mapped Diagnostic Context)

使用 MDC 添加結構化欄位，這些欄位會在 Graylog 中顯示為可搜尋的欄位：

```java
MDC.put("user_id", userId);
MDC.put("request_id", requestId);
MDC.put("operation", operation);
```

**重要**：記得在 `finally` 區塊中清除 MDC，避免執行緒重用造成的資料污染。

### 2. 不同級別的日誌

```java
log.debug("調試資訊");  // 開發環境使用
log.info("一般資訊");   // 重要業務流程
log.warn("警告資訊");   // 需要注意但不影響運行
log.error("錯誤資訊", exception);  // 錯誤和異常
```

### 3. 異常日誌記錄

```java
try {
    // 業務邏輯
} catch (IllegalArgumentException e) {
    log.warn("業務驗證失敗: {}", e.getMessage(), e);
} catch (Exception e) {
    log.error("系統錯誤", e);
}
```

## 📝 最佳實踐

1. **使用 MDC 添加結構化資訊**：方便在 Graylog 中搜尋和過濾
2. **記錄完整的異常堆疊**：使用 `log.error("訊息", exception)` 而非 `log.error("訊息: " + exception.getMessage())`
3. **使用參數化日誌**：`log.info("處理資料: {}", data)` 而非字串拼接
4. **及時清除 MDC**：在 `finally` 區塊中清除，避免執行緒重用問題
5. **適當的日誌級別**：DEBUG 用於開發，INFO 用於重要流程，WARN 用於警告，ERROR 用於錯誤

## 🔍 故障排除

### 日誌沒有出現在 Graylog 中

1. 確認 Graylog 服務正在運行：`docker-compose ps`
2. 確認應用程式可以連接到 Graylog：檢查網路連線
3. 確認日誌級別設定正確
4. 檢查 `logback-spring.xml` 中的 GELF 配置

### 應用程式無法啟動

1. 確認 Java 版本為 17+
2. 確認 Maven 依賴下載完整：`mvn clean install`
3. 檢查端口是否被占用：`netstat -ano | findstr :8080`

## 📚 參考資源

### 官方文檔
- [Graylog 官方文檔](https://docs.graylog.org/)
- [logback-gelf GitHub](https://github.com/osiegmar/logback-gelf)
- [logback-kafka-appender GitHub](https://github.com/danielwegener/logback-kafka-appender)
- [Spring Boot 日誌文檔](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging)
- [Apache Kafka 官方文檔](https://kafka.apache.org/documentation/)

### 練習筆記
- [[Graylog練習專案_GELF_Kafka配置指南]] - GELF Kafka 完整配置指南
- [[Graylog練習專案_Input設定指南]] - Input 設定完整指南
- [[Graylog練習專案指南]] - 完整的專案建立指南

## 📄 授權

本專案僅用於學習和練習目的。


package com.example.practice.controller;

import com.example.practice.service.LogTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Graylog 日誌測試 Controller
 * 
 * 本 Controller 展示了如何在 Spring Boot 應用中使用結構化日誌，
 * 並將日誌發送到 Graylog 進行集中管理和分析。
 * 
 * 重點功能：
 * 1. 使用 MDC (Mapped Diagnostic Context) 添加結構化欄位
 * 2. 不同級別的日誌記錄 (DEBUG, INFO, WARN, ERROR)
 * 3. 異常日誌的完整堆疊追蹤
 * 4. 請求追蹤 (Request ID) 的實現
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogTestController {

    private final LogTestService logTestService;

    /**
     * 基本日誌測試
     * 展示不同級別的日誌輸出
     * 
     * @param user 用戶名稱（用於 MDC）
     * @param type 處理類型
     * @return 處理結果
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testLogs(
            @RequestParam(defaultValue = "guest") String user,
            @RequestParam(defaultValue = "normal") String type) {

        // 1. 【關鍵步驟】設定結構化上下文 (MDC)
        // 這些資料會自動變成 Graylog 中的可搜尋欄位
        String requestId = UUID.randomUUID().toString();
        MDC.put("user_id", user);
        MDC.put("request_id", requestId);
        MDC.put("action", "test_logs");
        MDC.put("endpoint", "/api/logs/test");

        try {
            log.debug("收到測試日誌請求 - DEBUG 級別（通常只在開發環境顯示）");
            log.info("收到請求，準備處理。用戶: {}, 類型: {}", user, type);
            log.warn("這是一條 WARN 級別的日誌，表示需要注意但不影響運行");

            // 呼叫業務邏輯
            logTestService.processBusinessLogic(type);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("requestId", requestId);
            response.put("timestamp", LocalDateTime.now());
            response.put("message", "所有級別的日誌已發送，請在 Graylog 中查看");

            log.info("請求處理完成，返回成功響應");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // 2. 記錄錯誤日誌（包含完整的堆疊資訊）
            log.error("處理請求時發生錯誤: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("requestId", requestId);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);

        } finally {
            // 3. 【非常重要】清除 MDC
            // 因為 Tomcat 的執行緒是會重複使用的 (Thread Pool)，
            // 如果不清除，下一個使用這個執行緒的請求可能會帶有上一個人的 userId 標籤
            MDC.clear();
        }
    }

    /**
     * 異常日誌測試
     * 展示如何記錄完整的異常堆疊資訊
     * 
     * @return 處理結果
     */
    @GetMapping("/test/exception")
    public ResponseEntity<Map<String, Object>> testException() {
        String requestId = UUID.randomUUID().toString();
        MDC.put("request_id", requestId);
        MDC.put("action", "test_exception");
        MDC.put("endpoint", "/api/logs/test/exception");

        try {
            log.info("開始測試異常日誌記錄");

            // 模擬一個業務異常
            throw new IllegalArgumentException("這是一個測試異常，用於展示異常日誌的記錄方式");

        } catch (IllegalArgumentException e) {
            // 記錄業務異常（ERROR 級別，用於測試 Alerts）
            log.error("業務邏輯驗證失敗: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "validation_error");
            response.put("requestId", requestId);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            // 記錄系統異常（ERROR 級別）
            log.error("系統發生未預期的錯誤", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("requestId", requestId);
            response.put("message", "系統錯誤，請聯繫管理員");
            return ResponseEntity.internalServerError().body(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * 結構化日誌測試
     * 展示如何使用 MDC 添加豐富的結構化資訊
     * 
     * @param userId 用戶 ID
     * @param operation 操作類型
     * @return 處理結果
     */
    @GetMapping("/test/structured")
    public ResponseEntity<Map<String, Object>> testStructuredLogs(
            @RequestParam String userId,
            @RequestParam(defaultValue = "view") String operation) {

        String requestId = UUID.randomUUID().toString();
        
        // 添加豐富的結構化資訊到 MDC
        MDC.put("user_id", userId);
        MDC.put("request_id", requestId);
        MDC.put("operation", operation);
        MDC.put("endpoint", "/api/logs/test/structured");
        MDC.put("ip_address", "192.168.1.100"); // 實際應用中從 HttpServletRequest 獲取
        MDC.put("user_agent", "Mozilla/5.0"); // 實際應用中從 HttpServletRequest 獲取

        try {
            log.info("用戶執行結構化日誌測試。操作: {}", operation);
            log.debug("結構化資訊已添加到 MDC，這些欄位會在 Graylog 中顯示為可搜尋的欄位");

            // 模擬業務處理
            logTestService.processBusinessLogic(operation);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("requestId", requestId);
            response.put("userId", userId);
            response.put("operation", operation);
            response.put("message", "結構化日誌已發送，請在 Graylog 中搜尋 user_id 或 operation 欄位");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("處理結構化日誌測試時發生錯誤", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("requestId", requestId);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * 結構化日誌（訂單示例）
     * 展示多個 MDC 欄位：user_id、order_id、channel、amount、currency
     */
    @GetMapping("/test/structured/order")
    public ResponseEntity<Map<String, Object>> testStructuredOrder(
            @RequestParam String userId,
            @RequestParam String orderId,
            @RequestParam(defaultValue = "web") String channel,
            @RequestParam(defaultValue = "1200.50") String amount,
            @RequestParam(defaultValue = "TWD") String currency) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("request_id", requestId);
        MDC.put("user_id", userId);
        MDC.put("order_id", orderId);
        MDC.put("channel", channel);
        MDC.put("amount", amount);
        MDC.put("currency", currency);
        MDC.put("endpoint", "/api/logs/test/structured/order");

        try {
            log.info("用戶下單事件收到，order_id={}, amount={} {}", orderId, amount, currency);
            log.debug("MDC 已帶上訂單欄位，Graylog 可直接用 order_id / channel / amount 查詢");

            // 模擬呼叫業務處理
            logTestService.processBusinessLogic(orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("requestId", requestId);
            response.put("orderId", orderId);
            response.put("userId", userId);
            response.put("channel", channel);
            response.put("message", "訂單事件日誌已送出，可用 order_id 或 channel 搜尋");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("處理訂單結構化日誌時發生錯誤", e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("requestId", requestId);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } finally {
            MDC.clear();
        }
    }

    /**
     * 服務層日誌測試
     * 展示跨層級的日誌追蹤
     * 
     * @param data 處理資料
     * @return 處理結果
     */
    @GetMapping("/test/service")
    public ResponseEntity<Map<String, Object>> testServiceLogs(
            @RequestParam(defaultValue = "test-data") String data) {

        String requestId = UUID.randomUUID().toString();
        MDC.put("request_id", requestId);
        MDC.put("action", "test_service");
        MDC.put("endpoint", "/api/logs/test/service");

        try {
            log.info("Controller 層：開始處理服務層日誌測試請求");

            // 呼叫服務層，服務層的日誌也會包含相同的 request_id（如果服務層也設置了 MDC）
            logTestService.processBusinessLogic(data);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("requestId", requestId);
            response.put("message", "服務層日誌已記錄，請在 Graylog 中查看完整的請求追蹤鏈");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Controller 層：處理服務層日誌測試時發生錯誤", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("requestId", requestId);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);

        } finally {
            MDC.clear();
        }
    }

    /**
     * 健康檢查端點
     * 用於確認應用是否正常運行
     * 
     * @return 健康狀態
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("健康檢查請求");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("application", "graylog-practice");
        
        return ResponseEntity.ok(response);
    }
}
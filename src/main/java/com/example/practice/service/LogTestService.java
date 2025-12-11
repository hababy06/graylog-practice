package com.example.practice.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * 日誌測試服務
 * 
 * 展示服務層如何記錄日誌，包括：
 * 1. 業務邏輯的日誌記錄
 * 2. 異常處理和日誌記錄
 * 3. 與 Controller 層的日誌追蹤關聯
 */
@Slf4j
@Service
public class LogTestService {

    /**
     * 處理業務邏輯
     * 
     * @param data 要處理的資料
     * @throws IllegalArgumentException 當資料無效時拋出
     */
    public void processBusinessLogic(String data) {
        // 服務層也可以添加 MDC 資訊，用於更細粒度的追蹤
        String originalData = MDC.get("data");
        if (originalData == null) {
            MDC.put("data", data);
        }

        log.info("Service 層：開始處理資料: {}", data);
        log.debug("Service 層：資料長度: {}, 資料類型: {}", data.length(), data.getClass().getSimpleName());

        try {
            // 模擬一些耗時的業務運算
            log.debug("Service 層：模擬業務處理中...");
            Thread.sleep(50);

            // 模擬業務規則驗證
            validateData(data);

            // 模擬資料處理
            String processedData = processData(data);
            log.info("Service 層：資料處理完成，結果: {}", processedData);

        } catch (InterruptedException e) {
            log.warn("Service 層：處理過程被中斷", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("處理被中斷", e);
        } catch (IllegalArgumentException e) {
            // 業務異常：記錄為 WARN 級別
            log.warn("Service 層：業務驗證失敗 - {}", e.getMessage());
            // 重新拋出，讓 Controller 層處理
            throw e;
        } catch (Exception e) {
            // 系統異常：記錄為 ERROR 級別
            log.error("Service 層：處理資料時發生未預期的錯誤: {}", data, e);
            throw new RuntimeException("處理失敗", e);
        } finally {
            // 清理服務層添加的 MDC（如果有的話）
            if (originalData == null) {
                MDC.remove("data");
            }
        }
    }

    /**
     * 驗證資料
     * 
     * @param data 要驗證的資料
     * @throws IllegalArgumentException 當資料無效時
     */
    private void validateData(String data) {
        log.debug("Service 層：開始驗證資料");

        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("資料不能為空");
        }

        if ("error".equalsIgnoreCase(data)) {
            throw new IllegalArgumentException("接收到無效的資料參數: " + data);
        }

        if (data.length() > 100) {
            log.warn("Service 層：資料長度超過建議值: {} (建議 < 100)", data.length());
        }

        log.debug("Service 層：資料驗證通過");
    }

    /**
     * 處理資料
     * 
     * @param data 原始資料
     * @return 處理後的資料
     */
    private String processData(String data) {
        log.debug("Service 層：開始處理資料邏輯");

        // 模擬資料轉換
        String processed = data.toUpperCase();
        
        log.debug("Service 層：資料轉換完成: {} -> {}", data, processed);
        
        return processed;
    }
}
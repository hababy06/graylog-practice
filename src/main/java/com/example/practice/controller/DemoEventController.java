package com.example.practice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
public class DemoEventController {

    private static final Logger log = LoggerFactory.getLogger(DemoEventController.class);

    @GetMapping("/demo/main-fail")
    public ResponseEntity<Map<String, Object>> mainFail(
            @RequestParam(defaultValue = "device-001") String deviceId) {

        Map<String, Object> body = new HashMap<>();
        MDC.put("deviceId", deviceId);
        MDC.put("eventType", "MAIN_FAIL");
        MDC.put("ts", Instant.now().toString());

        log.error("主電源故障 (MAIN_FAIL) deviceId={}", deviceId);

        MDC.clear();
        body.put("status", "ok");
        body.put("event", "MAIN_FAIL");
        body.put("deviceId", deviceId);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/demo/backup-fail")
    public ResponseEntity<Map<String, Object>> backupFail(
            @RequestParam(defaultValue = "device-001") String deviceId) {

        Map<String, Object> body = new HashMap<>();
        MDC.put("deviceId", deviceId);
        MDC.put("eventType", "BACKUP_FAIL");
        MDC.put("ts", Instant.now().toString());

        log.error("備用電源故障 (BACKUP_FAIL) deviceId={}", deviceId);

        MDC.clear();
        body.put("status", "ok");
        body.put("event", "BACKUP_FAIL");
        body.put("deviceId", deviceId);
        return ResponseEntity.ok(body);
    }
}
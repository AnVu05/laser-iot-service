package com.anne.laseriot.controller;

import com.anne.laseriot.dto.*;
import com.anne.laseriot.service.DeviceSessionService;
import com.anne.laseriot.service.JobCoordinatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalApiController {

    private final JobCoordinatorService jobCoordinatorService;
    private final DeviceSessionService deviceSessionService;

    /**
     * Endpoint nhận lệnh điều phối Job và nội dung G-code từ Web Backend
     * POST /api/v1/internal/jobs/dispatch
     */
    @PostMapping("/jobs/dispatch")
    public ResponseEntity<ApiResponse<JobDispatchResponse>> dispatchJob(
            @Valid @RequestBody JobDispatchRequest request) {
        try {
            JobDispatchResponse response = jobCoordinatorService.dispatchJob(request);
            return ResponseEntity.ok(ApiResponse.ok(response, "Điều phối Job thành công tới thiết bị " + request.getDeviceCode()));
        } catch (IOException e) {
            log.error("[INTERNAL API] Lỗi lưu trữ G-code cho Job {}: {}", request.getJobCode(), e.getMessage());
            return ResponseEntity.internalServerError().body(ApiResponse.error("Lỗi lưu trữ tệp G-code: " + e.getMessage()));
        }
    }

    /**
     * Tra cứu chi tiết một Job theo mã Job Code
     * GET /api/v1/internal/jobs/{jobCode}
     */
    @GetMapping("/jobs/{jobCode}")
    public ResponseEntity<ApiResponse<JobDetailDto>> getJobDetail(@PathVariable String jobCode) {
        JobDetailDto job = jobCoordinatorService.getJob(jobCode);
        if (job == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("Không tìm thấy Job có mã: " + jobCode));
        }
        return ResponseEntity.ok(ApiResponse.ok(job, "Lấy thông tin Job thành công"));
    }

    /**
     * Web Backend gọi lấy Telemetry mới nhất của thiết bị (tiến độ, toạ độ X/Y, thông số cảm biến)
     * GET /api/v1/internal/devices/{deviceCode}/telemetry
     */
    @GetMapping("/devices/{deviceCode}/telemetry")
    public ResponseEntity<ApiResponse<DeviceTelemetryDto>> getDeviceTelemetry(@PathVariable String deviceCode) {
        DeviceTelemetryDto telemetry = deviceSessionService.getLatestTelemetry(deviceCode);
        if (telemetry == null) {
            return ResponseEntity.ok(ApiResponse.ok(null, "Thiết bị chưa có dữ liệu telemetry"));
        }
        return ResponseEntity.ok(ApiResponse.ok(telemetry, "Lấy dữ liệu telemetry thành công"));
    }

    /**
     * Tra cứu trạng thái kết nối & nhịp tim của máy khắc
     * GET /api/v1/internal/devices/{deviceCode}/status
     */
    @GetMapping("/devices/{deviceCode}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeviceStatus(@PathVariable String deviceCode) {
        String status = deviceSessionService.getDeviceStatus(deviceCode);
        boolean active = deviceSessionService.isDeviceActive(deviceCode);
        DeviceHeartbeatDto heartbeat = deviceSessionService.getLatestHeartbeat(deviceCode);

        Map<String, Object> data = Map.of(
                "deviceCode", deviceCode.toUpperCase(),
                "status", status,
                "isActive", active,
                "lastHeartbeat", heartbeat != null ? heartbeat : Map.of()
        );
        return ResponseEntity.ok(ApiResponse.ok(data, "Lấy trạng thái thiết bị thành công"));
    }

    /**
     * Tra cứu cấu hình giới hạn vùng khắc và tốc độ của thiết bị
     * GET /api/v1/internal/devices/{deviceCode}/config
     */
    @GetMapping("/devices/{deviceCode}/config")
    public ResponseEntity<ApiResponse<DeviceConfigDto>> getDeviceConfig(@PathVariable String deviceCode) {
        DeviceConfigDto config = deviceSessionService.getDeviceConfig(deviceCode);
        if (config == null) {
            return ResponseEntity.status(404).body(ApiResponse.error("Chưa có cấu hình cho thiết bị: " + deviceCode));
        }
        return ResponseEntity.ok(ApiResponse.ok(config, "Lấy cấu hình thiết bị thành công"));
    }

    /**
     * Gửi lệnh tức thời xuống bo mạch (PAUSE, RESUME, CANCEL, HOME)
     * POST /api/v1/internal/devices/{deviceCode}/commands
     */
    @PostMapping("/devices/{deviceCode}/commands")
    public ResponseEntity<ApiResponse<Void>> sendDeviceCommand(
            @PathVariable String deviceCode,
            @Valid @RequestBody DeviceCommandDto command) {
        jobCoordinatorService.sendCommand(deviceCode, command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã phát lệnh " + command.getAction() + " xuống thiết bị " + deviceCode));
    }

    /**
     * Cập nhật cấu hình thông số và bắn STOMP đồng bộ xuống bo mạch
     * PUT /api/v1/internal/devices/{deviceCode}/config
     */
    @PutMapping("/devices/{deviceCode}/config")
    public ResponseEntity<ApiResponse<Void>> updateDeviceConfig(
            @PathVariable String deviceCode,
            @Valid @RequestBody DeviceConfigDto config) {
        jobCoordinatorService.sendConfig(deviceCode, config);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã cập nhật và phát cấu hình phiên bản " + config.getConfigVersion() + " xuống máy"));
    }

    /**
     * Xoay token thiết bị (để nạp vào captive portal trên bo mạch)
     * POST /api/v1/internal/devices/{deviceCode}/token
     */
    @PostMapping("/devices/{deviceCode}/token")
    public ResponseEntity<ApiResponse<Map<String, String>>> rotateDeviceToken(@PathVariable String deviceCode) {
        String token = deviceSessionService.rotateDeviceToken(deviceCode);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "deviceCode", deviceCode.toUpperCase(),
                "deviceToken", token
        ), "Cấp mới token thiết bị thành công"));
    }
}

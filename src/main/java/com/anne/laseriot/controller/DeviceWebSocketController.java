package com.anne.laseriot.controller;

import com.anne.laseriot.dto.DeviceHeartbeatDto;
import com.anne.laseriot.dto.DeviceStatusDto;
import com.anne.laseriot.dto.DeviceTelemetryDto;
import com.anne.laseriot.service.DeviceSessionService;
import com.anne.laseriot.service.JobCoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DeviceWebSocketController {

    private final DeviceSessionService deviceSessionService;
    private final JobCoordinatorService jobCoordinatorService;

    /**
     * Nhận Heartbeat PING từ bo mạch Makerbase MKS DLC32 V2.1 gửi lên qua STOMP:
     * Destination: /app/device/heartbeat
     */
    @MessageMapping("/device/heartbeat")
    public void handleDeviceHeartbeat(
            @Payload DeviceHeartbeatDto message,
            @Header(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES) Map<String, Object> attributes) {
        String authenticatedDevice = getAuthenticatedDevice(attributes);
        message.setDeviceCode(authenticatedDevice);

        deviceSessionService.updateHeartbeat(message);
    }

    /**
     * Nhận Telemetry tiến độ, toạ độ X/Y, thông số cảm biến từ bo mạch MKS DLC32 gửi lên:
     * Destination: /app/device/telemetry
     */
    @MessageMapping("/device/telemetry")
    public void handleDeviceTelemetry(
            @Payload DeviceTelemetryDto message,
            @Header(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES) Map<String, Object> attributes) {
        String authenticatedDevice = getAuthenticatedDevice(attributes);
        message.setDeviceCode(authenticatedDevice);

        // Lưu telemetry mới nhất vào session cache
        deviceSessionService.updateTelemetry(message);

        // Cập nhật tiến độ vào bản ghi Job đang chạy
        jobCoordinatorService.updateJobTelemetry(message);
    }

    /**
     * Nhận Cập nhật trạng thái vòng đời Job từ bo mạch MKS DLC32:
     * (DOWNLOADING, VALIDATING, AWAITING_ONSITE_CONFIRMATION, PRINTING, COMPLETED, FAILED)
     * Destination: /app/device/status
     */
    @MessageMapping("/device/status")
    public void handleDeviceJobStatus(
            @Payload DeviceStatusDto message,
            @Header(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES) Map<String, Object> attributes) {
        String authenticatedDevice = getAuthenticatedDevice(attributes);
        message.setDeviceCode(authenticatedDevice);

        deviceSessionService.updateDeviceStatus(authenticatedDevice, message.getStatus(), message.getMessage());

        if (message.getJobCode() != null && !message.getJobCode().isBlank()) {
            jobCoordinatorService.updateJobStatus(message.getJobCode(), message.getStatus(), message.getMessage());
        }
    }

    /**
     * Nhận ACK xác nhận bo mạch đã lưu cấu hình NVS thành công:
     * Destination: /app/device/config-applied
     */
    @MessageMapping("/device/config-applied")
    public void handleDeviceConfigApplied(
            @Payload Map<String, Object> message,
            @Header(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES) Map<String, Object> attributes) {
        String authenticatedDevice = getAuthenticatedDevice(attributes);
        Object version = message.get("configVersion");
        log.info("[WS IN] Thiết bị {} xác nhận đã áp dụng cấu hình phiên bản: {}", authenticatedDevice, version);
    }

    private String getAuthenticatedDevice(Map<String, Object> attributes) {
        Object code = attributes != null ? attributes.get("authenticatedDeviceCode") : null;
        if (!(code instanceof String deviceCode) || deviceCode.isBlank()) {
            throw new SecurityException("Phiên kết nối của thiết bị chưa được xác thực.");
        }
        return deviceCode;
    }
}

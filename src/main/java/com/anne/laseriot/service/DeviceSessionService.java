package com.anne.laseriot.service;

import com.anne.laseriot.dto.DeviceConfigDto;
import com.anne.laseriot.dto.DeviceHeartbeatDto;
import com.anne.laseriot.dto.DeviceTelemetryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DeviceSessionService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // Lưu token đã băm BCrypt: deviceCode -> bCryptHash
    private final Map<String, String> deviceTokenHashMap = new ConcurrentHashMap<>();

    // Lưu trạng thái mới nhất: deviceCode -> Status ("ONLINE", "OFFLINE", "PRINTING", "IDLE", ...)
    private final Map<String, String> deviceStatusMap = new ConcurrentHashMap<>();

    // Lưu Heartbeat mới nhất
    private final Map<String, DeviceHeartbeatDto> heartbeatCache = new ConcurrentHashMap<>();

    // Lưu Telemetry mới nhất (bao gồm toạ độ X/Y, cảm biến, tiến độ)
    private final Map<String, DeviceTelemetryDto> telemetryCache = new ConcurrentHashMap<>();

    // Lưu Cấu hình biên giới hạn thiết bị
    private final Map<String, DeviceConfigDto> configCache = new ConcurrentHashMap<>();

    // Thời điểm nhận tin cuối cùng (timestamp epoch ms)
    private final Map<String, Long> lastSeenMap = new ConcurrentHashMap<>();

    public DeviceSessionService(@Value("${app.device.default-code:LASER-HN-001}") String defaultCode,
                                @Value("${app.device.default-token:mks-dlc32-secret-token}") String defaultToken) {
        // Đăng ký thiết bị mặc định với token khởi tạo
        registerOrUpdateDeviceToken(defaultCode, defaultToken);

        // Khởi tạo cấu hình giới hạn an toàn mặc định cho bo mạch Makerbase MKS DLC32 V2.1
        configCache.put(defaultCode.toUpperCase(), DeviceConfigDto.builder()
                .maxWidthMm(300.0)
                .maxHeightMm(200.0)
                .maxSpeedMmMin(3000)
                .maxPowerS(1000)
                .configVersion(1L)
                .build());

        deviceStatusMap.put(defaultCode.toUpperCase(), "OFFLINE");
    }

    public synchronized String rotateDeviceToken(String deviceCode) {
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        registerOrUpdateDeviceToken(deviceCode, rawToken);
        log.info("[DEVICE AUTH] Đã xoay token cho máy {}. Token mới được cấp.", deviceCode);
        return rawToken;
    }

    public void registerOrUpdateDeviceToken(String deviceCode, String rawToken) {
        String hash = passwordEncoder.encode(rawToken);
        deviceTokenHashMap.put(deviceCode.toUpperCase(), hash);
    }

    public boolean authenticateDevice(String deviceCode, String rawToken) {
        if (deviceCode == null || rawToken == null) return false;
        String storedHash = deviceTokenHashMap.get(deviceCode.toUpperCase());
        if (storedHash == null) {
            log.warn("[DEVICE AUTH] Thiết bị '{}' chưa được đăng ký trong hệ thống", deviceCode);
            return false;
        }
        return passwordEncoder.matches(rawToken, storedHash);
    }

    public void updateHeartbeat(DeviceHeartbeatDto heartbeat) {
        if (heartbeat == null || heartbeat.getDeviceCode() == null) return;
        String code = heartbeat.getDeviceCode().toUpperCase();
        long now = Instant.now().toEpochMilli();

        heartbeat.setTimestamp(now);
        heartbeatCache.put(code, heartbeat);
        lastSeenMap.put(code, now);

        // Cập nhật trạng thái sang ONLINE nếu đang OFFLINE
        String currentStatus = deviceStatusMap.getOrDefault(code, "OFFLINE");
        if ("OFFLINE".equalsIgnoreCase(currentStatus)) {
            deviceStatusMap.put(code, "ONLINE");
        }

        log.debug("[DEVICE HEARTBEAT] Nhận nhịp tim từ {}: FreeHeap={} bytes, RSSI={} dBm",
                code, heartbeat.getFreeHeap(), heartbeat.getWifiRssi());
    }

    public void updateTelemetry(DeviceTelemetryDto telemetry) {
        if (telemetry == null || telemetry.getDeviceCode() == null) return;
        String code = telemetry.getDeviceCode().toUpperCase();
        long now = Instant.now().toEpochMilli();

        telemetry.setTimestamp(now);
        telemetryCache.put(code, telemetry);
        lastSeenMap.put(code, now);

        if (telemetry.getStatus() != null && !telemetry.getStatus().isBlank()) {
            deviceStatusMap.put(code, telemetry.getStatus().toUpperCase());
        }

        log.info("[DEVICE TELEMETRY] {}: Job={}, Tiến độ={}% (X={}, Y={}), Cảm biến={}",
                code, telemetry.getJobCode(), telemetry.getProgressPercent(),
                telemetry.getX(), telemetry.getY(), telemetry.getSensors());
    }

    public void updateDeviceStatus(String deviceCode, String status, String message) {
        if (deviceCode == null || status == null) return;
        String code = deviceCode.toUpperCase();
        deviceStatusMap.put(code, status.toUpperCase());
        lastSeenMap.put(code, Instant.now().toEpochMilli());
        log.info("[DEVICE STATUS] Thiết bị {} chuyển trạng thái sang: {} ({})", code, status, message);
    }

    public void updateDeviceConfig(String deviceCode, DeviceConfigDto config) {
        if (deviceCode == null || config == null) return;
        configCache.put(deviceCode.toUpperCase(), config);
    }

    public DeviceConfigDto getDeviceConfig(String deviceCode) {
        return configCache.get(deviceCode.toUpperCase());
    }

    public DeviceTelemetryDto getLatestTelemetry(String deviceCode) {
        return telemetryCache.get(deviceCode.toUpperCase());
    }

    public DeviceHeartbeatDto getLatestHeartbeat(String deviceCode) {
        return heartbeatCache.get(deviceCode.toUpperCase());
    }

    public String getDeviceStatus(String deviceCode) {
        return deviceStatusMap.getOrDefault(deviceCode.toUpperCase(), "OFFLINE");
    }

    public boolean isDeviceActive(String deviceCode) {
        Long lastSeen = lastSeenMap.get(deviceCode.toUpperCase());
        if (lastSeen == null) return false;
        // Coi là active nếu có tín hiệu trong vòng 120 giây (2 phút)
        return (Instant.now().toEpochMilli() - lastSeen) < 120_000L;
    }

    // Định kỳ mỗi 30 giây kiểm tra thiết bị mất kết nối (timeout sau 120 giây)
    @Scheduled(fixedRate = 30000)
    public void checkOfflineDevices() {
        long now = Instant.now().toEpochMilli();
        for (Map.Entry<String, Long> entry : lastSeenMap.entrySet()) {
            String code = entry.getKey();
            long lastSeen = entry.getValue();
            if ((now - lastSeen) > 120_000L) {
                String current = deviceStatusMap.getOrDefault(code, "OFFLINE");
                if (!"OFFLINE".equalsIgnoreCase(current)) {
                    deviceStatusMap.put(code, "OFFLINE");
                    log.warn("[DEVICE MONITOR] Thiết bị {} quá 120s không gửi nhịp tim -> Đánh dấu OFFLINE", code);
                }
            }
        }
    }
}

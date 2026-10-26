package com.anne.laseriot.service;

import com.anne.laseriot.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobCoordinatorService {

    private final GcodeStorageService storageService;
    private final DeviceSessionService deviceSessionService;
    private final SimpMessagingTemplate messagingTemplate;

    // Quản lý các Job đang chạy trong bộ nhớ: jobCode -> JobDetailDto
    private final Map<String, JobDetailDto> jobRegistry = new ConcurrentHashMap<>();

    public JobDispatchResponse dispatchJob(JobDispatchRequest request) throws IOException {
        String jobCode = request.getJobCode();
        String deviceCode = request.getDeviceCode().toUpperCase();

        log.info("[JOB COORDINATOR] Tiếp nhận yêu cầu điều phối Job {} tới thiết bị {}", jobCode, deviceCode);

        // 1. Lưu file G-code và tính toán mã băm phần cứng SHA-256
        GcodeStorageService.StorageResult storageResult = storageService.saveGcode(jobCode, request.getGcodeContent());

        // 2. Tạo đường link tải file bảo mật qua HTTPS
        String downloadUrl = "/api/v1/jobs/" + jobCode + "/download";

        // 3. Khởi tạo đối tượng JobDetailDto lưu trong bộ nhớ
        long now = Instant.now().toEpochMilli();
        JobDetailDto jobDetail = JobDetailDto.builder()
                .jobCode(jobCode)
                .deviceCode(deviceCode)
                .originalFilename(request.getOriginalFilename() != null ? request.getOriginalFilename() : jobCode + ".gcode")
                .storedFilePath(storageResult.filePath())
                .fileSizeBytes(storageResult.fileSize())
                .sha256Checksum(storageResult.sha256())
                .targetWidthMm(request.getTargetWidthMm())
                .targetHeightMm(request.getTargetHeightMm())
                .feedRate(request.getFeedRate())
                .laserPower(request.getLaserPower())
                .totalLines(storageResult.totalLines())
                .status("DISPATCHED")
                .currentProgress(0.0)
                .materialType(request.getMaterialType())
                .downloadUrl(downloadUrl)
                .dispatchedAt(now)
                .build();

        jobRegistry.put(jobCode, jobDetail);

        // 4. Bắn sự kiện STOMP thông báo tới topic riêng của thiết bị
        // Bo mạch ESP32 đang SUBSCRIBE tại: /topic/device/{deviceCode}/jobs
        Map<String, Object> stompPayload = Map.of(
                "event", "NEW_JOB_AVAILABLE",
                "jobCode", jobCode,
                "downloadUrl", downloadUrl,
                "fileSizeBytes", storageResult.fileSize(),
                "sha256Checksum", storageResult.sha256(),
                "targetWidthMm", request.getTargetWidthMm(),
                "targetHeightMm", request.getTargetHeightMm(),
                "feedRate", request.getFeedRate() != null ? request.getFeedRate() : 1000,
                "laserPower", request.getLaserPower() != null ? request.getLaserPower() : 500,
                "dispatchedAt", now
        );

        String destination = "/topic/device/" + deviceCode + "/jobs";
        messagingTemplate.convertAndSend(destination, (Object) stompPayload);
        log.info("[JOB COORDINATOR] Đã bắn STOMP thông báo Job tới {}: {}", destination, stompPayload);

        deviceSessionService.updateDeviceStatus(deviceCode, "JOB_DISPATCHED", "Job " + jobCode + " đã được gửi tới thiết bị");

        return JobDispatchResponse.builder()
                .jobCode(jobCode)
                .deviceCode(deviceCode)
                .downloadUrl(downloadUrl)
                .fileSizeBytes(storageResult.fileSize())
                .sha256Checksum(storageResult.sha256())
                .totalLines(storageResult.totalLines())
                .status("DISPATCHED")
                .dispatchedAt(now)
                .build();
    }

    public JobDetailDto getJob(String jobCode) {
        return jobRegistry.get(jobCode);
    }

    public void updateJobStatus(String jobCode, String status, String message) {
        JobDetailDto job = jobRegistry.get(jobCode);
        if (job != null) {
            job.setStatus(status);
            if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
                job.setCompletedAt(Instant.now().toEpochMilli());
            }
            log.info("[JOB COORDINATOR] Cập nhật trạng thái Job {}: {} ({})", jobCode, status, message);
        }
    }

    public void updateJobTelemetry(DeviceTelemetryDto telemetry) {
        String jobCode = telemetry.getJobCode();
        if (jobCode != null && !jobCode.isBlank()) {
            JobDetailDto job = jobRegistry.get(jobCode);
            if (job != null) {
                if (telemetry.getProgressPercent() != null) {
                    job.setCurrentProgress(telemetry.getProgressPercent());
                }
                if (telemetry.getStatus() != null && !telemetry.getStatus().isBlank()) {
                    job.setStatus(telemetry.getStatus());
                }
            }
        }
    }

    public void sendCommand(String deviceCode, DeviceCommandDto command) {
        String destination = "/topic/device/" + deviceCode.toUpperCase() + "/commands";
        messagingTemplate.convertAndSend(destination, command);
        log.info("[JOB COORDINATOR] Đã gửi lệnh '{}' tới thiết bị {}", command.getAction(), deviceCode);

        if ("CANCEL".equalsIgnoreCase(command.getAction()) && command.getJobCode() != null) {
            updateJobStatus(command.getJobCode(), "CANCELLED", "Hủy bỏ bởi người dùng từ Web");
        }
    }

    public void sendConfig(String deviceCode, DeviceConfigDto config) {
        deviceSessionService.updateDeviceConfig(deviceCode, config);
        String destination = "/topic/device/" + deviceCode.toUpperCase() + "/config";
        messagingTemplate.convertAndSend(destination, config);
        log.info("[JOB COORDINATOR] Đã bắn cấu hình phiên bản {} tới thiết bị {}", config.getConfigVersion(), deviceCode);
    }
}

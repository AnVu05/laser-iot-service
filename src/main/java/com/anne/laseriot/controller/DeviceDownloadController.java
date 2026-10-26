package com.anne.laseriot.controller;

import com.anne.laseriot.dto.JobDetailDto;
import com.anne.laseriot.service.DeviceSessionService;
import com.anne.laseriot.service.GcodeStorageService;
import com.anne.laseriot.service.JobCoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
public class DeviceDownloadController {

    private final JobCoordinatorService jobCoordinatorService;
    private final GcodeStorageService storageService;
    private final DeviceSessionService deviceSessionService;

    /**
     * Endpoint bảo mật cho bo mạch ESP32 (Makerbase MKS DLC32 V2.1) tải file G-code
     * GET /api/v1/jobs/{jobCode}/download
     */
    @GetMapping("/{jobCode}/download")
    public ResponseEntity<Resource> downloadJobGcode(
            @PathVariable String jobCode,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @RequestHeader(value = "X-Device-Code", required = false) String deviceCodeHeader) {

        JobDetailDto job = jobCoordinatorService.getJob(jobCode);
        if (job == null) {
            log.warn("[GCODE DOWNLOAD] Không tìm thấy Job có mã: {}", jobCode);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 1. Kiểm tra xác thực thiết bị
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("[GCODE DOWNLOAD] Từ chối tải Job {}: Thiếu Authorization header", jobCode);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
        String deviceCode = (deviceCodeHeader != null && !deviceCodeHeader.isBlank())
                ? deviceCodeHeader.trim().toUpperCase()
                : job.getDeviceCode().toUpperCase();

        // Kiểm tra xem thiết bị yêu cầu có đúng là thiết bị đích của Job hay không
        if (!job.getDeviceCode().equalsIgnoreCase(deviceCode)) {
            log.warn("[GCODE DOWNLOAD] Thiết bị '{}' cố tình tải file của Job thuộc về máy '{}'",
                    deviceCode, job.getDeviceCode());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Kiểm tra token bí mật của thiết bị
        boolean valid = deviceSessionService.authenticateDevice(deviceCode, token);
        if (!valid) {
            log.warn("[GCODE DOWNLOAD] Token của thiết bị '{}' không hợp lệ", deviceCode);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 2. Trả về luồng file G-code kèm Header SHA-256 để bo mạch đối soát phần cứng
        try {
            Resource resource = storageService.loadAsResource(jobCode);
            String filename = job.getOriginalFilename() != null ? job.getOriginalFilename() : jobCode + ".gcode";

            log.info("[GCODE DOWNLOAD] Thiết bị {} tải thành công file Job {} (SHA-256: {})",
                    deviceCode, jobCode, job.getSha256Checksum());

            // Cập nhật trạng thái Job sang DOWNLOADING
            jobCoordinatorService.updateJobStatus(jobCode, "DOWNLOADING", "Bo mạch đang tải file qua HTTPS");

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header("X-File-SHA256", job.getSha256Checksum())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("[GCODE DOWNLOAD] Lỗi đọc file G-code của Job {}: {}", jobCode, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

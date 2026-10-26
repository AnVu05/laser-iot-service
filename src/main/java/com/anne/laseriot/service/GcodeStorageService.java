package com.anne.laseriot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@Slf4j
public class GcodeStorageService {

    private final Path storageDir;

    public GcodeStorageService(@Value("${app.storage.gcode-dir:storage/gcode-cache}") String storagePath) {
        this.storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDir);
            log.info("[GCODE STORAGE] Thư mục lưu trữ tạm: {}", this.storageDir);
        } catch (IOException e) {
            log.error("[GCODE STORAGE] Không thể tạo thư mục lưu trữ: {}", this.storageDir, e);
            throw new RuntimeException("Lỗi khởi tạo thư mục lưu trữ G-code", e);
        }
    }

    public StorageResult saveGcode(String jobCode, String content) throws IOException {
        String filename = jobCode + ".gcode";
        Path targetPath = storageDir.resolve(filename).normalize();

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(targetPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        String sha256 = calculateSha256(bytes);
        long fileSize = bytes.length;
        int totalLines = countLines(content);

        log.info("[GCODE STORAGE] Đã lưu file {} ({} bytes, {} lines, SHA-256: {})",
                filename, fileSize, totalLines, sha256);

        return new StorageResult(targetPath.toString(), filename, fileSize, sha256, totalLines);
    }

    public Resource loadAsResource(String jobCode) throws IOException {
        String filename = jobCode + ".gcode";
        Path targetPath = storageDir.resolve(filename).normalize();

        if (!Files.exists(targetPath) || !Files.isReadable(targetPath)) {
            throw new IOException("Không tìm thấy file G-code cho Job " + jobCode);
        }

        return new FileSystemResource(targetPath);
    }

    public String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Thuật toán SHA-256 không khả dụng trên JVM", e);
        }
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty()) return 0;
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    public record StorageResult(String filePath, String filename, long fileSize, String sha256, int totalLines) {}
}

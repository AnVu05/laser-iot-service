package com.anne.laseriot;

import com.anne.laseriot.service.GcodeStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GcodeStorageServiceTest {

    @TempDir
    Path tempDir;

    private GcodeStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new GcodeStorageService(tempDir.toString());
    }

    @Test
    void testSaveAndLoadGcode() throws IOException {
        String jobCode = "JOB-TEST-001";
        String content = "G21 ; Metric\nG90 ; Absolute\nG0 X10 Y20\nM3 S500\nG1 X50 F1000\nM5\n";

        GcodeStorageService.StorageResult result = storageService.saveGcode(jobCode, content);

        assertNotNull(result);
        assertEquals(jobCode + ".gcode", result.filename());
        assertTrue(result.fileSize() > 0);
        assertNotNull(result.sha256());
        assertEquals(64, result.sha256().length()); // SHA-256 is 64 hex chars
        assertEquals(7, result.totalLines());

        // Test loading back
        Resource resource = storageService.loadAsResource(jobCode);
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void testCalculateSha256Determinism() {
        byte[] data = "G0 X100 Y100 F3000\n".getBytes();
        String hash1 = storageService.calculateSha256(data);
        String hash2 = storageService.calculateSha256(data);

        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    void testLoadNonExistentFileThrowsException() {
        assertThrows(IOException.class, () -> storageService.loadAsResource("NON-EXISTENT-JOB"));
    }
}

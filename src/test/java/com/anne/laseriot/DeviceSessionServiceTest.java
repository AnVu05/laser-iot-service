package com.anne.laseriot;

import com.anne.laseriot.dto.DeviceConfigDto;
import com.anne.laseriot.dto.DeviceHeartbeatDto;
import com.anne.laseriot.dto.DeviceTelemetryDto;
import com.anne.laseriot.service.DeviceSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeviceSessionServiceTest {

    private DeviceSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new DeviceSessionService("LASER-HN-001", "secret-token-123");
    }

    @Test
    void testAuthenticateDefaultDeviceSuccess() {
        assertTrue(sessionService.authenticateDevice("LASER-HN-001", "secret-token-123"));
        assertTrue(sessionService.authenticateDevice("laser-hn-001", "secret-token-123")); // Case insensitive
    }

    @Test
    void testAuthenticateDefaultDeviceWrongTokenFails() {
        assertFalse(sessionService.authenticateDevice("LASER-HN-001", "wrong-token"));
    }

    @Test
    void testRotateDeviceToken() {
        String newToken = sessionService.rotateDeviceToken("LASER-HN-001");
        assertNotNull(newToken);
        assertNotEquals("secret-token-123", newToken);

        // Old token should now fail
        assertFalse(sessionService.authenticateDevice("LASER-HN-001", "secret-token-123"));
        // New token should succeed
        assertTrue(sessionService.authenticateDevice("LASER-HN-001", newToken));
    }

    @Test
    void testHeartbeatUpdatesStatusAndTimestamp() {
        DeviceHeartbeatDto heartbeat = DeviceHeartbeatDto.builder()
                .deviceCode("LASER-HN-001")
                .freeHeap(180000L)
                .wifiRssi(-55)
                .configVersion(1L)
                .build();

        sessionService.updateHeartbeat(heartbeat);

        assertEquals("ONLINE", sessionService.getDeviceStatus("LASER-HN-001"));
        assertTrue(sessionService.isDeviceActive("LASER-HN-001"));
        assertNotNull(sessionService.getLatestHeartbeat("LASER-HN-001"));
    }

    @Test
    void testTelemetryCachingWithCoordinatesAndSensors() {
        DeviceTelemetryDto telemetry = DeviceTelemetryDto.builder()
                .deviceCode("LASER-HN-001")
                .jobCode("JOB-001")
                .status("PRINTING")
                .progressPercent(45.5)
                .currentLine(455)
                .totalLines(1000)
                .x(25.4)
                .y(18.2)
                .feedRate(1500)
                .laserPower(750)
                .sensors(Map.of("temperature", 38.5, "flameDetected", false))
                .build();

        sessionService.updateTelemetry(telemetry);

        assertEquals("PRINTING", sessionService.getDeviceStatus("LASER-HN-001"));
        DeviceTelemetryDto cached = sessionService.getLatestTelemetry("LASER-HN-001");
        assertNotNull(cached);
        assertEquals(25.4, cached.getX());
        assertEquals(18.2, cached.getY());
        assertEquals(45.5, cached.getProgressPercent());
        assertNotNull(cached.getSensors());
        assertEquals(38.5, cached.getSensors().get("temperature"));
    }

    @Test
    void testUpdateAndRetrieveDeviceConfig() {
        DeviceConfigDto config = DeviceConfigDto.builder()
                .maxWidthMm(400.0)
                .maxHeightMm(300.0)
                .maxSpeedMmMin(4000)
                .maxPowerS(1000)
                .configVersion(2L)
                .build();

        sessionService.updateDeviceConfig("LASER-HN-001", config);
        DeviceConfigDto retrieved = sessionService.getDeviceConfig("LASER-HN-001");

        assertNotNull(retrieved);
        assertEquals(400.0, retrieved.getMaxWidthMm());
        assertEquals(2L, retrieved.getConfigVersion());
    }
}

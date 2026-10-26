package com.anne.laseriot;

import com.anne.laseriot.config.InternalApiKeyFilter;
import com.anne.laseriot.controller.InternalApiController;
import com.anne.laseriot.dto.DeviceCommandDto;
import com.anne.laseriot.dto.DeviceTelemetryDto;
import com.anne.laseriot.dto.JobDispatchRequest;
import com.anne.laseriot.service.DeviceSessionService;
import com.anne.laseriot.service.GcodeStorageService;
import com.anne.laseriot.service.JobCoordinatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InternalApiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private DeviceSessionService deviceSessionService;
    private JobCoordinatorService jobCoordinatorService;

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SECRET_KEY = "test-secret-key";

    @BeforeEach
    void setUp() {
        GcodeStorageService storageService = new GcodeStorageService(tempDir.toString());
        deviceSessionService = new DeviceSessionService("LASER-HN-001", "token-123");
        jobCoordinatorService = new JobCoordinatorService(storageService, deviceSessionService, messagingTemplate);

        InternalApiController controller = new InternalApiController(jobCoordinatorService, deviceSessionService);
        InternalApiKeyFilter filter = new InternalApiKeyFilter(SECRET_KEY);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .build();
    }

    @Test
    void testUnauthorizedWithoutSecretKey() throws Exception {
        mockMvc.perform(get("/api/v1/internal/devices/LASER-HN-001/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message", containsString("X-Internal-Secret-Key")));
    }

    @Test
    void testUnauthorizedWithWrongSecretKey() throws Exception {
        mockMvc.perform(get("/api/v1/internal/devices/LASER-HN-001/status")
                        .header("X-Internal-Secret-Key", "wrong-secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void testAuthorizedGetDeviceStatus() throws Exception {
        mockMvc.perform(get("/api/v1/internal/devices/LASER-HN-001/status")
                        .header("X-Internal-Secret-Key", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.deviceCode", is("LASER-HN-001")));
    }

    @Test
    void testDispatchJobSuccessfully() throws Exception {
        JobDispatchRequest request = JobDispatchRequest.builder()
                .jobCode("JOB-TEST-DISPATCH-001")
                .deviceCode("LASER-HN-001")
                .originalFilename("test_logo.gcode")
                .gcodeContent("G21\nG90\nG0 X10 Y10\nM3 S500\nG1 X50 Y10 F1200\nM5\n")
                .targetWidthMm(100.0)
                .targetHeightMm(100.0)
                .feedRate(1200)
                .laserPower(500)
                .materialType("Gỗ Plywood 3mm")
                .build();

        mockMvc.perform(post("/api/v1/internal/jobs/dispatch")
                        .header("X-Internal-Secret-Key", SECRET_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.jobCode", is("JOB-TEST-DISPATCH-001")))
                .andExpect(jsonPath("$.data.sha256Checksum", notNullValue()))
                .andExpect(jsonPath("$.data.downloadUrl", is("/api/v1/jobs/JOB-TEST-DISPATCH-001/download")));
    }

    @Test
    void testGetDeviceTelemetryWithCoordinatesAndSensors() throws Exception {
        deviceSessionService.updateTelemetry(DeviceTelemetryDto.builder()
                .deviceCode("LASER-HN-001")
                .jobCode("JOB-TEST-DISPATCH-001")
                .status("PRINTING")
                .progressPercent(60.0)
                .currentLine(600)
                .totalLines(1000)
                .x(45.2)
                .y(30.8)
                .feedRate(1200)
                .laserPower(500)
                .sensors(Map.of("temperature", 42.1, "flameDetected", false))
                .build());

        mockMvc.perform(get("/api/v1/internal/devices/LASER-HN-001/telemetry")
                        .header("X-Internal-Secret-Key", SECRET_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.x", is(45.2)))
                .andExpect(jsonPath("$.data.y", is(30.8)))
                .andExpect(jsonPath("$.data.progressPercent", is(60.0)))
                .andExpect(jsonPath("$.data.sensors.temperature", is(42.1)));
    }

    @Test
    void testSendCommand() throws Exception {
        DeviceCommandDto command = DeviceCommandDto.builder()
                .action("PAUSE")
                .jobCode("JOB-TEST-DISPATCH-001")
                .reason("Cảnh báo khói bụi")
                .build();

        mockMvc.perform(post("/api/v1/internal/devices/LASER-HN-001/commands")
                        .header("X-Internal-Secret-Key", SECRET_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("PAUSE")));
    }
}

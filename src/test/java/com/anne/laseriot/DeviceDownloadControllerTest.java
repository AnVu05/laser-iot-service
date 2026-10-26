package com.anne.laseriot;

import com.anne.laseriot.controller.DeviceDownloadController;
import com.anne.laseriot.dto.JobDispatchRequest;
import com.anne.laseriot.service.DeviceSessionService;
import com.anne.laseriot.service.GcodeStorageService;
import com.anne.laseriot.service.JobCoordinatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DeviceDownloadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @TempDir
    Path tempDir;

    private static final String DEVICE_CODE = "LASER-HN-001";
    private static final String DEVICE_TOKEN = "mks-dlc32-secret-token";
    private static final String JOB_CODE = "JOB-DOWNLOAD-TEST-001";

    @BeforeEach
    void setUp() throws Exception {
        GcodeStorageService storageService = new GcodeStorageService(tempDir.toString());
        DeviceSessionService deviceSessionService = new DeviceSessionService(DEVICE_CODE, DEVICE_TOKEN);
        JobCoordinatorService jobCoordinatorService = new JobCoordinatorService(storageService, deviceSessionService, messagingTemplate);

        jobCoordinatorService.dispatchJob(JobDispatchRequest.builder()
                .jobCode(JOB_CODE)
                .deviceCode(DEVICE_CODE)
                .originalFilename("sample.gcode")
                .gcodeContent("G21\nG90\nG0 X0 Y0\nM3 S800\nG1 X100 Y100 F2000\nM5\n")
                .targetWidthMm(100.0)
                .targetHeightMm(100.0)
                .feedRate(2000)
                .laserPower(800)
                .build());

        DeviceDownloadController controller = new DeviceDownloadController(jobCoordinatorService, storageService, deviceSessionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testDownloadGcodeSuccessWithTokenAndSha256Header() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + JOB_CODE + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + DEVICE_TOKEN)
                        .header("X-Device-Code", DEVICE_CODE))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-File-SHA256"))
                .andExpect(header().string("X-File-SHA256", not(emptyOrNullString())))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sample.gcode")))
                .andExpect(content().string(containsString("G1 X100 Y100 F2000")));
    }

    @Test
    void testDownloadWithoutAuthorizationFails() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + JOB_CODE + "/download")
                        .header("X-Device-Code", DEVICE_CODE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDownloadWithWrongTokenFails() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + JOB_CODE + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token-xyz")
                        .header("X-Device-Code", DEVICE_CODE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDownloadWithMismatchedDeviceCodeFails() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/" + JOB_CODE + "/download")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + DEVICE_TOKEN)
                        .header("X-Device-Code", "LASER-HCM-999"))
                .andExpect(status().isForbidden());
    }
}

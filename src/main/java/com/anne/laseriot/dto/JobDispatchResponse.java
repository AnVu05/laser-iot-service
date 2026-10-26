package com.anne.laseriot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDispatchResponse {
    private String jobCode;
    private String deviceCode;
    private String downloadUrl;
    private Long fileSizeBytes;
    private String sha256Checksum;
    private Integer totalLines;
    private String status;
    @Builder.Default
    private Long dispatchedAt = Instant.now().toEpochMilli();
}

package com.anne.laseriot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDetailDto {
    private String jobCode;
    private String deviceCode;
    private String originalFilename;
    private String storedFilePath;
    private Long fileSizeBytes;
    private String sha256Checksum;
    private Double targetWidthMm;
    private Double targetHeightMm;
    private Integer feedRate;
    private Integer laserPower;
    private Integer totalLines;
    private String status;
    private Double currentProgress;
    private String materialType;
    private String downloadUrl;
    private Long dispatchedAt;
    private Long completedAt;
}

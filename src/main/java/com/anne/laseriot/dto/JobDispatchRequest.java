package com.anne.laseriot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDispatchRequest {
    @NotBlank(message = "jobCode không được để trống")
    private String jobCode;

    @NotBlank(message = "deviceCode không được để trống")
    private String deviceCode;

    private String originalFilename;

    @NotBlank(message = "gcodeContent không được để trống")
    private String gcodeContent;

    @NotNull(message = "targetWidthMm không được để trống")
    private Double targetWidthMm;

    @NotNull(message = "targetHeightMm không được để trống")
    private Double targetHeightMm;

    private Integer feedRate;
    private Integer laserPower;
    private String materialType;
    private String userId;
}

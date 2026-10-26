package com.anne.laseriot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceStatusDto {
    private String type;
    private String deviceCode;
    private String jobCode;
    private String status;
    private String message;
    private Long timestamp;
}

package com.anne.laseriot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceHeartbeatDto {
    private String type;
    private String deviceCode;
    private String status;
    private Long freeHeap;
    private Integer wifiRssi;
    private Long uptimeMs;
    private Long configVersion;
    private Long timestamp;
}

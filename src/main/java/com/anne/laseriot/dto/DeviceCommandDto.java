package com.anne.laseriot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandDto {
    @NotBlank(message = "Lệnh command không được để trống (PAUSE, RESUME, CANCEL, HOME)")
    private String action;

    private String jobCode;
    private String reason;
}

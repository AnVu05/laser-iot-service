package com.anne.laseriot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceConfigDto {
    @NotNull(message = "maxWidthMm không được để trống")
    @Min(value = 10, message = "Chiều rộng tối thiểu 10mm")
    @Max(value = 1000, message = "Chiều rộng tối đa 1000mm")
    private Double maxWidthMm;

    @NotNull(message = "maxHeightMm không được để trống")
    @Min(value = 10, message = "Chiều cao tối thiểu 10mm")
    @Max(value = 1000, message = "Chiều cao tối đa 1000mm")
    private Double maxHeightMm;

    @NotNull(message = "maxSpeedMmMin không được để trống")
    @Min(value = 100, message = "Tốc độ tối thiểu 100mm/phút")
    @Max(value = 10000, message = "Tốc độ tối đa 10000mm/phút")
    private Integer maxSpeedMmMin;

    @NotNull(message = "maxPowerS không được để trống")
    @Min(value = 0, message = "Công suất tối thiểu 0")
    @Max(value = 1000, message = "Công suất tối đa 1000")
    private Integer maxPowerS;

    @NotNull(message = "configVersion không được để trống")
    private Long configVersion;
}

package com.anne.laseriot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTelemetryDto {
    private String type;
    private String deviceCode;
    private String jobCode;
    private String status;
    private Double progressPercent;
    private Integer currentLine;
    private Integer totalLines;
    
    // Toạ độ thực tế của đầu khắc laser
    private Double x;
    private Double y;

    // Thông số gia công tức thời
    private Integer feedRate;
    private Integer laserPower;
    private Integer remainingSeconds;

    // Lỗi phần cứng (nếu có)
    private String errorCode;
    private String errorMessage;

    // Dữ liệu cảm biến phần cứng: nhiệt độ, phát hiện lửa, công tắc an toàn, giới hạn hành trình
    private Map<String, Object> sensors;

    private Long timestamp;
}

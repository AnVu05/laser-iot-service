package com.anne.laseriot.config;

import com.anne.laseriot.service.DeviceSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class DeviceStompAuthenticationInterceptor implements ChannelInterceptor {

    private final DeviceSessionService deviceSessionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        // 1. Xử lý xác thực khi bo mạch gửi frame CONNECT
        if (StompCommand.CONNECT.equals(command)) {
            String deviceCode = accessor.getFirstNativeHeader("x-device-code");
            String authHeader = accessor.getFirstNativeHeader("authorization");

            if (deviceCode == null || deviceCode.isBlank()) {
                log.warn("[STOMP AUTH] Từ chối kết nối: Thiếu header 'x-device-code'");
                throw new SecurityException("Thiếu header định danh thiết bị: x-device-code");
            }

            if (authHeader == null || authHeader.isBlank()) {
                log.warn("[STOMP AUTH] Từ chối kết nối từ máy '{}': Thiếu header 'authorization'", deviceCode);
                throw new SecurityException("Thiếu header xác thực: authorization");
            }

            String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7).trim() : authHeader.trim();
            boolean valid = deviceSessionService.authenticateDevice(deviceCode, token);

            if (!valid) {
                log.warn("[STOMP AUTH] Token không hợp lệ cho thiết bị '{}'", deviceCode);
                throw new SecurityException("Khóa bảo mật thiết bị không chính xác");
            }

            // Gắn mã thiết bị đã xác thực vào session attributes
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                sessionAttributes.put("authenticatedDeviceCode", deviceCode.toUpperCase());
            }

            log.info("[STOMP AUTH] Thiết bị '{}' đã xác thực thành công qua STOMP CONNECT", deviceCode);
        }

        // 2. Bảo vệ topic khi bo mạch gửi frame SUBSCRIBE
        // Ngăn thiết bị A nghe lén topic của thiết bị B
        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            String authenticatedCode = sessionAttributes != null ? (String) sessionAttributes.get("authenticatedDeviceCode") : null;

            if (destination != null && destination.startsWith("/topic/device/")) {
                String[] parts = destination.split("/");
                // Format: /topic/device/{deviceCode}/...
                if (parts.length >= 4) {
                    String requestedCode = parts[3];
                    if (authenticatedCode == null || !authenticatedCode.equalsIgnoreCase(requestedCode)) {
                        log.warn("[STOMP AUTH] Từ chối quyền subscribe: Thiết bị '{}' không được phép nghe topic '{}'",
                                authenticatedCode, destination);
                        throw new SecurityException("Không có quyền truy cập topic của thiết bị khác");
                    }
                }
            }
        }

        return message;
    }
}

package com.anne.laseriot.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final DeviceStompAuthenticationInterceptor deviceStompAuthInterceptor;
    private final DeviceEndpointHandshakeInterceptor handshakeInterceptor;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(deviceStompAuthInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // - /topic: Broadcast sự kiện và lệnh cho thiết bị
        // - /queue: Gửi tin nhắn đích danh
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix định tuyến cho các tin nhắn từ Client/Bo mạch gửi lên Controller (@MessageMapping)
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint duy nhất kết nối cho bo mạch Makerbase MKS DLC32 V2.1
        registry.addEndpoint("/ws/device")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}

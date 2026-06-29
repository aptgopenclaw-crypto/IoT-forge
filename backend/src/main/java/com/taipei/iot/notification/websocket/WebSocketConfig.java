package com.taipei.iot.notification.websocket;

import com.taipei.iot.config.CorsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final CorsProperties corsProperties;

	@PostConstruct
	void validateAndLog() {
		String[] origins = corsProperties.getAllowedOrigins();
		if (origins == null || origins.length == 0) {
			throw new IllegalStateException("WebSocket CORS: allowedOrigins must be configured (cors.allowed-origins)");
		}
		for (String origin : origins) {
			if ("*".equals(origin.trim())) {
				throw new IllegalStateException("WebSocket CORS: allowedOrigins must not contain '*' — "
						+ "configure explicit origins in cors.allowed-origins");
			}
		}
		log.info("WebSocket CORS allowedOrigins={}", Arrays.asList(origins));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic", "/queue");
		config.setApplicationDestinationPrefixes("/app");
		config.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.getAllowedOrigins()).withSockJS();
	}

}

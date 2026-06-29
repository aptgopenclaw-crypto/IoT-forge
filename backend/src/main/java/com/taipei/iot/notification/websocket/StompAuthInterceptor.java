package com.taipei.iot.notification.websocket;

import com.taipei.iot.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;

@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@RequiredArgsConstructor
public class StompAuthInterceptor implements WebSocketMessageBrokerConfigurer {

	private final JwtUtil jwtUtil;

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new ChannelInterceptor() {
			@Override
			public Message<?> preSend(Message<?> message, MessageChannel channel) {
				StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
				if (accessor == null) {
					return message;
				}

				if (StompCommand.CONNECT.equals(accessor.getCommand())) {
					handleConnect(accessor);
				}
				else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
					handleSubscribe(accessor);
				}
				else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
					handleDisconnect(accessor);
				}

				return message;
			}
		});
	}

	private void handleConnect(StompHeaderAccessor accessor) {
		String token = accessor.getFirstNativeHeader("Authorization");
		if (token != null && token.startsWith("Bearer ")) {
			token = token.substring(7);
			try {
				Claims claims = jwtUtil.parseToken(token);
				String userId = claims.getSubject();
				String tenantId = claims.get("tenantId", String.class);
				UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userId, tenantId,
						List.of());
				accessor.setUser(auth);
			}
			catch (Exception e) {
				log.warn("WebSocket STOMP auth failed: {}", e.getMessage());
				throw new MessageDeliveryException("Authentication failed: invalid or expired token");
			}
		}
		else {
			throw new MessageDeliveryException("Authentication required: missing Authorization header");
		}
	}

	private void handleSubscribe(StompHeaderAccessor accessor) {
		Principal user = accessor.getUser();
		String destination = accessor.getDestination();

		if (user == null) {
			throw new MessageDeliveryException("Subscription denied: not authenticated");
		}
		if (destination == null) {
			throw new MessageDeliveryException("Subscription denied: missing destination");
		}

		String allowedPrefix = "/user/" + user.getName() + "/queue/";
		if (!destination.startsWith(allowedPrefix)) {
			log.warn("User [{}] attempted to subscribe to unauthorized destination: {}", user.getName(), destination);
			throw new MessageDeliveryException("Subscription denied: cannot subscribe to other user's queue");
		}
	}

	private void handleDisconnect(StompHeaderAccessor accessor) {
		Principal user = accessor.getUser();
		if (user != null) {
			log.info("WebSocket disconnect user={}", user.getName());
		}
		else {
			log.info("WebSocket disconnect (unauthenticated session)");
		}
	}

}

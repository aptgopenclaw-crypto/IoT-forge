package com.taipei.iot.notification.websocket;

import com.taipei.iot.auth.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private MessageChannel messageChannel;

	private ChannelInterceptor interceptor;

	@BeforeEach
	void setUp() {
		StompAuthInterceptor config = new StompAuthInterceptor(jwtUtil);

		// Extract the ChannelInterceptor registered by configureClientInboundChannel
		ChannelRegistration registration = new ChannelRegistration();
		config.configureClientInboundChannel(registration);

		// Use reflection to get the interceptors list from ChannelRegistration
		try {
			Field interceptorsField = ChannelRegistration.class.getDeclaredField("interceptors");
			interceptorsField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<ChannelInterceptor> interceptors = (List<ChannelInterceptor>) interceptorsField.get(registration);
			assertNotNull(interceptors);
			assertFalse(interceptors.isEmpty());
			this.interceptor = interceptors.get(0);
		}
		catch (NoSuchFieldException | IllegalAccessException e) {
			fail("Cannot extract interceptor from ChannelRegistration: " + e.getMessage());
		}
	}

	// --- CONNECT tests ---

	@Test
	void connect_withValidToken_shouldSetUser() {
		Claims claims = new DefaultClaims(Map.of("sub", "user-123", "tenantId", "tenant-A"));

		when(jwtUtil.parseToken("valid-token")).thenReturn(claims);

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.addNativeHeader("Authorization", "Bearer valid-token");
		accessor.setLeaveMutable(true);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, messageChannel);

		assertNotNull(result);
		StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
		assertNotNull(resultAccessor.getUser());
		assertEquals("user-123", resultAccessor.getUser().getName());
	}

	@Test
	void connect_withoutAuthorizationHeader_shouldThrow() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
				() -> interceptor.preSend(message, messageChannel));
		assertTrue(ex.getMessage().contains("Authentication required"));
	}

	@Test
	void connect_withInvalidToken_shouldThrow() {
		when(jwtUtil.parseToken("bad-token")).thenThrow(new RuntimeException("expired"));

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.addNativeHeader("Authorization", "Bearer bad-token");
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
				() -> interceptor.preSend(message, messageChannel));
		assertTrue(ex.getMessage().contains("Authentication failed"));
	}

	// --- SUBSCRIBE tests (N-1 fix) ---

	@Test
	void subscribe_toOwnQueue_shouldPass() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/user/user-123/queue/notifications");
		accessor.setUser(new UsernamePasswordAuthenticationToken("user-123", "tenant-A", List.of()));
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, messageChannel);

		assertNotNull(result);
	}

	@Test
	void subscribe_toOtherUserQueue_shouldThrow() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/user/user-999/queue/notifications");
		accessor.setUser(new UsernamePasswordAuthenticationToken("user-123", "tenant-A", List.of()));
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
				() -> interceptor.preSend(message, messageChannel));
		assertTrue(ex.getMessage().contains("Subscription denied"));
	}

	@Test
	void subscribe_withoutAuthentication_shouldThrow() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/user/user-123/queue/notifications");
		// No user set (simulating unauthenticated state)
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
				() -> interceptor.preSend(message, messageChannel));
		assertTrue(ex.getMessage().contains("not authenticated"));
	}

	@Test
	void subscribe_withNullDestination_shouldThrow() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		// destination not set
		accessor.setUser(new UsernamePasswordAuthenticationToken("user-123", "tenant-A", List.of()));
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
				() -> interceptor.preSend(message, messageChannel));
		assertTrue(ex.getMessage().contains("missing destination"));
	}

	@Test
	void subscribe_toArbitraryTopic_shouldThrow() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setDestination("/topic/global-announcements");
		accessor.setUser(new UsernamePasswordAuthenticationToken("user-123", "tenant-A", List.of()));
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		MessageDeliveryException ex = assertThrows(MessageDeliveryException.class,
				() -> interceptor.preSend(message, messageChannel));
		assertTrue(ex.getMessage().contains("Subscription denied"));
	}

	// --- Non-CONNECT/SUBSCRIBE commands should pass through ---

	@Test
	void send_command_shouldPassThrough() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setDestination("/app/some-endpoint");
		accessor.setUser(new UsernamePasswordAuthenticationToken("user-123", "tenant-A", List.of()));
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, messageChannel);

		assertNotNull(result);
	}

	// --- DISCONNECT tests (N-8 fix) ---

	@Test
	void disconnect_withAuthenticatedUser_shouldLogAndPassThrough() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		accessor.setUser(new UsernamePasswordAuthenticationToken("user-123", "tenant-A", List.of()));
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, messageChannel);

		assertNotNull(result);
	}

	@Test
	void disconnect_withoutUser_shouldLogAndPassThrough() {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
		// No user set (unauthenticated session)
		Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		Message<?> result = interceptor.preSend(message, messageChannel);

		assertNotNull(result);
	}

}

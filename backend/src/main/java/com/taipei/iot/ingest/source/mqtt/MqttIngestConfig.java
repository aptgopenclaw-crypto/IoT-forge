package com.taipei.iot.ingest.source.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.nio.charset.StandardCharsets;

/**
 * EMQX/MQTT 訂閱接入配置。預設停用（{@code mqtt.enabled=false}），避免本機/CI 無 broker 時啟動失敗； 部署時設
 * {@code MQTT_ENABLED=true} 啟用。實際解碼/接入邏輯委派 {@link TelemetryMqttHandler}（已單元測試）。
 */
@Configuration
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttIngestConfig {

	@Bean
	public MqttPahoClientFactory mqttClientFactory(@Value("${mqtt.broker-url}") String brokerUrl,
			@Value("${mqtt.username:}") String username, @Value("${mqtt.password:}") String password) {
		DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
		MqttConnectOptions options = new MqttConnectOptions();
		options.setServerURIs(new String[] { brokerUrl });
		options.setCleanSession(true);
		options.setAutomaticReconnect(true);
		if (!username.isBlank()) {
			options.setUserName(username);
			options.setPassword(password.toCharArray());
		}
		factory.setConnectionOptions(options);
		return factory;
	}

	@Bean
	public MessageChannel mqttInboundChannel() {
		return new DirectChannel();
	}

	@Bean
	public MessageProducer mqttInbound(MqttPahoClientFactory clientFactory, MessageChannel mqttInboundChannel,
			@Value("${mqtt.client-id}") String clientId, @Value("${mqtt.telemetry-topic}") String telemetryTopic,
			@Value("${mqtt.qos:1}") int qos, @Value("${mqtt.completion-timeout:30000}") long completionTimeout) {
		MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(clientId + "-ingest",
				clientFactory, telemetryTopic);
		DefaultPahoMessageConverter converter = new DefaultPahoMessageConverter();
		converter.setPayloadAsBytes(true);
		adapter.setConverter(converter);
		adapter.setQos(qos);
		adapter.setCompletionTimeout(completionTimeout);
		adapter.setOutputChannel(mqttInboundChannel);
		return adapter;
	}

	@Bean
	@ServiceActivator(inputChannel = "mqttInboundChannel")
	public MessageHandler mqttMessageHandler(TelemetryMqttHandler telemetryMqttHandler) {
		return message -> {
			Object topicHeader = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
			String topic = topicHeader != null ? topicHeader.toString() : null;
			Object payload = message.getPayload();
			byte[] bytes = (payload instanceof byte[] b) ? b : String.valueOf(payload).getBytes(StandardCharsets.UTF_8);
			telemetryMqttHandler.handle(topic, bytes);
		};
	}

}

package io.riox.springxd.sinks.websocket;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;
import java.util.concurrent.Executors;


@Configuration
@EnableIntegration
@ComponentScan
public class WebsocketSink {

	static final Logger log = LoggerFactory.getLogger(WebsocketSink.class);

	@Value("${port}")
	int port;

	@Value("${ssl}")
	boolean ssl;

	@PostConstruct
	public void init() throws InterruptedException, CertificateException, SSLException {
		log.info("Starting netty websocket server...");
		webSocketServerNetty().run();
		log.info("Started netty server on port {}", port);
	}

	@Bean
	NettyWebSocketServer webSocketServerNetty() {
		return new NettyWebSocketServer(port);
	}

	@Bean
	MessageHandler webSocketOutboundAdapter() {
		return new NettyWebSocketOutboundMessageHandler();
	}

	@Bean
	MessageChannel input() {
		return new DirectChannel();
	}

	@Bean
	IntegrationFlow webSocketFlow() {
		return IntegrationFlows
				.from(input())
				.channel(c -> c.executor(Executors.newCachedThreadPool()))
				.handle(webSocketOutboundAdapter()).get();
	}

}

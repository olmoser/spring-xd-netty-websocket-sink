package io.riox.springxd.sinks.websocket;

import io.netty.channel.Channel;

import java.security.cert.CertificateException;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.Channels;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannelSpec;
import org.springframework.integration.dsl.support.Function;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

@Configuration
@EnableIntegration
@ComponentScan
public class WebsocketSink {

	static final Logger log = LoggerFactory.getLogger(WebsocketSink.class);

	@Value("${port}")
	int port;

	@Value("${path}")
	String path;

	@Value("${ssl}")
	boolean ssl;

	private MessageHandler handler;
	private DirectChannel channel;

	private static final Map<Integer,NettyWebSocketServer> SERVERS = new ConcurrentHashMap<>();

	protected static final String MSG_HEADER_PATH = "__path";

	@PostConstruct
	public void init() throws InterruptedException, CertificateException, SSLException {
		log.info("Starting netty websocket server...");
		webSocketServerNetty();
		log.info("Started netty server on port {}", port);
	}

	@Bean
	synchronized NettyWebSocketServer webSocketServerNetty() throws InterruptedException, CertificateException, SSLException {
		if(!SERVERS.containsKey(port)) {
			NettyWebSocketServer server = new NettyWebSocketServer(port);
			server.run();
			SERVERS.put(port, server);
		}
		if(!NettyWebSocketServer.pathToChannels.containsKey(path)) {
			NettyWebSocketServer.pathToChannels.put(path, new LinkedList<Channel>());
		}
		return SERVERS.get(port);
	}

	@Bean
	MessageHandler webSocketOutboundAdapter() {
		if(handler == null) {
			final MessageHandler h2 = new NettyWebSocketOutboundMessageHandler(path);
			handler = new MessageHandler() {
				public void handleMessage(Message<?> msg) throws MessagingException {
					if(path.equals(msg.getHeaders().get(MSG_HEADER_PATH))) {
						h2.handleMessage(msg);
					}
				}
			};
		}
		return handler;
	}

	@Bean
	MessageChannel input() {
		if(channel == null) {
			channel = new DirectChannel() {
				protected boolean doSend(Message<?> message, long timeout) {
					Message<?> newMsg = MessageBuilder.fromMessage(message).setHeader(MSG_HEADER_PATH, path).build();
					return super.doSend(newMsg, timeout);
				}
			};
		}
		return channel;
	}

	@Bean
	IntegrationFlow webSocketFlow() {
		Function<Channels, MessageChannelSpec<?, ?>> func = new Function<Channels, MessageChannelSpec<?,?>>() {
			public MessageChannelSpec<?, ?> apply(Channels c) {
				return c.executor(Executors.newCachedThreadPool());
			}
		};
		return IntegrationFlows
				.from(input())
				.channel(func)
				.handle(webSocketOutboundAdapter()).get();
	}

}

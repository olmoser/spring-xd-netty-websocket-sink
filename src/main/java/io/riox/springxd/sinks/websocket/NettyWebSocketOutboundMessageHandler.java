package io.riox.springxd.sinks.websocket;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.handler.AbstractMessageHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

/**
 * Straight forward <code>MessageHandler</code> implementation that forwards all incoming messages to
 * all available netty channels
 *
 * @author omoser
 * @author whummer
 */
//@Component
public class NettyWebSocketOutboundMessageHandler extends AbstractMessageHandler {

	static final Logger log = LoggerFactory.getLogger(NettyWebSocketOutboundMessageHandler.class);
	String path;

	public NettyWebSocketOutboundMessageHandler(String path) {
		this.path = path;
	}

	@Override
	public String getComponentType() {
		return "netty-websocket:outbound-channel-adapter";
	}

	@Override
	protected void handleMessageInternal(Message<?> message) throws Exception {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);
		headers.setMessageTypeIfNotSet(SimpMessageType.MESSAGE);
		for (Channel channel : NettyWebSocketServer.pathToChannels.get(path)) {
			String messagePayload = message.getPayload().toString();
			log.trace("Writing message {} to channel {}", messagePayload, channel.localAddress());
			channel.write(new TextWebSocketFrame(messagePayload));
			channel.flush();
		}
	}
}

/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.riox.springxd.sinks.websocket;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles handshakes and messages
 */
public class NettyWebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

	private static final Logger log = LoggerFactory.getLogger(NettyWebSocketServerHandler.class);

	private static final String WEBSOCKET_PATH = "/websocket";

	private WebSocketServerHandshaker handshaker;

	private boolean ssl;

	public NettyWebSocketServerHandler(boolean ssl) {
		this.ssl = ssl;
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof FullHttpRequest) {
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	protected void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
		// Handle a bad request.
		if (!req.getDecoderResult().isSuccess()) {
			log.warn("Bad request: {}", req.getUri());
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
			return;
		}

		// Allow only GET methods.
		if (req.getMethod() != GET) {
			log.warn("Unsupported HTTP method: {}", req.getMethod());
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
			return;
		}

		if (!additionalHttpRequestHandler(ctx, req)) {
			return;
		}

		// Handshake
		WebSocketServerHandshakerFactory wsFactory
				= new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true);

		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
			Map<String,List<Channel>> pathsToChannels = NettyWebSocketServer.getPathsToChannels();
			List<Channel> existing = pathsToChannels.get(req.getUri());
			if(existing == null) {
				sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN));
			} else {
				existing.add(ctx.channel());
			}
		}
	}



	protected void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
		// Check for closing frame
		if (frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
			return;
		}
		if (frame instanceof PingWebSocketFrame) {
			ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		if (!(frame instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
					.getName()));
		}

		handleTextWebSocketFrameInternal((TextWebSocketFrame) frame, ctx);
	}

	protected boolean additionalHttpRequestHandler(ChannelHandlerContext ctx, FullHttpRequest req) {
		// implement other HTTP request logic
		return true; // continue processing
	}

	// simple echo implementation
	protected void handleTextWebSocketFrameInternal(TextWebSocketFrame frame, ChannelHandlerContext ctx) {
		log.debug("%s received %s%n", ctx.channel(), frame.text());
		ctx.channel().write(new TextWebSocketFrame("You said: " + frame.text()));
	}

	void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse res) {
		// Generate an error page if response getStatus code is not OK (200).
		if (res.getStatus().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpHeaders.setContentLength(res, res.content().readableBytes());
		}

		// Send the response and close the connection if necessary.
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	String getWebSocketLocation(FullHttpRequest req) {
		String location = req.headers().get(HOST) + WEBSOCKET_PATH;
		if (ssl) {
			return "wss://" + location;
		} else {
			return "ws://" + location;
		}
	}
}

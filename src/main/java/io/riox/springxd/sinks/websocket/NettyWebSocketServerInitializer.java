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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslContext;

public class NettyWebSocketServerInitializer extends
		ChannelInitializer<SocketChannel> {

	private final SslContext sslCtx;
	private static final String HANDLER_CLASS = NettyWebSocketServerHandler.class
			.getName();

	public NettyWebSocketServerInitializer(SslContext sslCtx) {
		this.sslCtx = sslCtx;
	}

	public NettyWebSocketServerInitializer() {
		sslCtx = null;
	}

	@Override
	public void initChannel(SocketChannel ch) throws Exception {
		ChannelPipeline pipeline = ch.pipeline();
		if (sslCtx != null) {
			pipeline.addLast(sslCtx.newHandler(ch.alloc()));
		}
		pipeline.addLast(new HttpServerCodec());
		pipeline.addLast(new HttpObjectAggregator(65536));
		/* need to use reflection here, due to Spring XD classloader isolation */
		Object handler = Class.forName(HANDLER_CLASS)
				.getConstructor(boolean.class).newInstance(sslCtx != null);
		pipeline.addLast((ChannelHandler)handler);
	}
}

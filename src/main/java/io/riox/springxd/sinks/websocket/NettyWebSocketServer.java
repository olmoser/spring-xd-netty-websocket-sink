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


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * simple websocket server implementation based on netty
 * inspired by the netty websocket server example
 *
 * @see <a href="https://github.com/netty/netty/tree/master/example/src/main/java/io/netty/example/http/websocketx/server">netty websocket server example</a>
 *
 * @author omoser
 * @author whummer
 */
public class NettyWebSocketServer {

	public static final int DEFAULT_PORT = 9292;

	static final Logger log = LoggerFactory.getLogger(NettyWebSocketServer.class);

	static final Map<String,List<Channel>> pathToChannels = Collections.synchronizedMap(new HashMap<String,List<Channel>>());

	public static final int BOSS_GROUP_THREADS = 1;

	NettyWebSocketServerInitializer nettyWebSocketServerInitializer;

	EventLoopGroup bossGroup = new NioEventLoopGroup(BOSS_GROUP_THREADS);

	EventLoopGroup workerGroup = new NioEventLoopGroup();

	boolean ssl;

	int port = DEFAULT_PORT;

	// for cmdline testing
	public static void main(String[] args) throws Exception {
		new NettyWebSocketServer().run();
	}

	public NettyWebSocketServer() {
	}

	public NettyWebSocketServer(int port) {
		this.port = port;
	}

	public void run() throws SSLException, CertificateException, InterruptedException {
		// Configure SSL.
		final SslContext sslCtx;
		if (ssl) {
			SelfSignedCertificate ssc = new SelfSignedCertificate();
			sslCtx = SslContext.newServerContext(ssc.certificate(), ssc.privateKey());
		} else {
			sslCtx = null;
		}

		new ServerBootstrap().group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new NettyWebSocketServerInitializer(sslCtx))
				.bind(port)
				.sync()
				.channel();


		log.info("************************************************");
		log.info("Started netty websocket server on port {}", port);
		log.info("************************************************");
	}

	@PreDestroy
	public void shutdown() {
		bossGroup.shutdownGracefully();
		workerGroup.shutdownGracefully();
	}
}

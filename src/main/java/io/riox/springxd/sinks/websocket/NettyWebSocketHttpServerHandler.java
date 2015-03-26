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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Handles handshakes and messages
 */

public class NettyWebSocketHttpServerHandler extends NettyWebSocketServerHandler {

	public NettyWebSocketHttpServerHandler(boolean ssl) {
		super(ssl);
	}

	@Override
	protected boolean additionalHttpRequestHandler(ChannelHandlerContext ctx, FullHttpRequest req) {
		if ("/".equals(req.getUri())) {
			ByteBuf content = NettyWebSocketServerIndexPage.getContent(getWebSocketLocation(req));
			FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, OK, content);
			res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
			HttpHeaders.setContentLength(res, content.readableBytes());
			sendHttpResponse(ctx, req, res);
			return false;
		}

		if ("/favicon.ico".equals(req.getUri())) {
			FullHttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND);
			sendHttpResponse(ctx, req, res);
			return false;
		}

		return true; // continue processing
	}
}

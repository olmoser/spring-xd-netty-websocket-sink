package io.riox.springxd.sinks.websocket;

import org.springframework.xd.module.options.spi.ModuleOption;

/**
 * Created by omoser on 25/03/15.
 *
 * @author omoser
 */
public class NettyWebsocketSinkOptions {

	public static final int DEFAULT_PORT = 9292;

	private int port = DEFAULT_PORT;

	private boolean ssl;

	public int getPort() {
		return port;
	}

	@ModuleOption("the port to listen to")
	public void setPort(int port) {
		this.port = port;
	}

	public boolean isSsl() {
		return ssl;
	}

	@ModuleOption("true for wss://")
	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

}

/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.riox.springxd.sinks.websocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.integration.test.util.SocketUtils;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * @author Waldemar Hummer
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextHierarchy({
    @ContextConfiguration(classes = WebsocketSinkTests.ContextConfig1.class),
    @ContextConfiguration(classes = WebsocketSinkTests.ContextConfig2.class)
})
public class WebsocketSinkTests {

	static final List<WebsocketConfig> configs = new LinkedList<>();

	static final class WebsocketConfig {
		int port;
		String path;
		WebsocketSink sink;
	}

	public static class CommonConfig {
		@Bean
		public PropertySourcesPlaceholderConfigurer placeHolderConfigurer() {
			int port = SocketUtils.findAvailableServerSocket();
			WebsocketConfig config = new WebsocketConfig();
			synchronized (configs) {
				config.port = port;
				config.path = "/test" + (configs.size() + 1);
				configs.add(config);
			}
			Properties props = new Properties();
			props.put("port", config.port);
			props.put("path", config.path);
			props.put("ssl", false);
			PropertySourcesPlaceholderConfigurer c = new PropertySourcesPlaceholderConfigurer();
			c.setProperties(props);
			c.setEnvironment(new MockEnvironment());
			c.setLocalOverride(true);
			return c;
		}
	}

	@PropertySource("classpath:config1.properties")
	@ComponentScan
	@Configuration
	public static class ContextConfig1 extends CommonConfig {
		@Autowired
		WebsocketSink sink;
	}

	@PropertySource("classpath:config2.properties")
	@ComponentScan
	@Configuration
	public static class ContextConfig2 {
		@Autowired
		WebsocketSink sink;

		@PostConstruct
		public void post() throws Exception {
			Field f2 = WebsocketSink.class.getDeclaredField("path");
			f2.setAccessible(true);
			String path = (String) f2.get(sink);
			for(WebsocketConfig cfg : configs) {
				if(path.equals(cfg.path)) {
					cfg.sink = sink;
				}
			}
		}
	}

	@Test
	public void testMultiplePaths() throws Exception {

		final List<String> messages1 = new ArrayList<String>();
		final List<String> messages2 = new ArrayList<String>();

		Field f1 = WebsocketSink.class.getDeclaredField("port");
		f1.setAccessible(true);
		Field f2 = WebsocketSink.class.getDeclaredField("path");
		f2.setAccessible(true);

		WebsocketSink sink1 = configs.get(0).sink;
		WebsocketSink sink2 = configs.get(1).sink;

		Method m1 = WebsocketSink.class.getDeclaredMethod("input");
		m1.setAccessible(true);
		final MessageChannel channel1 = (MessageChannel) m1.invoke(sink1);
		final MessageChannel channel2 = (MessageChannel) m1.invoke(sink2);

		URI uri1 = new URI("http://localhost:" + configs.get(0).port + configs.get(0).path);
		URI uri2 = new URI("http://localhost:" + configs.get(1).port + configs.get(1).path);
		URI uri3 = new URI("http://localhost:" + configs.get(1).port + "/invalid_path");

		/* create WS clients */
		final int numMsg1 = 2000;
		final int numMsg2 = 3000;
		final CountDownLatch connectLatch = new CountDownLatch(3);
		final CountDownLatch messagesLatch = new CountDownLatch(numMsg1 + numMsg2);
		WebSocketClient c1 = new WebSocketClient(uri1) {
			public void onMessage(String msg) {
				messages1.add(msg);
				messagesLatch.countDown();
			}
			public void onClose(int arg0, String arg1, boolean arg2) {}
			public void onError(Exception e) {}
			public void onOpen(ServerHandshake arg0) {
				connectLatch.countDown();
			}
		};
		c1.connect();
		WebSocketClient c2 = new WebSocketClient(uri2) {
			public void onMessage(String msg) {
				messages2.add(msg);
				messagesLatch.countDown();
			}
			public void onClose(int arg0, String arg1, boolean arg2) {}
			public void onError(Exception e) {}
			public void onOpen(ServerHandshake arg0) {
				connectLatch.countDown();
			}
		};
		c2.connect();
		WebSocketClient c3 = new WebSocketClient(uri3) {
			public void onMessage(String msg) {
				messagesLatch.countDown();
			}
			public void onClose(int arg0, String arg1, boolean arg2) {}
			public void onError(Exception e) {
				e.printStackTrace();
			}
			public void onOpen(ServerHandshake arg0) {
				connectLatch.countDown();
			}
		};
		c3.connect();
		connectLatch.await(1, TimeUnit.SECONDS);

		/* send test messages */
		new Thread() {
			public void run() {
				for(int i = 0; i < numMsg1; i ++) {
					channel1.send(new GenericMessage<String>("foo"));
				}
			}
		}.start();
		new Thread() {
			public void run() {
				for(int i = 0; i < numMsg2; i ++) {
					channel2.send(new GenericMessage<String>("bar"));
				}
			}
		}.start();

		/* assertions */
		assertTrue(messagesLatch.await(5, TimeUnit.SECONDS));
		assertEquals(numMsg1, messages1.size());
		assertEquals(numMsg2, messages2.size());
		for(String m : messages1) {
			assertEquals("foo", m);
		}
		for(String m : messages2) {
			assertEquals("bar", m);
		}

	}

}

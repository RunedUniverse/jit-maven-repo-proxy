/*
 * Copyright © 2026 VenaNocta (venanocta@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.runeduniverse.lib.repo.maven.proxy.itest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.netty.channel.Channel;
import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class ARepoTest {

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

	protected ProxyServer server = null;
	protected SocketAddress socketAddress = null;
	protected Channel channel = null;

	protected abstract ProxyServerBuilder configure(ProxyServerBuilder builder);

	@BeforeAll
	public void before() throws InterruptedException {
		this.server = configure(new ProxyServerBuilder()).build();
		assert this.server != null;

		this.socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 3333);
		assert this.socketAddress != null;

		this.channel = server.bindChannel(this.socketAddress)
				.sync()
				.channel();
		assert this.channel != null;

		// it's online
	}

	@AfterAll
	public void shutdown() {
		try {
			this.channel.close();
		} finally {
			server.shutdownGracefully();
		}
	}

}

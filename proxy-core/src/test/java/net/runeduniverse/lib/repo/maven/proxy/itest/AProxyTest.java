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
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import io.netty.channel.Channel;
import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public abstract class AProxyTest {

	@TempDir
	protected Path repoPath;
	@TempDir
	protected Path clientPath;

	protected ProxyServer server = null;
	protected InetSocketAddress socketAddress = null;
	protected Channel channel = null;

	protected RepositorySource source = null;
	protected RepositorySourceClient client = null;

	public long timeout() {
		return 10;
	}

	public Path repoPath() {
		return this.repoPath;
	}

	public Path clientPath() {
		return this.repoPath;
	}

	public RepositorySourceClient client() {
		return this.client;
	}

	protected abstract ProxyServerBuilder configureProxy(ProxyServerBuilder builder);

	protected abstract RepositorySource configureClient();

	@BeforeEach
	public void before() throws InterruptedException {
		this.server = configureProxy(new ProxyServerBuilder()).build();
		assert this.server != null;

		this.socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 3333);
		assert this.socketAddress != null;

		this.channel = server.bindChannel(this.socketAddress)
				.sync()
				.channel();
		assert this.channel != null;

		// it's online

		this.source = configureClient();
		this.client = this.source.client();

		assert this.source.getLocalRepoPath() != null;
		assert this.client != null;
	}

	@AfterEach
	public void shutdown() {
		if (this.client != null) {
			this.client.shutdownGracefully();
		}
		try {
			this.channel.close();
		} finally {
			this.server.shutdownGracefully();
		}
	}

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

	public String systemProperty_repo_mvnCentral_urlHttp() {
		return System.getProperty("test.infra.repo.maven-central.url-http",
				"https://nexus.runeduniverse.net/repository/maven-central/");
	}

	public String systemProperty_r4m_version() {
		return System.getProperty("test.infra.r4m.version", "1.1.0");
	}
}

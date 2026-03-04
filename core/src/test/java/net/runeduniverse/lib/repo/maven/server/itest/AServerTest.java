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
package net.runeduniverse.lib.repo.maven.server.itest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;
import net.runeduniverse.lib.repo.maven.api.FileContentType;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public abstract class AServerTest {

	protected final EventLoopGroup mainGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
	protected final EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

	@TempDir
	protected Path repoPath;
	@TempDir
	protected Path clientPath;

	protected InetSocketAddress socketAddress = null;
	protected Channel channel = null;

	protected RepositorySource source = null;
	protected RepositorySourceClient client = null;

	protected ServerBootstrap serverBootstrap = null;

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

	protected abstract void configureRepoInstance(Map<String, MavenRepositoryInstance> instances);

	protected abstract RepositorySource configureClient();

	@BeforeEach
	public void before() throws InterruptedException {

		this.socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 3333);
		assert this.socketAddress != null;

		this.channel = bootstrap().bind(this.socketAddress)
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
			shutdownGracefully();
		}
	}

	protected ServerBootstrap bootstrap() {
		if (this.serverBootstrap != null)
			return this.serverBootstrap;

		final Map<String, MavenRepositoryInstance> instances = new ConcurrentHashMap<>();
		configureRepoInstance(instances);

		final ServerBootstrap bootstrap = new ServerBootstrap()//
				.group(this.mainGroup, this.workerGroup)
				.channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new HttpRepoServerInitializer(instances::get, fileTypeMappings(), fileTypes()));

		return this.serverBootstrap = bootstrap;
	}

	public void shutdownGracefully() {
		this.workerGroup.shutdownGracefully();
		this.mainGroup.shutdownGracefully();
	}

	protected Map<String, String> fileTypeMappings() {
		final Map<String, String> fType2cTypeMap = new LinkedHashMap<>();

		// xml files
		fType2cTypeMap.put("xml", "application/xml");
		fType2cTypeMap.put("pom", "application/xml");
		// signatures
		fType2cTypeMap.put("asc", "application/pgp-signature");
		// hashes as hex text
		fType2cTypeMap.put("md5", "text/plain; charset=UTF-8");
		fType2cTypeMap.put("sha1", "text/plain; charset=UTF-8");
		fType2cTypeMap.put("sha256", "text/plain; charset=UTF-8");
		fType2cTypeMap.put("sha512", "text/plain; charset=UTF-8");
		// all others fall back to "application/octet-stream" by default

		return fType2cTypeMap;
	}

	protected Map<String, FileContentType> fileTypes() {
		final Map<String, FileContentType> fTypeMap = new LinkedHashMap<>();

		fTypeMap.put("pom", FileContentType.POM);
		// signatures
		fTypeMap.put("asc", FileContentType.SIGNATURE);
		// hashes
		fTypeMap.put("md5", FileContentType.CHECKSUM);
		fTypeMap.put("sha1", FileContentType.CHECKSUM);
		fTypeMap.put("sha256", FileContentType.CHECKSUM);
		fTypeMap.put("sha512", FileContentType.CHECKSUM);
		// all others fall back to DATA by default

		return fTypeMap;
	}

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

}

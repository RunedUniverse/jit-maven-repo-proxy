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
package net.runeduniverse.lib.repo.maven.proxy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositoryInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.FileContentType;
import net.runeduniverse.lib.repo.maven.proxy.api.ProxyServer;

public class DefaultProxyServer implements ProxyServer {

	protected final Map<String, RepositoryInstance> instances = new LinkedHashMap<>();
	protected final Map<String, String> fType2cTypeMap;
	protected final Map<String, FileContentType> fTypeMap;

	protected final EventLoopGroup mainGroup;
	protected final EventLoopGroup workerGroup;

	protected ServerBootstrap serverBootstrap = null;

	public DefaultProxyServer(final Map<String, FileContentType> fTypeMap) {
		this(null, null, Collections.emptyMap(), fTypeMap);
	}

	public DefaultProxyServer(final Map<String, String> fType2cTypeMap, final Map<String, FileContentType> fTypeMap) {
		this(null, null, fType2cTypeMap, fTypeMap);
	}

	public DefaultProxyServer(final EventLoopGroup mainGroup, final EventLoopGroup workerGroup,
			final Map<String, String> fType2cTypeMap, final Map<String, FileContentType> fTypeMap) {
		this.mainGroup = mainGroup == null ? //
				new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()) : mainGroup;
		this.workerGroup = workerGroup == null ? //
				new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()) : workerGroup;
		this.fType2cTypeMap = fType2cTypeMap;
		this.fTypeMap = fTypeMap;
	}

	public void addInstance(final RepositoryInstance instance) {
		this.instances.put(instance.getPath(), instance);
	}

	protected ServerBootstrap bootstrap() {
		if (this.serverBootstrap != null)
			return this.serverBootstrap;

		final ServerBootstrap bootstrap = new ServerBootstrap()//
				.group(this.mainGroup, this.workerGroup)
				.channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new HttpRepoServerInitializer(this.instances::get, this.fType2cTypeMap, this.fTypeMap));

		return this.serverBootstrap = bootstrap;
	}

	protected ChannelFuture bindChannel(int port) {
		return bootstrap().bind(port);
	}

	public void start() throws InterruptedException {
		try {

			Channel ch = bindChannel(8080).sync()
					.channel();

			// it's active!

			ch.closeFuture()
					.sync();

		} finally {
			this.workerGroup.shutdownGracefully();
			this.mainGroup.shutdownGracefully();
		}
	}

}

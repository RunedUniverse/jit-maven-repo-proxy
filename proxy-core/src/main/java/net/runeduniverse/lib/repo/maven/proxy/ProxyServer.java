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

import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.runeduniverse.lib.repo.maven.api.FileTypeIndex;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;

public class ProxyServer {

	protected final Map<String, MavenRepositoryInstance> instances = new ConcurrentHashMap<>();
	protected final BiFunction<Function<String, MavenRepositoryInstance>, FileTypeIndex, ChannelInitializer<SocketChannel>> serverChannelInitializer;
	protected final FileTypeIndex fileTypeIndex;

	protected final EventLoopGroup mainGroup;
	protected final EventLoopGroup workerGroup;

	protected ServerBootstrap serverBootstrap = null;

	public ProxyServer(final FileTypeIndex fileTypeIndex,
			final BiFunction<Function<String, MavenRepositoryInstance>, FileTypeIndex, ChannelInitializer<SocketChannel>> serverChannelInitializer) {
		this(null, null, fileTypeIndex, serverChannelInitializer);
	}

	public ProxyServer(final EventLoopGroup mainGroup, final EventLoopGroup workerGroup,
			final FileTypeIndex fileTypeIndex,
			final BiFunction<Function<String, MavenRepositoryInstance>, FileTypeIndex, ChannelInitializer<SocketChannel>> serverChannelInitializer) {
		this.mainGroup = mainGroup == null ? //
				new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()) : mainGroup;
		this.workerGroup = workerGroup == null ? //
				new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()) : workerGroup;
		this.fileTypeIndex = fileTypeIndex;
		this.serverChannelInitializer = serverChannelInitializer;
	}

	public void addInstance(final MavenRepositoryInstance instance) {
		this.instances.put(instance.path(), instance);
	}

	protected ServerBootstrap bootstrap() {
		if (this.serverBootstrap != null)
			return this.serverBootstrap;

		final ServerBootstrap bootstrap = new ServerBootstrap()//
				.group(this.mainGroup, this.workerGroup)
				.channel(NioServerSocketChannel.class)
				.childHandler(this.serverChannelInitializer.apply(this.instances::get, this.fileTypeIndex));

		return this.serverBootstrap = bootstrap;
	}

	public ChannelFuture bindChannel(final int port) {
		return bootstrap().bind(port);
	}

	public ChannelFuture bindChannel(final SocketAddress localAddress) {
		return bootstrap().bind(localAddress);
	}

	public ChannelFuture bindChannel(final InetAddress inetHost, final int port) {
		return bootstrap().bind(inetHost, port);
	}

	public void shutdownGracefully() {
		this.workerGroup.shutdownGracefully();
		this.mainGroup.shutdownGracefully();
	}
}

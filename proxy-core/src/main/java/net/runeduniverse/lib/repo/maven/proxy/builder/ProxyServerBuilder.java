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
package net.runeduniverse.lib.repo.maven.proxy.builder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import net.runeduniverse.lib.repo.maven.api.FileTypeIndex;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;
import net.runeduniverse.lib.repo.maven.data.DefaultFileTypeIndex;
import net.runeduniverse.lib.repo.maven.proxy.DefaultCache;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class ProxyServerBuilder {

	protected final Map<String, RepoInstanceBuilder> instanceMap = new LinkedHashMap<>();

	protected Function<String, RepoInstanceBuilder> repoBuilderFactory = RepoInstanceBuilder::new;
	protected Function<MavenRepositoryProxyInstance, Cache> cacheFactory = DefaultCache::new;
	protected BiFunction<Map<String, MavenRepositoryInstance>, FileTypeIndex, ChannelInitializer<SocketChannel>> serverChannelInitializer = null;
	protected FileTypeIndex fileTypeIndex = null;

	public ProxyServerBuilder setInstanceBuilderFactory(Function<String, RepoInstanceBuilder> factory) {
		this.repoBuilderFactory = factory;
		return this;
	}

	public RepoInstanceBuilder instance(String path) {
		path = path.trim();
		path = path.replace("/", "");
		path = path.replace(".", "");
		return this.instanceMap.computeIfAbsent(path, this.repoBuilderFactory);
	}

	public ProxyServerBuilder instance(final String path, final Consumer<RepoInstanceBuilder> consumer) {
		consumer.accept(instance(path));
		return this;
	}

	public ProxyServerBuilder setCacheFactory(final Function<MavenRepositoryProxyInstance, Cache> factory) {
		this.cacheFactory = factory == null ? DefaultCache::new : factory;
		return this;
	}

	public ProxyServerBuilder setServerChannelInitializer(
			final BiFunction<Map<String, MavenRepositoryInstance>, FileTypeIndex, ChannelInitializer<SocketChannel>> initializer) {
		this.serverChannelInitializer = initializer;
		return this;
	}

	public ProxyServerBuilder setFileTypeIndex(final FileTypeIndex fileTypeIndex) {
		this.fileTypeIndex = fileTypeIndex;
		return this;
	}

	public ProxyServer build() {
		final ProxyServer server = new ProxyServer(
				this.fileTypeIndex == null ? new DefaultFileTypeIndex() : this.fileTypeIndex,
				this.serverChannelInitializer);

		for (Entry<String, RepoInstanceBuilder> entry : this.instanceMap.entrySet()) {
			server.addInstance(entry.getValue()
					.build(this.cacheFactory));
		}

		return server;
	}
}

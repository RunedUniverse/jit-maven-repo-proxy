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
import java.util.function.Consumer;
import java.util.function.Function;

import net.runeduniverse.lib.repo.maven.proxy.RepoServer;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositoryServer;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class RepoServerBuilder {

	protected final Map<String, RepoInstanceBuilder> instanceMap = new LinkedHashMap<>();

	protected Function<String, Cache> cacheFactory = null;

	public RepoInstanceBuilder instance(String path) {
		path = path.trim();
		path = path.replace("/", "");
		path = path.replace(".", "");
		return this.instanceMap.computeIfAbsent(path, RepoInstanceBuilder::new);
	}

	public RepoServerBuilder instance(String path, Consumer<RepoInstanceBuilder> consumer) {
		consumer.accept(instance(path));
		return this;
	}

	public RepoServerBuilder cacheFactory(Function<String, Cache> factory) {
		this.cacheFactory = factory;
		return this;
	}

	public RepositoryServer build() {
		final RepoServer server = new RepoServer();

		for (Entry<String, RepoInstanceBuilder> entry : this.instanceMap.entrySet()) {
			server.addInstance(entry.getValue()
					.build(this.cacheFactory));
		}

		return server;
	}

}

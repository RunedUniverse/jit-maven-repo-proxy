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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import net.runeduniverse.lib.repo.maven.proxy.DefaultRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupArtifactListener;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupMetadataListener;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class RepoInstanceBuilder {

	protected final Map<String, RepositorySource> sources = new LinkedHashMap<>();
	protected final Set<LookupMetadataListener> lookupMetadataListeners = new LinkedHashSet<>();
	protected final Set<LookupArtifactListener> lookupArtifactListeners = new LinkedHashSet<>();
	protected final String path;

	protected BiFunction<String, Function<MavenRepositoryProxyInstance, Cache>, MavenRepositoryProxyInstance> instanceFacory = DefaultRepositoryProxyInstance::new;

	public RepoInstanceBuilder(final String path) {
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}

	public RepoInstanceBuilder setInstanceFacory(
			BiFunction<String, Function<MavenRepositoryProxyInstance, Cache>, MavenRepositoryProxyInstance> factory) {
		this.instanceFacory = factory;
		return this;
	}

	public Map<String, RepositorySource> sourceMap() {
		return this.sources;
	}

	public RepoInstanceBuilder putSource(final RepositorySource source) {
		this.sources.put(source.key(), source);
		return this;
	}

	public RepoInstanceBuilder removeSource(final String key) {
		this.sources.remove(key);
		return this;
	}

	public RepoInstanceBuilder removeSource(final RepositorySource source) {
		return removeSource(source.key());
	}

	public RepoInstanceBuilder addListener(final LookupMetadataListener listener) {
		this.lookupMetadataListeners.add(listener);
		return this;
	}

	public RepoInstanceBuilder addListener(final LookupArtifactListener listener) {
		this.lookupArtifactListeners.add(listener);
		return this;
	}

	public MavenRepositoryProxyInstance build(final Function<MavenRepositoryProxyInstance, Cache> factory) {
		final MavenRepositoryProxyInstance instance = this.instanceFacory.apply(this.path, factory);
		instance.sources()
				.putAll(this.sources);
		for (LookupMetadataListener listener : this.lookupMetadataListeners) {
			instance.addListener(listener);
		}
		for (LookupArtifactListener listener : this.lookupArtifactListeners) {
			instance.addListener(listener);
		}
		return instance;
	}
}

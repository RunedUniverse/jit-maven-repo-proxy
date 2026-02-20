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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;
import net.runeduniverse.lib.repo.maven.proxy.api.SourceArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.api.SourceArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class DefaultRepositoryInstance implements MavenRepositoryProxyInstance {

	protected final Map<String, RepositorySource> sources = new ConcurrentHashMap<>();

	protected final String path;
	protected final Cache cache;

	public DefaultRepositoryInstance(final String path, final Function<MavenRepositoryProxyInstance, Cache> factory) {
		this.path = path;
		this.cache = factory.apply(this);
	}

	@Override
	public String path() {
		return this.path;
	}

	@Override
	public Cache cache() {
		return this.cache;
	}

	@Override
	public Map<String, RepositorySource> sources() {
		return this.sources;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		return this.cache.getMetadata(coords);
	}

	@Override
	public CompletableFuture<ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		return this.cache.getArtifact(coords);
	}

	@Override
	public CompletableFuture<SourceArtifactMetadata> lookupMetadata(final String sourceKey,
			final ArtifactCoordinates coords) {
		final RepositorySource defSource = this.sources.get(sourceKey);
		RepositorySourceClient client;
		if (defSource != null && (client = defSource.client()) != null) {
			return client.getMetadata(coords)
					.thenApply(metadata -> SourceArtifactMetadata.wrap(defSource, metadata));
		}

		final CompletableFuture<SourceArtifactMetadata> future = new CompletableFuture<>();
		final List<CompletableFuture<Void>> upstream = new LinkedList<>();

		for (RepositorySource source : this.sources.values()) {
			if ((client = source.client()) == null)
				continue;
			upstream.add(client.getMetadata(coords)
					.thenAccept(metadata -> {
						if (metadata == null)
							return;
						future.complete(SourceArtifactMetadata.wrap(source, metadata));
					}));
		}
		CompletableFuture.allOf(upstream.toArray(new CompletableFuture[upstream.size()]))
				.thenRun(() -> future.complete(null));

		return future;
	}

	@Override
	public CompletableFuture<SourceArtifactData> lookupArtifact(final String sourceKey,
			final ArtifactDataCoordinates coords) {
		final RepositorySource defSource = this.sources.get(sourceKey);
		RepositorySourceClient client;
		if (defSource != null && (client = defSource.client()) != null) {
			return defSource.client()
					.getArtifact(coords)
					.thenApply(data -> SourceArtifactData.wrap(defSource, data));
		}

		final CompletableFuture<SourceArtifactData> future = new CompletableFuture<>();
		final List<CompletableFuture<Void>> upstream = new LinkedList<>();

		for (RepositorySource source : this.sources.values()) {
			if ((client = source.client()) == null)
				continue;
			upstream.add(client.getArtifact(coords)
					.thenAccept(data -> {
						if (data == null)
							return;
						future.complete(SourceArtifactData.wrap(source, data));
					}));
		}
		CompletableFuture.allOf(upstream.toArray(new CompletableFuture[upstream.size()]))
				.thenRun(() -> future.complete(null));

		return future;
	}
}

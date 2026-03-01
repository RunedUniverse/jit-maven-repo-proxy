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
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;
import net.runeduniverse.lib.repo.maven.proxy.data.DefaultAggregateArtifactMetadata;

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
	public CompletableFuture<ArtifactMetadata> lookupMetadata(final ArtifactCoordinates coords) {
		final DefaultAggregateArtifactMetadata aggMetadata = new DefaultAggregateArtifactMetadata(coords);

		RepositorySourceClient client;
		for (RepositorySource source : this.sources.values()) {
			if ((client = source.client()) == null)
				continue;
			aggMetadata.track(attachToMetadataLookup(client, coords, client.getMetadata(coords)));
		}

		return aggMetadata.asFuture();
	}

	protected CompletableFuture<ArtifactMetadata> attachToMetadataLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final CompletableFuture<ArtifactMetadata> upstream) {
		final CompletableFuture<ArtifactMetadata> future = new CompletableFuture<>();
		upstream.handle((v, t) -> {
			try {
				future.complete(DefaultRepositoryInstance.this.interceptMetadataLookup(client, coords, v, t));
			} catch (Throwable e) {
				future.completeExceptionally(e);
			}
			return v;
		});
		return future;
	}

	protected ArtifactMetadata interceptMetadataLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final ArtifactMetadata metadata, final Throwable throwable)
			throws Throwable {
		// TODO do something with it!
		return metadata;
	}

	@Override
	public CompletableFuture<SourceArtifactData> lookupArtifact(final String sourceKey,
			final ArtifactDataCoordinates coords) {
		final RepositorySource defSource = sourceKey == null ? null : this.sources.get(sourceKey);
		RepositorySourceClient client;
		if (defSource != null && (client = defSource.client()) != null) {
			return attachToArtifactLookup(client, coords, true, defSource.client()
					.getArtifact(coords)).thenApply(data -> SourceArtifactData.wrap(sourceKey, data));
		}

		final CompletableFuture<SourceArtifactData> future = new CompletableFuture<>();
		final List<CompletableFuture<Void>> upstream = new LinkedList<>();

		for (RepositorySource source : this.sources.values()) {
			if ((client = source.client()) == null)
				continue;
			upstream.add(attachToArtifactLookup(client, coords, false, client.getArtifact(coords)).thenAccept(data -> {
				if (data == null)
					return;
				future.complete(SourceArtifactData.wrap(source.key(), data));
			}));
		}
		CompletableFuture.allOf(upstream.toArray(new CompletableFuture<?>[0]))
				.thenRun(() -> future.complete(null));

		return future;
	}

	protected CompletableFuture<ArtifactData> attachToArtifactLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final boolean exact, final CompletableFuture<ArtifactData> upstream) {
		final CompletableFuture<ArtifactData> future = new CompletableFuture<>();
		upstream.handle((v, t) -> {
			try {
				future.complete(DefaultRepositoryInstance.this.interceptArtifactLookup(client, coords, exact, v, t));
			} catch (Throwable e) {
				future.completeExceptionally(e);
			}
			return v;
		});
		return future;
	}

	protected ArtifactData interceptArtifactLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final boolean exact, final ArtifactData data, final Throwable throwable)
			throws Throwable {
		if (throwable != null) {
			// exactly that source was requested -> errors are deserved
			if (exact)
				throw throwable;
			// bury it! -> if 1 fails all do!
			return data;
		}

		// TODO do something with it!
		return data;
	}
}

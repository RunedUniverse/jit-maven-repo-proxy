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
package net.runeduniverse.lib.repo.maven.proxy.cache.caffeine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.SourceArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class CacheAdapter implements Cache {

	protected final MavenRepositoryProxyInstance proxy;
	protected final AsyncLoadingCache<ArtifactCoordinates, ArtifactMetadata> metadataCache;
	protected final AsyncLoadingCache<ArtifactDataCoordinates, ArtifactData> artifactCache;

	public CacheAdapter(final MavenRepositoryProxyInstance proxy) {
		this.proxy = proxy;
		this.metadataCache = Caffeine.newBuilder()
				.buildAsync(this::lookupMetadata);
		this.artifactCache = Caffeine.newBuilder()
				.buildAsync(this::lookupArtifact);
	}

	protected CompletableFuture<ArtifactMetadata> lookupMetadata(final ArtifactCoordinates coords,
			final Executor executor) {
		return this.proxy.lookupMetadata(coords);
	}

	protected CompletableFuture<SourceArtifactData> lookupArtifact(final ArtifactDataCoordinates coords,
			final Executor executor) {
		return this.proxy.lookupArtifact(null, coords);
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		return this.metadataCache.get(coords);
	}

	@Override
	public CompletableFuture<? extends ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		return this.artifactCache.get(coords);
	}
}

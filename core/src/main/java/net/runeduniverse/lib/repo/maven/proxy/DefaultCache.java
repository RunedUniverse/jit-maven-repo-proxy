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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class DefaultCache implements Cache {

	protected final Map<String, CompletableFuture<ArtifactMetadata>> metaMap = new ConcurrentHashMap<>();
	protected final Map<String, CompletableFuture<ArtifactData>> artifactMap = new ConcurrentHashMap<>();

	protected final MavenRepositoryProxyInstance repo;

	public DefaultCache(final MavenRepositoryProxyInstance repo) {
		this.repo = repo;
	}

	@Override
	public synchronized CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		return this.metaMap.computeIfAbsent(ArtifactCoordinates.key(coords),
				k -> DefaultCache.this.repo.lookupMetadata(coords));
	}

	@Override
	public synchronized CompletableFuture<ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		final String artifactKey = ArtifactDataCoordinates.key(coords);
		// TODO consider adding some sort of lookup using the metadata!
		return this.artifactMap.computeIfAbsent(artifactKey, k -> this.repo.lookupArtifact(null, coords)
				.thenApply(v -> v));
	}

}

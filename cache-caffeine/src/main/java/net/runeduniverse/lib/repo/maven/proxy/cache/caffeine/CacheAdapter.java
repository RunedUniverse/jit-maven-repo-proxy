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

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class CacheAdapter implements Cache {

	protected final MavenRepositoryProxyInstance proxy;

	public CacheAdapter(final MavenRepositoryProxyInstance proxy) {
		this.proxy = proxy;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(ArtifactCoordinates coords) {
		// TODO implement cache
		return this.proxy.getMetadata(coords);
	}

	@Override
	public CompletableFuture<ArtifactData> getArtifact(ArtifactDataCoordinates coords) {
		// TODO implement cache
		return this.proxy.getArtifact(coords);
	}

}

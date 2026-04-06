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

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.SourceArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class RepositoryProxyInstanceProxy extends ARepositoryProxyInstance {

	final MavenRepositoryProxyInstance wrapped;

	public RepositoryProxyInstanceProxy(final MavenRepositoryProxyInstance wrapped) {
		this.wrapped = wrapped;
	}

	@Override
	public Cache cache() {
		return this.wrapped.cache();
	}

	@Override
	public String path() {
		return this.wrapped.path();
	}

	@Override
	public Map<String, RepositorySource> sources() {
		return this.wrapped.sources();
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		return this.wrapped.getMetadata(coords);
	}

	@Override
	public CompletableFuture<? extends ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		return this.wrapped.getArtifact(coords);
	}

	@Override
	public CompletableFuture<ArtifactMetadata> lookupMetadata(final ArtifactCoordinates coords) {
		return this.wrapped.lookupMetadata(coords);
	}

	@Override
	public CompletableFuture<SourceArtifactData> lookupArtifact(final String sourceKey,
			final ArtifactDataCoordinates coords) {
		return this.wrapped.lookupArtifact(sourceKey, coords);
	}
}

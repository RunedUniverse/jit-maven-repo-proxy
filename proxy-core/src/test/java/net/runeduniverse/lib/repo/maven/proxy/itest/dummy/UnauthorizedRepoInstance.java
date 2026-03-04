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
package net.runeduniverse.lib.repo.maven.proxy.itest.dummy;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.UnauthorizedArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.DefaultRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.SourceArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class UnauthorizedRepoInstance extends DefaultRepositoryProxyInstance {

	public UnauthorizedRepoInstance(String path, Function<MavenRepositoryProxyInstance, Cache> factory) {
		super(path, factory);
	}

	@Override
	public CompletableFuture<ArtifactMetadata> lookupMetadata(ArtifactCoordinates coords) {
		final CompletableFuture<ArtifactMetadata> future = new CompletableFuture<>();
		future.completeExceptionally(new UnauthorizedArtifactException());
		return future;
	}

	@Override
	public CompletableFuture<SourceArtifactData> lookupArtifact(String sourceKey, ArtifactDataCoordinates coords) {
		final CompletableFuture<SourceArtifactData> future = new CompletableFuture<>();
		future.completeExceptionally(new UnauthorizedArtifactException());
		return future;
	}

}

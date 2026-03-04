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
package net.runeduniverse.lib.repo.maven.server.itest.dummy;

import java.util.concurrent.CompletableFuture;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.ForbiddenArtifactException;
import net.runeduniverse.lib.repo.maven.server.itest.ARepositoryInstance;

public class ForbiddenRepoInstance extends ARepositoryInstance {

	public ForbiddenRepoInstance(final String path) {
		super(path);
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		final CompletableFuture<ArtifactMetadata> future = new CompletableFuture<>();
		future.completeExceptionally(new ForbiddenArtifactException());
		return future;
	}

	@Override
	public CompletableFuture<ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		final CompletableFuture<ArtifactData> future = new CompletableFuture<>();
		future.completeExceptionally(new ForbiddenArtifactException());
		return future;
	}
}

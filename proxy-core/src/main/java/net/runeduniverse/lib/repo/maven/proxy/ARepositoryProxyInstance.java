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

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.UnmodifiableArtifactData;
import net.runeduniverse.lib.repo.maven.data.UnmodifiableArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupArtifactListener;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupMetadataListener;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;

public abstract class ARepositoryProxyInstance implements MavenRepositoryProxyInstance {

	protected final Set<LookupMetadataListener> lookupMetadataListeners = new ConcurrentSkipListSet<>(
			Comparator.comparing(Object::hashCode));
	protected final Set<LookupArtifactListener> lookupArtifactListeners = new ConcurrentSkipListSet<>(
			Comparator.comparing(Object::hashCode));

	@Override
	public MavenRepositoryProxyInstance addListener(final LookupMetadataListener listener) {
		this.lookupMetadataListeners.add(listener);
		return this;
	}

	@Override
	public MavenRepositoryProxyInstance addListener(final LookupArtifactListener listener) {
		this.lookupArtifactListeners.add(listener);
		return this;
	}

	protected void preMetadataLookup(final ArtifactCoordinates coords) {
		this.lookupMetadataListeners.forEach(listener -> listener.preLookup(coords));
	}

	protected void postMetadataLookup(final ArtifactMetadata metadata, final Throwable throwable) {
		if (this.lookupMetadataListeners.isEmpty())
			return;
		final Future<ArtifactMetadata> future = asUnmodifiableFuture(metadata, throwable);
		this.lookupMetadataListeners.forEach(listener -> listener.postLookup(future));
	}

	protected void preArtifactLookup(final ArtifactDataCoordinates coords) {
		this.lookupArtifactListeners.forEach(listener -> listener.preLookup(coords));
	}

	protected void postArtifactLookup(final ArtifactData data, final Throwable throwable) {
		if (this.lookupArtifactListeners.isEmpty())
			return;
		final Future<ArtifactData> future = asUnmodifiableFuture(data, throwable);
		this.lookupArtifactListeners.forEach(listener -> listener.postLookup(future));
	}

	protected Future<ArtifactMetadata> asUnmodifiableFuture(final ArtifactMetadata metadata,
			final Throwable throwable) {
		final CompletableFuture<ArtifactMetadata> future = new CompletableFuture<>();
		if (throwable instanceof CancellationException)
			future.cancel(true);
		else if (throwable != null)
			future.completeExceptionally(throwable);
		else if (metadata != null)
			future.complete(UnmodifiableArtifactMetadata.wrap(metadata));
		else
			future.complete(null);
		return future;
	}

	protected Future<ArtifactData> asUnmodifiableFuture(final ArtifactData data, final Throwable throwable) {
		final CompletableFuture<ArtifactData> future = new CompletableFuture<>();
		if (throwable instanceof CancellationException)
			future.cancel(true);
		else if (throwable != null)
			future.completeExceptionally(throwable);
		else if (data != null)
			future.complete(UnmodifiableArtifactData.wrap(data));
		else
			future.complete(null);
		return future;
	}
}

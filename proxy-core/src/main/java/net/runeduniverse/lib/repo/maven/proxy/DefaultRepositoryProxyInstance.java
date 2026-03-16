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
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import java.util.function.Function;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.UnmodifiableArtifactData;
import net.runeduniverse.lib.repo.maven.data.UnmodifiableArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;
import net.runeduniverse.lib.repo.maven.error.UnvalidatableArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupArtifactListener;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupMetadataListener;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;
import net.runeduniverse.lib.repo.maven.proxy.api.SourceArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;
import net.runeduniverse.lib.repo.maven.proxy.data.DefaultAggregateArtifactMetadata;

public class DefaultRepositoryProxyInstance implements MavenRepositoryProxyInstance {

	protected final Map<String, RepositorySource> sources = new ConcurrentHashMap<>();
	protected final Set<LookupMetadataListener> lookupMetadataListeners = new ConcurrentSkipListSet<>();
	protected final Set<LookupArtifactListener> lookupArtifactListeners = new ConcurrentSkipListSet<>();

	protected final String path;
	protected final Cache cache;

	public DefaultRepositoryProxyInstance(final String path,
			final Function<MavenRepositoryProxyInstance, Cache> factory) {
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
	public MavenRepositoryProxyInstance addListener(final LookupMetadataListener listener) {
		this.lookupMetadataListeners.add(listener);
		return this;
	}

	@Override
	public MavenRepositoryProxyInstance addListener(LookupArtifactListener listener) {
		this.lookupArtifactListeners.add(listener);
		return this;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		return this.cache.getMetadata(coords);
	}

	@Override
	public CompletableFuture<? extends ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		return this.cache.getArtifact(coords);
	}

	@Override
	public CompletableFuture<ArtifactMetadata> lookupMetadata(final ArtifactCoordinates coords) {
		preMetadataLookup(coords);

		final DefaultAggregateArtifactMetadata aggMetadata = new DefaultAggregateArtifactMetadata(coords);

		RepositorySourceClient client;
		for (RepositorySource source : this.sources.values()) {
			if ((client = source.client()) == null)
				continue;
			aggMetadata.track(attachToMetadataLookup(client, coords, client.getMetadata(coords)));
		}

		final CompletableFuture<ArtifactMetadata> future = aggMetadata.asFuture();
		future.whenComplete(this::postMetadataLookup);
		return future;
	}

	protected void preMetadataLookup(final ArtifactCoordinates coords) {
		this.lookupMetadataListeners.forEach(listener -> listener.preLookup(coords));
	}

	protected CompletableFuture<ArtifactMetadata> attachToMetadataLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final CompletableFuture<ArtifactMetadata> upstream) {
		final CompletableFuture<ArtifactMetadata> future = new CompletableFuture<>();
		upstream.handle((v, t) -> {
			try {
				future.complete(DefaultRepositoryProxyInstance.this.interceptMetadataLookup(client, coords, v, t));
			} catch (Throwable e) {
				future.completeExceptionally(e);
			}
			return v;
		});
		return future;
	}

	protected ArtifactMetadata interceptMetadataLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final ArtifactMetadata metadata, Throwable throwable) throws Throwable {
		if (throwable instanceof CompletionException)
			throwable = throwable.getCause();
		if (throwable instanceof NotFoundArtifactException) {
			// not found is handled in aggregation
			throw throwable;
		} else if (throwable != null) {
			// log and re-throw
			throwable.printStackTrace(System.err);
			throw throwable;
		}

		// TODO do something with it!
		return metadata;
	}

	protected void postMetadataLookup(final ArtifactMetadata metadata, final Throwable throwable) {
		if (!this.lookupMetadataListeners.isEmpty())
			return;
		final Future<ArtifactMetadata> future = asUnmodifiableFuture(metadata, throwable);
		this.lookupMetadataListeners.forEach(listener -> listener.postLookup(future));
	}

	@Override
	public CompletableFuture<SourceArtifactData> lookupArtifact(final String sourceKey,
			final ArtifactDataCoordinates coords) {
		preArtifactLookup(coords);
		final CompletableFuture<SourceArtifactData> future;

		final RepositorySource defSource = sourceKey == null ? null : this.sources.get(sourceKey);
		RepositorySourceClient client;
		if (defSource != null && (client = defSource.client()) != null) {
			future = attachToArtifactLookup(client, coords, true, defSource.client()
					.getArtifact(coords)).thenApply(data -> SourceArtifactData.wrap(sourceKey, data));
			future.whenComplete(this::postArtifactLookup);
			return future;
		}

		// --- if there wasn't a specifc source requested ---

		future = new CompletableFuture<>();
		final List<CompletableFuture<Void>> upstream = new LinkedList<>();
		final Map<Integer, Queue<ArtifactException>> issues = new ConcurrentHashMap<>();

		for (RepositorySource source : this.sources.values()) {
			if ((client = source.client()) == null)
				continue;
			upstream.add(attachToArtifactLookup(client, coords, false, client.getArtifact(coords))
					.handle((data, throwable) -> {
						if (data != null)
							future.complete(SourceArtifactData.wrap(source.key(), data));
						if (throwable instanceof ArtifactException) {
							final ArtifactException ex = (ArtifactException) throwable;
							issues.computeIfAbsent(ex.priority(), p -> new ConcurrentLinkedQueue<>())
									.add(ex);
						}
						return null;
					}));
		}
		// cleanup pass -> result usually ignored
		CompletableFuture.allOf(upstream.toArray(new CompletableFuture<?>[0]))
				.thenRun(() -> {
					ArtifactException ex = DefaultRepositoryProxyInstance.this.processArtifactLookupErrors(issues);
					if (ex == null)
						future.complete(null);
					else
						future.completeExceptionally(ex);
				});

		future.whenComplete(this::postArtifactLookup);
		return future;
	}

	protected void preArtifactLookup(final ArtifactDataCoordinates coords) {
		this.lookupArtifactListeners.forEach(listener -> listener.preLookup(coords));
	}

	protected CompletableFuture<? extends ArtifactData> attachToArtifactLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final boolean exact,
			final CompletableFuture<? extends ArtifactData> upstream) {
		final CompletableFuture<ArtifactData> future = new CompletableFuture<>();
		upstream.handle((v, t) -> {
			try {
				future.complete(
						DefaultRepositoryProxyInstance.this.interceptArtifactLookup(client, coords, exact, v, t));
			} catch (Throwable e) {
				future.completeExceptionally(e);
			}
			return v;
		});
		return future;
	}

	protected ArtifactData interceptArtifactLookup(final RepositorySourceClient client,
			final ArtifactCoordinates coords, final boolean exact, final ArtifactData data, Throwable throwable)
			throws Throwable {

		if (throwable instanceof CompletionException)
			throwable = throwable.getCause();
		if (throwable != null) {
			// exactly that source was requested -> errors are deserved
			if (exact)
				throw throwable;
			// rethrow validation errors!
			if (throwable instanceof InvalidArtifactException || throwable instanceof UnvalidatableArtifactException)
				throw throwable;
			// bury it! -> if 1 fails all do!
			if (!(throwable instanceof ArtifactException))
				throwable.printStackTrace(System.err);
			return data;
		}

		// TODO do something with it!
		return data;
	}

	/**
	 * Selects the ArtifactException that get's thrown from the recorded map.
	 *
	 * @param issues
	 * @return the ArtifactException to be rethrown or null for an empty result
	 */
	protected ArtifactException processArtifactLookupErrors(final Map<Integer, Queue<ArtifactException>> issues) {
		for (Entry<Integer, Queue<ArtifactException>> entry : issues.entrySet()) {
			final Queue<ArtifactException> queue = entry.getValue();
			if (queue == null || queue.isEmpty())
				continue;
			for (ArtifactException ex : queue) {
				if (ex == null)
					continue;
				return ex;
			}
		}
		return null;
	}

	protected void postArtifactLookup(final ArtifactData data, final Throwable throwable) {
		if (!this.lookupArtifactListeners.isEmpty())
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

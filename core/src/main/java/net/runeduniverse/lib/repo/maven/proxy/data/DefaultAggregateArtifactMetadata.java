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
package net.runeduniverse.lib.repo.maven.proxy.data;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.lang3.StringUtils;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.AArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.ComparableVersion;
import net.runeduniverse.lib.repo.maven.proxy.api.AggregateArtifactMetadata;

public class DefaultAggregateArtifactMetadata extends AArtifactMetadata implements AggregateArtifactMetadata {

	protected final Set<CompletableFuture<Void>> trackedFutures = ConcurrentHashMap.newKeySet();

	protected final ReadWriteLock releaseLock = new ReentrantReadWriteLock();
	protected final ReadWriteLock latestLock = new ReentrantReadWriteLock();
	protected final ReadWriteLock lastUpdatedLock = new ReentrantReadWriteLock();

	public DefaultAggregateArtifactMetadata(final ArtifactCoordinates coords) {
		this(coords.getGroupId(), coords.getArtifactId());
	}

	public DefaultAggregateArtifactMetadata(final String groupId, final String artifactId) {
		super(new ConcurrentSkipListSet<>(), groupId, artifactId);
	}

	@Override
	public ComparableVersion getReleaseVersion2() {
		this.releaseLock.readLock()
				.lock();
		try {
			return this.release;
		} finally {
			this.releaseLock.readLock()
					.unlock();
		}
	}

	@Override
	public ComparableVersion getLatestVersion2() {
		this.latestLock.readLock()
				.lock();
		try {
			return this.latest;
		} finally {
			this.latestLock.readLock()
					.unlock();
		}
	}

	@Override
	public String getLastUpdated() {
		this.lastUpdatedLock.readLock()
				.lock();
		try {
			return this.lastUpdated;
		} finally {
			this.lastUpdatedLock.readLock()
					.unlock();
		}
	}

	@Override
	public void updateRelease(String release) {
		if ((release = StringUtils.trimToNull(release)) == null)
			return;
		updateRelease(new ComparableVersion(release));
	}

	@Override
	public void updateLatest(String latest) {
		if ((latest = StringUtils.trimToNull(latest)) == null)
			return;
		updateLatest(new ComparableVersion(latest));
	}

	public void updateRelease(final ComparableVersion release) {
		this.releaseLock.writeLock()
				.lock();
		try {
			if (0 < this.release.compareTo(release))
				this.release = release;
		} finally {
			this.releaseLock.writeLock()
					.unlock();
		}
	}

	public void updateLatest(final ComparableVersion latest) {
		this.latestLock.writeLock()
				.lock();
		try {
			if (0 < this.latest.compareTo(latest))
				this.latest = latest;
		} finally {
			this.latestLock.writeLock()
					.unlock();
		}
	}

	@Override
	public void updateLastUpdated(String lastUpdated) {
		if ((lastUpdated = StringUtils.trimToNull(lastUpdated)) == null)
			return;

		this.lastUpdatedLock.writeLock()
				.lock();
		try {
			if (Long.parseLong(this.lastUpdated) < Long.parseLong(lastUpdated)) {
				this.lastUpdated = lastUpdated;
			}
		} finally {
			this.lastUpdatedLock.writeLock()
					.unlock();
		}
	}

	@Override
	public void add(final ArtifactMetadata metadata) {
		// validate
		if (metadata == null)
			return;
		// track all versions
		for (String version : metadata.getVersions()) {
			addVersion(version);
		}
		// update headlines
		updateRelease(metadata.getReleaseVersion());
		updateLatest(metadata.getLatestVersion());
		updateLastUpdated(metadata.getLastUpdated());
	}

	protected void add2(final AArtifactMetadata metadata) {
		// validate
		if (metadata == null)
			return;
		// track all versions
		for (ComparableVersion version : metadata.getVersions2()) {
			addVersion(version);
		}
		// update headlines
		updateRelease(metadata.getReleaseVersion2());
		updateLatest(metadata.getLatestVersion2());
		updateLastUpdated(metadata.getLastUpdated());
	}

	public void track(final CompletableFuture<ArtifactMetadata> future) {
		this.trackedFutures.add(future.thenAccept(this::add));
	}

	protected <T> T removeCompletedFutures(final T obj) {
		for (Iterator<CompletableFuture<Void>> i = this.trackedFutures.iterator(); i.hasNext();) {
			final CompletableFuture<Void> future = i.next();
			if (future.isDone())
				i.remove();
		}
		return obj;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> asFuture() {
		return CompletableFuture.allOf(this.trackedFutures.toArray(new CompletableFuture<?>[0]))
				.thenApply(DefaultAggregateArtifactMetadata.this::removeCompletedFutures)
				.thenApply(v -> DefaultAggregateArtifactMetadata.this);
	}
}

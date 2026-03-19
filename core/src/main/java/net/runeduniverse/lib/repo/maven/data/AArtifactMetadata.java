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
package net.runeduniverse.lib.repo.maven.data;

import java.util.Iterator;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;

public abstract class AArtifactMetadata implements ArtifactMetadata {

	protected final NavigableSet<ComparableVersion> versions;
	protected final String groupId;
	protected final String artifactId;

	protected String lastUpdated = null;

	public AArtifactMetadata(final ArtifactCoordinates coords) {
		this(coords.getGroupId(), coords.getArtifactId());
	}

	public AArtifactMetadata(final String groupId, final String artifactId) {
		this(new TreeSet<>(), groupId, artifactId);
	}

	public AArtifactMetadata(final NavigableSet<ComparableVersion> versions, final String groupId,
			final String artifactId) {
		this.versions = versions;
		this.groupId = groupId;
		this.artifactId = artifactId;
	}

	@Override
	public String getGroupId() {
		return this.groupId;
	}

	@Override
	public String getArtifactId() {
		return this.artifactId;
	}

	@Override
	public String getReleaseVersion() {
		final ComparableVersion version = getReleaseVersion2();
		if (version == null)
			return null;
		return version.toString();
	}

	public ComparableVersion getReleaseVersion2() {
		return this.versions.descendingSet()
				.stream()
				.filter(this::isRelease2)
				.findFirst()
				.orElse(null);
	}

	public boolean isRelease2(final ComparableVersion version) {
		return ArtifactMetadata.super.isRelease(version.toString());
	}

	@Override
	public String getLatestVersion() {
		final ComparableVersion version = getLatestVersion2();
		if (version == null)
			return null;
		return version.toString();
	}

	public ComparableVersion getLatestVersion2() {
		if (this.versions.isEmpty())
			return null;
		return this.versions.last();
	}

	@Override
	public Set<String> getVersions() {
		return this.versions.stream()
				.map(ComparableVersion::toString)
				.collect(Collectors.toSet());
	}

	public Set<ComparableVersion> getVersions2() {
		return this.versions;
	}

	@Override
	public Iterator<String> getVersionIterator() {
		final Iterator<ComparableVersion> iterator = this.versions.iterator();
		return new Iterator<String>() {

			@Override
			public String next() {
				return iterator.next()
						.toString();
			}

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		};
	}

	@Override
	public String getLastUpdated() {
		return this.lastUpdated.toString();
	}

	public void setLastUpdated(final String lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	public void addVersion(final String version) {
		if (version == null)
			return;
		addVersion2(new ComparableVersion(version));
	}

	public void addVersion2(final ComparableVersion version) {
		if (version == null)
			return;
		this.versions.add(version);
	}

	@Override
	public void removeVersion(final String version) {
		if (version == null)
			return;
		removeVersion2(new ComparableVersion(version));
	}

	public void removeVersion2(final ComparableVersion version) {
		if (version == null)
			return;
		this.versions.remove(version);
	}

	public CompletableFuture<ArtifactMetadata> asFuture() {
		return CompletableFuture.completedFuture(this);
	}

	protected static <T> T throwNullAsNotFound(final T value) {
		if (value == null)
			throw new NotFoundArtifactException();
		return value;
	}

	protected static <T> T voidThrowable(final T value, final Throwable throwable) {
		// we don't care about exceptions -> result is optional
		return value;
	}
}

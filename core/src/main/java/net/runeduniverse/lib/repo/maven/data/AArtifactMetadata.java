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

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;

public abstract class AArtifactMetadata implements ArtifactMetadata {

	protected final SortedSet<ComparableVersion> versions;
	protected final String groupId;
	protected final String artifactId;

	protected ComparableVersion release = null;
	protected ComparableVersion latest = null;
	protected String lastUpdated = null;

	public AArtifactMetadata(final ArtifactCoordinates coords) {
		this(coords.getGroupId(), coords.getArtifactId());
	}

	public AArtifactMetadata(final String groupId, final String artifactId) {
		this(new TreeSet<>(), groupId, artifactId);
	}

	public AArtifactMetadata(final SortedSet<ComparableVersion> versions, final String groupId,
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
		return getReleaseVersion2().toString();
	}

	public ComparableVersion getReleaseVersion2() {
		return this.release;
	}

	@Override
	public String getLatestVersion() {
		return getLatestVersion2().toString();
	}

	public ComparableVersion getLatestVersion2() {
		return this.latest;
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
	public String getLastUpdated() {
		return this.lastUpdated.toString();
	}

	public void setRelease(final String release) {
		setRelease(new ComparableVersion(release));
	}

	public void setRelease(final ComparableVersion release) {
		this.release = release;
	}

	public void setLatest(final String latest) {
		setLatest(new ComparableVersion(latest));
	}

	public void setLatest(final ComparableVersion latest) {
		this.latest = latest;
	}

	public void setLastUpdated(final String lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	public void addVersion(final String version) {
		if (version == null)
			return;
		addVersion(new ComparableVersion(version));
	}

	public void addVersion(final ComparableVersion version) {
		if (version == null)
			return;
		this.versions.add(version);
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

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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;

public abstract class AArtifactMetadata implements ArtifactMetadata {

	protected final Set<String> versions;
	protected final String groupId;
	protected final String artifactId;

	protected String release = null;
	protected String latest = null;
	protected String lastUpdated = null;

	public AArtifactMetadata(final ArtifactCoordinates coords) {
		this(coords.getGroupId(), coords.getArtifactId());
	}

	public AArtifactMetadata(final String groupId, final String artifactId) {
		this(new HashSet<>(), groupId, artifactId);
	}

	public AArtifactMetadata(final Set<String> versions, final String groupId, final String artifactId) {
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
		return this.release;
	}

	@Override
	public String getLatestVersion() {
		return this.latest;
	}

	@Override
	public Set<String> getVersions() {
		return this.versions;
	}

	@Override
	public String getLastUpdated() {
		return this.lastUpdated;
	}

	public void setRelease(final String release) {
		this.release = release;
	}

	public void setLatest(final String latest) {
		this.latest = latest;
	}

	public void setLastUpdated(final String lastUpdated) {
		this.lastUpdated = lastUpdated;
	}

	public void addVersion(final String version) {
		if (version == null)
			return;
		this.versions.add(version);
	}

	public CompletableFuture<ArtifactMetadata> asFuture() throws Exception {
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

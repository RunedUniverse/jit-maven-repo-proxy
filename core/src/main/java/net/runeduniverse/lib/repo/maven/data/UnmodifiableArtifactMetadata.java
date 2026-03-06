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

import java.util.Collections;
import java.util.Set;

import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;

public class UnmodifiableArtifactMetadata extends ArtifactCoordinates.Data implements ArtifactMetadata {

	protected final Set<String> versions;
	protected final String releaseVersion;
	protected final String latestVersion;
	protected final String lastUpdated;

	public UnmodifiableArtifactMetadata(final String groupId, final String artifactId, final Set<String> versions,
			final String releaseVersion, final String latestVersion, final String lastUpdated) {
		super(groupId, artifactId);
		this.versions = versions;
		this.releaseVersion = releaseVersion;
		this.latestVersion = latestVersion;
		this.lastUpdated = lastUpdated;
	}

	@Override
	public String getReleaseVersion() {
		return this.releaseVersion;
	}

	@Override
	public String getLatestVersion() {
		return this.latestVersion;
	}

	@Override
	public Set<String> getVersions() {
		return this.versions;
	}

	@Override
	public String getLastUpdated() {
		return this.lastUpdated;
	}

	public static ArtifactMetadata wrap(final ArtifactMetadata metadata) {
		return new UnmodifiableArtifactMetadata(metadata.getGroupId(), metadata.getArtifactId(),
				Collections.unmodifiableSet(metadata.getVersions()), metadata.getReleaseVersion(),
				metadata.getLatestVersion(), metadata.getLastUpdated());
	}
}

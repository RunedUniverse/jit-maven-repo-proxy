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

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;

public class UnmodifiableArtifactData extends ArtifactDataCoordinates.Data implements ArtifactData {

	protected final Path artifactPath;
	protected final Path signaturePath;
	protected final Map<String, String> checksums;

	public UnmodifiableArtifactData(final String groupId, final String artifactId, final String version,
			final String classifier, final String extension, //
			final Path artifactPath, final Path signaturePath, final Map<String, String> checksums) {
		super(groupId, artifactId, version, classifier, extension);
		this.artifactPath = artifactPath;
		this.signaturePath = signaturePath;
		this.checksums = checksums;
	}

	@Override
	public Path getArtifactPath() {
		return this.artifactPath;
	}

	@Override
	public Path getSignaturePath() {
		return this.signaturePath;
	}

	@Override
	public Map<String, String> getChecksums() {
		return this.checksums;
	}

	public static ArtifactData wrap(final ArtifactData data) {
		return new UnmodifiableArtifactData(data.getGroupId(), data.getArtifactId(), data.getVersion(),
				data.getClassifier(), data.getExtension(), data.getArtifactPath(), data.getSignaturePath(),
				Collections.unmodifiableMap(data.getChecksums()));
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof ArtifactData && super.equals(obj)))
			return false;
		final ArtifactData other = (ArtifactData) obj;
		return Objects.equals(this.artifactPath, other.getArtifactPath())//
				&& Objects.equals(this.signaturePath, other.getSignaturePath())//
				&& this.checksums.equals(other.getChecksums());
	}
}

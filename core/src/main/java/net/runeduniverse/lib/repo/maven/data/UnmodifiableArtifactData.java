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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactPOM;

public class UnmodifiableArtifactData extends ArtifactDataCoordinates.Data implements ArtifactData {

	protected final Supplier<CompletableFuture<ArtifactPOM>> pomSupplier;
	protected final Path artifactPath;
	protected final Path signaturePath;
	protected final Map<String, String> checksums;

	public UnmodifiableArtifactData(final String groupId, final String artifactId, final String version,
			final String classifier, final String extension, final Supplier<CompletableFuture<ArtifactPOM>> pomSupplier,
			final Path artifactPath, final Path signaturePath, final Map<String, String> checksums) {
		super(groupId, artifactId, version, classifier, extension);
		this.pomSupplier = pomSupplier;
		this.artifactPath = artifactPath;
		this.signaturePath = signaturePath;
		this.checksums = checksums;
	}

	@Override
	public CompletableFuture<ArtifactPOM> getPOM() {
		return this.pomSupplier.get();
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
				data.getClassifier(), data.getExtension(), data::getPOM, data.getArtifactPath(),
				data.getSignaturePath(), Collections.unmodifiableMap(data.getChecksums()));
	}
}

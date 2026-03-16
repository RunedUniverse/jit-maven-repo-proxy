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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;

public abstract class AArtifactData implements ArtifactData {

	protected final Map<String, String> checksums = new ConcurrentHashMap<>();

	protected final String groupId;
	protected final String artifactId;
	protected final String version;
	protected final String classifier;
	protected final String extension;

	public AArtifactData(final String groupId, final String artifactId, final String version, //
			final String classifier, final String extension) {

		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.classifier = classifier;
		this.extension = extension;
	}

	public AArtifactData(final ArtifactDataCoordinates coords) {
		this(coords.getGroupId(), coords.getArtifactId(), coords.getVersion(), //
				coords.getClassifier(), coords.getExtension() //
		);
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
	public String getVersion() {
		return this.version;
	}

	@Override
	public String getClassifier() {
		return this.classifier;
	}

	@Override
	public String getExtension() {
		return this.extension;
	}

	@Override
	public Map<String, String> getChecksums() {
		return this.checksums;
	}

	public CompletableFuture<? extends ArtifactData> asFuture() throws Exception {
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

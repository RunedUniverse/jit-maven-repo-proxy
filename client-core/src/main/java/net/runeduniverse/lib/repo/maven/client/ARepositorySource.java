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
package net.runeduniverse.lib.repo.maven.client;

import java.net.URI;
import java.nio.file.Path;
import java.util.Deque;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;

public abstract class ARepositorySource implements RepositorySource {

	protected final Deque<MetadataValidator> metadataValidators;
	protected final Deque<ArtifactValidator> artifactValidators;
	protected final String key;
	protected final URI uri;
	protected final Path repoPath;
	protected final int maxRedirects;
	protected final int maxRetries;

	public ARepositorySource(//
			final String key, final URI uri, final Path repoPath, //
			final int maxRedirects, final int maxRetries, //
			final Deque<MetadataValidator> metadataValidators, final Deque<ArtifactValidator> artifactValidators) {
		this.key = key;
		this.uri = uri;
		this.repoPath = repoPath;
		this.maxRedirects = maxRedirects;
		this.maxRetries = maxRetries;
		this.metadataValidators = metadataValidators;
		this.artifactValidators = artifactValidators;
	}

	@Override
	public String key() {
		return this.key;
	}

	@Override
	public URI getRepoUri() {
		return this.uri;
	}

	@Override
	public Path getLocalRepoPath() {
		return this.repoPath;
	}

	@Override
	public ARepositorySource addFirstValidator(final MetadataValidator validator) {
		if (validator != null)
			this.metadataValidators.addFirst(validator);
		return this;
	}

	@Override
	public ARepositorySource addFirstValidator(final ArtifactValidator validator) {
		if (validator != null)
			this.artifactValidators.addFirst(validator);
		return this;
	}

	@Override
	public ARepositorySource addLastValidator(final MetadataValidator validator) {
		if (validator != null)
			this.metadataValidators.addLast(validator);
		return this;
	}

	@Override
	public ARepositorySource addLastValidator(final ArtifactValidator validator) {
		if (validator != null)
			this.artifactValidators.addLast(validator);
		return this;
	}

	public MetadataValidator getMetadataValidator() {
		if (this.metadataValidators.isEmpty())
			return null;
		return new MetadataValidator() {
			@Override
			public boolean validate(ArtifactMetadata metadata) throws InvalidArtifactException {
				boolean processed = false;
				for (MetadataValidator validator : ARepositorySource.this.metadataValidators)
					processed = processed || validator.validate(metadata);
				return processed;
			}
		};
	}

	public ArtifactValidator getArtifactValidator() {
		if (this.artifactValidators.isEmpty())
			return null;
		return new ArtifactValidator() {
			@Override
			public boolean validate(ArtifactData data) throws InvalidArtifactException {
				boolean processed = false;
				for (ArtifactValidator validator : ARepositorySource.this.artifactValidators) {
					processed = processed || validator.validate(data);
				}
				return processed;
			}
		};
	}
}

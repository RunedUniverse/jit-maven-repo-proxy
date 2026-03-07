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
package net.runeduniverse.lib.repo.maven.client.http;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.client.XmlArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.AArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.TextProcessor;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.UnvalidatableArtifactException;

public class HttpArtifactMetadata extends XmlArtifactMetadata {

	protected final URI repoUri;
	protected final int maxRedirects;

	protected MetadataValidator validator = null;

	// even when overridden - only access via Getter
	private HttpDataRequest metadataRequest = null;

	public HttpArtifactMetadata(final URI repoUri, final int maxRedirects, //
			final ArtifactCoordinates coords) {
		super(coords);

		this.repoUri = repoUri;
		this.maxRedirects = maxRedirects;
	}

	public HttpArtifactMetadata(final URI repoUri, final int maxRedirects, //
			final String groupId, final String artifactId) {
		super(groupId, artifactId);

		this.repoUri = repoUri;
		this.maxRedirects = maxRedirects;
	}

	public void setValidator(final MetadataValidator validator) {
		this.validator = validator;
	}

	public List<HttpDataRequest> getDataRequests() {
		final List<HttpDataRequest> dataRequests = new LinkedList<>();
		dataRequests.add(getMetadataRequest(this.repoUri, this.maxRedirects));
		return dataRequests;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> asFuture() {
		final List<HttpDataRequest> dataRequests = getDataRequests();
		final CompletableFuture<?>[] futures = new CompletableFuture<?>[dataRequests.size()];
		for (int i = 0; i < dataRequests.size(); i++) {
			futures[i] = dataRequests.get(i)
					.future();
		}
		return CompletableFuture.allOf(futures)
				.thenApply(v -> HttpArtifactMetadata.this);
	}

	public synchronized HttpDataRequest getMetadataRequest(final URI repoUri, final int maxRedirects) {
		if (this.metadataRequest != null)
			return this.metadataRequest;

		// --- Build Metadata Request ---
		final TextProcessor textProcessor = new TextProcessor();
		final HttpDataRequest metadataRequest = new HttpDataRequest(//
				this.repoUri.resolve(//
						String.join("/", this.groupId.replace('.', '/'), this.artifactId, "maven-metadata.xml")//
				), textProcessor, () -> {
					return textProcessor.future()
							.thenApply(AArtifactMetadata::throwNullAsNotFound)
							.thenApply(HttpArtifactMetadata.this::parseXmlText);
				}, this.maxRedirects);

		return this.metadataRequest = metadataRequest;
	}

	protected void validateArtifact() throws InvalidArtifactException {
		// if no validator is provided, all artifacts are deemed valid!
		if (this.validator == null)
			return;
		if (!this.validator.validate(this))
			throw new UnvalidatableArtifactException(UnvalidatableArtifactException.MSG_VALIDATOR_MISSMATCH);
	}
}

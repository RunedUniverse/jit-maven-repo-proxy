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
package net.runeduniverse.lib.repo.maven.http.data;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.codec.binary.Hex;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ChecksumType;
import net.runeduniverse.lib.repo.maven.data.AArtifactData;
import net.runeduniverse.lib.repo.maven.data.AContentProcessor;
import net.runeduniverse.lib.repo.maven.data.FileProcessor;
import net.runeduniverse.lib.repo.maven.data.TextProcessor;
import net.runeduniverse.lib.repo.maven.error.InvalidChecksumArtifactException;

public class HttpArtifactData extends AArtifactData implements ArtifactData {

	protected final Map<String, String> checksums = new ConcurrentHashMap<>();

	protected final Path repoPath;
	protected final URI repoUri;
	protected final int maxRedirects;

	// even when overridden - only access via Getter
	private String gavPath = null;
	private String artifactName = null;
	private Path artifactPath = null;
	private Path signaturePath = null;
	private HttpDataRequest artifactRequest = null;

	public HttpArtifactData(final Path repoPath, final URI repoUri, final int maxRedirects, //
			final String groupId, final String artifactId, final String version, //
			final String classifier, final String extension) {
		super(groupId, artifactId, version, classifier, extension);

		this.repoPath = repoPath;
		this.repoUri = repoUri;
		this.maxRedirects = maxRedirects;
	}

	public HttpArtifactData(final Path repoPath, final URI repoUri, final int maxRedirects,
			final ArtifactDataCoordinates coords) {
		super(coords);

		this.repoPath = repoPath;
		this.repoUri = repoUri;
		this.maxRedirects = maxRedirects;
	}

	public String getGAVPath() {
		if (this.gavPath == null) {
			// net/runeduniverse/lib/utils/utils-common/1.0.0/
			String.join("/", this.groupId.replace('.', '/'), this.artifactId, this.version);
		}
		return this.gavPath;
	}

	public String getArtifactName() {
		if (this.artifactName == null) {
			// utils-common-1.0.0-javadoc.jar.asc
			final StringBuilder builder = new StringBuilder()//
					.append(this.artifactId)
					.append('-')
					.append(this.version);
			if (this.classifier != null) {
				builder.append('-')
						.append(this.classifier);
			}
			builder.append('.')
					.append(this.extension);
			this.artifactName = builder.toString();
		}
		return this.artifactName;
	}

	@Override
	public Path getArtifactPath() {
		if (this.artifactPath == null) {
			this.artifactPath = this.repoPath.resolve(getGAVPath() + '/' + getArtifactName());
		}
		return this.artifactPath;
	}

	@Override
	public Path getSignaturePath() {
		if (this.signaturePath == null) {
			this.signaturePath = this.repoPath.resolve(getGAVPath() + '/' + getArtifactName() + ".asc");
		}
		return this.signaturePath;
	}

	public List<HttpDataRequest> getDataRequests() throws IOException {
		final List<HttpDataRequest> dataRequests = new LinkedList<>();
		dataRequests.add(getArtifactRequest(this.repoUri, this.maxRedirects));
		return dataRequests;
	}

	public CompletableFuture<ArtifactData> asFuture() throws IOException {
		final List<HttpDataRequest> dataRequests = getDataRequests();
		final CompletableFuture<?>[] futures = new CompletableFuture<?>[dataRequests.size()];
		for (int i = 0; i < dataRequests.size(); i++) {
			futures[i] = dataRequests.get(i)
					.future();
		}
		return CompletableFuture.allOf(futures)
				.thenApply(v -> HttpArtifactData.this);
	}

	public synchronized HttpDataRequest getArtifactRequest(final URI repoUri, final int maxRedirects)
			throws IOException {
		if (this.artifactRequest != null)
			return this.artifactRequest;

		// --- Prepare for building the Future Tree
		// --> build in reverse order!
		// --> execute in order!
		// --> on error -> cancel subtree!
		final Map<String, AContentProcessor<?>> subProcessorMap = new HashMap<>();
		final List<CompletableFuture<?>> futures = new LinkedList<>();
		final Map<String, MessageDigest> localChecksums = ChecksumType.tryFill(new ConcurrentHashMap<>(), null);
		final FileProcessor fileProcessor = new FileProcessor(getArtifactPath(), localChecksums);
		final CompletableFuture<?> fileFuture = fileProcessor.future();
		futures.add(fileFuture.whenComplete((v, t) -> {
			// file future is dominant! => kill all sub-processors others!
			if (t != null)
				subProcessorMap.values()
						.forEach(p -> p.cancel(true));
		}));

		// --- Build Checksum Requests ---
		for (ChecksumType type : ChecksumType.allEntries()) {
			final String ext = type.extension();
			final TextProcessor textProcessor = new TextProcessor(true);
			futures.add(textProcessor.future()
					.handle((value, ignoredEx) -> {
						// we don't care about checksum exceptions -> they are basically optional
						HttpArtifactData.this.checksums.put(ext, value);
						return value;
					}));
			subProcessorMap.put(ext, textProcessor);
		}

		// --- Build Signature Request ---
		final FileProcessor sigFileProcessor = new FileProcessor(getSignaturePath(), Collections.emptyMap());
		subProcessorMap.put("asc", sigFileProcessor);
		futures.add(sigFileProcessor.future()
				.handle(HttpArtifactData::voidThrowable));

		// --- Build Artifact Request ---
		final HttpDataRequest artifactRequest = new HttpDataRequest(//
				repoUri.resolve(getGAVPath() + '/' + getArtifactName()), fileProcessor, () -> {
					return CompletableFuture.allOf(//
							futures.toArray(new CompletableFuture<?>[futures.size()]))
							.thenApply(v -> {
								// --- Verify Artifact - Data
								// verify checksums / update if missing
								for (Entry<String, MessageDigest> entry : localChecksums.entrySet()) {
									final String ext = entry.getKey();
									final String localChecksum = Hex.encodeHexString(entry.getValue()
											.digest(), true);
									final String refChecksum = this.checksums.get(ext);

									if (refChecksum == null) {
										this.checksums.put(ext, localChecksum);
										continue;
									}
									if (!refChecksum.trim()
											.equals(localChecksum)) {
										// somthing is wrong !!!
										throw new InvalidChecksumArtifactException(ext, localChecksum, refChecksum);
									}
								}
								// verify signature
								// TODO validate PGP Signature
								return null;
							});
				}, maxRedirects);
		artifactRequest.subProcessorMap()
				.putAll(subProcessorMap);
		return this.artifactRequest = artifactRequest;
	}
}

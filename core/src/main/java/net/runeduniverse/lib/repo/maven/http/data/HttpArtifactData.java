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
import net.runeduniverse.lib.repo.maven.error.InvalidChecksumArtifactException;

public class HttpArtifactData implements ArtifactData {

	protected final Map<String, String> checksums = new ConcurrentHashMap<>();

	protected final Path repoPath;

	protected final String groupId;
	protected final String artifactId;
	protected final String version;
	protected final String classifier;
	protected final String extension;

	protected String gavPath = null;
	protected String artifactName = null;
	protected Path artifactPath = null;
	protected Path signaturePath = null;
	protected HttpDataRequest artifactRequest = null;
	protected HttpDataRequest signatureRequest = null;

	public HttpArtifactData(final Path repoPath, //
			final String groupId, final String artifactId, final String version, //
			final String classifier, final String extension) {
		this.repoPath = repoPath;
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.classifier = classifier;
		this.extension = extension;
		ChecksumType.fill(this.checksums, t -> null);
	}

	public HttpArtifactData(final Path repoPath, final ArtifactDataCoordinates coords) {
		this(repoPath, //
				coords.getGroupId(), coords.getArtifactId(), coords.getVersion(), //
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
			this.artifactPath = this.repoPath.resolve(getGAVPath() + '.' + getArtifactName());
		}
		return this.artifactPath;
	}

	@Override
	public Path getSignaturePath() {
		if (this.signaturePath == null) {
			this.signaturePath = this.repoPath.resolve(getGAVPath() + '.' + getArtifactName() + ".asc");
		}
		return this.signaturePath;
	}

	@Override
	public Map<String, String> getChecksums() {
		return this.checksums;
	}

	public synchronized HttpDataRequest getArtifactRequest(final URI repoUri, final int maxRedirects)
			throws IOException {
		if (this.artifactRequest != null)
			return this.artifactRequest;

		final List<CompletableFuture<?>> rawFutures = new LinkedList<>();
		final List<CompletableFuture<?>> futures = new LinkedList<>();
		final Map<String, MessageDigest> localChecksums = ChecksumType.tryFill(new ConcurrentHashMap<>(), null);
		final FileProcessor fileProcessor = new FileProcessor(getArtifactPath(), localChecksums);
		final CompletableFuture<?> fileFuture = fileProcessor.future();
		futures.add(fileFuture.whenComplete((v, t) -> {
			// file future is dominant! => kill the others!
			if (t != null)
				rawFutures.forEach(f -> f.cancel(true));
		}));

		final Map<String, AContentProcessor<?>> checksumMap = new HashMap<>();
		for (ChecksumType type : ChecksumType.allEntries()) {
			final String ext = type.extension();
			final TextProcessor textProcessor = new TextProcessor(true);
			final CompletableFuture<String> textFuture = textProcessor.future();
			rawFutures.add(textFuture);
			futures.add(textFuture.handle((value, ignoredEx) -> {
				// we don't care about checksum exceptions -> they are basically optional
				HttpArtifactData.this.checksums.put(ext, value);
				return value;
			}));
			checksumMap.put(ext, textProcessor);
		}

		final HttpDataRequest request = new HttpDataRequest(//
				repoUri.resolve(getGAVPath() + '.' + getArtifactName()), fileProcessor, () -> {
					return CompletableFuture.allOf(//
							futures.toArray(new CompletableFuture<?>[futures.size()]))
							.thenApply(v -> {
								for (Entry<String, MessageDigest> entry : localChecksums.entrySet()) {
									final String ext = entry.getKey();
									final String localChecksum = Hex.encodeHexString(entry.getValue()
											.digest(), true);
									final String refChecksum = this.checksums.get(ext);

									if (refChecksum == null) {
										this.checksums.put(ext, localChecksum);
										continue;
									}
									if (!refChecksum.equals(localChecksum)) {
										// somthing is wrong !!!
										throw new InvalidChecksumArtifactException(ext, localChecksum, refChecksum);
									}
								}
								return null;
							});
				}, maxRedirects);
		request.checksumMap()
				.putAll(checksumMap);
		return this.artifactRequest = request;
	}

	public synchronized HttpDataRequest getSignatureRequest(final URI repoUri, final int maxRedirects)
			throws IOException {
		if (this.signatureRequest != null)
			return this.signatureRequest;

		final HttpDataRequest artifactRequest = getArtifactRequest(repoUri, maxRedirects);

		final FileProcessor fileProcessor = new FileProcessor(getSignaturePath(), Collections.emptyMap());
		final CompletableFuture<?> fileFuture = fileProcessor.future();

		final HttpDataRequest request = new HttpDataRequest(//
				repoUri.resolve(getGAVPath() + '.' + getArtifactName() + ".asc"), fileProcessor, () -> {
					return CompletableFuture.allOf(artifactRequest.future(), fileFuture)
							.thenApply(v -> {
								// TODO validate PGP Signature
								return null;
							});
				}, maxRedirects);
		return this.signatureRequest = request;
	}
}

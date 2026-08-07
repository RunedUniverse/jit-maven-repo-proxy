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
package net.runeduniverse.lib.repo.maven.client.http.itest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.client.http.HttpSource;
import net.runeduniverse.lib.repo.maven.client.itest.ASourceTest;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.validation.pgp.PGPArtifactSignatureValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

import static org.junit.jupiter.api.Assertions.*;

public class HttpSourceTest extends ASourceTest {

	@Override
	protected RepositorySource configure() {
		// NOTE: we obviously test against our mirror and not maven-central
		// but of course it was tested against maven-central before moving to the
		// mirror!
		return new HttpSource("maven-central", URI.create(systemProperty_repo_mvnCentral_urlHttp()), repoPath(), 3, 5)
				.addFirstValidator(new PGPArtifactSignatureValidator(PublicKeyIndex.createDefaultKeyIndex()));
	}

	@Test
	@Tag("live")
	public void downloadMetadata() throws Exception {
		final ArtifactCoordinates coords = ArtifactCoordinates.build(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension");

		final CompletableFuture<ArtifactMetadata> future = client().getMetadata(coords);

		final ArtifactMetadata data = future.get(timeout(), TimeUnit.SECONDS);

		// Metadata
		final Set<String> versions = data.getVersions();
		final String latest = data.getLatestVersion();
		final String release = data.getReleaseVersion();

		System.out.println("latest: " + latest);
		System.out.println("release: " + release);
		System.out.println("updated: " + data.getLastUpdated());
		System.out.println("versions: " + String.join(", ", versions));

		assertTrue(latest == null || versions.contains(latest), //
				"versions-set must include the latest-version value");
		assertTrue(release == null || versions.contains(release), //
				"versions-set must include the release-version value");
	}

	@Test
	@Tag("live")
	public void downloadMetaNotFound() throws Exception {
		final ArtifactCoordinates coords = ArtifactCoordinates.build(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extensionX");

		final CompletableFuture<ArtifactMetadata> future = client().getMetadata(coords);

		Throwable t = null;
		try {
			future.get(timeout(), TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			t = e.getCause();
		}

		try {
			assertInstanceOf(NotFoundArtifactException.class, t,
					"missing metadata should throw NotFoundArtifactException");
		} catch (Throwable x) {
			print("[ERR] " + x.getMessage());
			throw x;
		}
	}

	@Test
	@Tag("live")
	public void downloadPom() throws Exception {
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.build(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", systemProperty_r4m_version(), null, "pom");

		final CompletableFuture<? extends ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get(timeout(), TimeUnit.SECONDS);

		// Artifact
		final Path artifact = data.getArtifactPath();
		assertFileExists(artifact, "Artifact");

		// Signature
		final Path signature = data.getSignaturePath();
		assertFileExists(signature, "Signature");
	}

	@Test
	@Tag("live")
	public void downloadWithClassifier() throws Exception {
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.build(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", systemProperty_r4m_version(), "sources",
				"jar");

		final CompletableFuture<? extends ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get(timeout(), TimeUnit.SECONDS);

		// Artifact
		final Path artifact = data.getArtifactPath();
		assertFileExists(artifact, "Artifact");

		// Signature
		final Path signature = data.getSignaturePath();
		assertFileExists(signature, "Signature");
	}

	@Test
	@Tag("live")
	public void downloadWithoutClassifier() throws Exception {
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.build(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", systemProperty_r4m_version(), null, "jar");

		final CompletableFuture<? extends ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get(timeout(), TimeUnit.SECONDS);

		// Artifact
		final Path artifact = data.getArtifactPath();
		assertFileExists(artifact, "Artifact");

		// Signature
		final Path signature = data.getSignaturePath();
		assertFileExists(signature, "Signature");
	}

	@Test
	@Tag("live")
	public void downloadNotFound() throws Exception {
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.build(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", systemProperty_r4m_version(), null, "pomX");

		final CompletableFuture<? extends ArtifactData> future = client().getArtifact(coords);

		Throwable t = null;
		try {
			future.get(timeout(), TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			t = e.getCause();
		}

		assertInstanceOf(NotFoundArtifactException.class, t, "missing artifact should throw NotFoundArtifactException");
	}

	protected void assertFileExists(final Path path, final String name) throws Exception {
		print(name + ": Path = " + path.toString());

		assertTrue(Files.exists(path), name + ": File should exist");
		long size = Files.size(path);
		assertTrue(size > 0, name + ": File should not be empty, size=" + size);
	}

	protected void assertFileNotExists(final Path path, final String name) throws Exception {
		print(name + ": Path = " + path.toString());

		assertFalse(Files.exists(path), name + ": File should not exist");
	}

}

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
package net.runeduniverse.lib.repo.maven.proxy.source.http.itest;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.source.http.HttpSource;
import net.runeduniverse.lib.repo.maven.proxy.source.itest.ASourceTest;

import static org.junit.jupiter.api.Assertions.*;

public class HttpSourceTest extends ASourceTest {

	@Override
	protected RepositorySource configure() {
		// NOTE: we obviously test against our mirror and not maven-central
		// but of course it was tested against maven-central before moving to the
		// mirror!
		return new HttpSource("maven-central", URI.create("https://nexus.runeduniverse.net/repository/maven-central/"),
				repoPath(), 3, 5);
	}

	@Test
	@Tag("live")
	public void downloadPom() throws Exception {
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.request(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", "1.1.0", null, "pom");

		final CompletableFuture<ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get();

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
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.request(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", "1.1.0", "sources", "jar");

		final CompletableFuture<ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get();

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
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.request(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", "1.1.0", null, "jar");

		final CompletableFuture<ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get();

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
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.request(//
				"net.runeduniverse.tools.maven.r4m", "r4m-maven-extension", "1.1.0", null, "pomX");

		final CompletableFuture<ArtifactData> future = client().getArtifact(coords);

		final ArtifactData data = future.get();

		// Artifact
		final Path artifact = data.getArtifactPath();
		assertFileNotExists(artifact, "Artifact");

		// Signature
		final Path signature = data.getSignaturePath();
		assertFileNotExists(signature, "Signature");
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

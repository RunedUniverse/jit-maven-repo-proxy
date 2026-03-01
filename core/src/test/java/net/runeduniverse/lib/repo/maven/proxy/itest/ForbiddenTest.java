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
package net.runeduniverse.lib.repo.maven.proxy.itest;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.error.ForbiddenArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.proxy.itest.dummy.ForbiddenRepoInstance;
import net.runeduniverse.lib.repo.maven.proxy.source.http.HttpSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ForbiddenTest extends ARepoTest {

	@Override
	protected ProxyServerBuilder configureServer(ProxyServerBuilder builder) {
		return builder.instance("maven-central", instance -> {
			// ensure no artifact is ever found
			instance.setInstanceFacory(ForbiddenRepoInstance::new);
		});
	}

	@Override
	protected RepositorySource configureClient() {
		return new HttpSource("proxy", URI.create(String.format("http://%s:%d/maven-central/",
				this.socketAddress.getHostString(), this.socketAddress.getPort())), clientPath(), 3, 5);
	}

	@Test
	@Tag("live")
	public void forbiddenMetadata() throws InterruptedException, TimeoutException {
		final ArtifactCoordinates coords = ArtifactCoordinates.request(//
				"net.runeduniverse", "missing-artifact");

		final CompletableFuture<ArtifactMetadata> future = client().getMetadata(coords);

		Throwable t = null;
		try {
			future.get(timeout(), TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			t = e.getCause();
		}

		try {
			assertInstanceOf(ForbiddenArtifactException.class, t,
					"missing metadata should throw ForbiddenArtifactException");
		} catch (Error e) {
			t.printStackTrace();
			throw e;
		}
	}

	@Test
	@Tag("live")
	public void forbiddenArtifact() throws InterruptedException, TimeoutException {
		final ArtifactDataCoordinates coords = ArtifactDataCoordinates.request(//
				"net.runeduniverse", "missing-artifact", "1", null, "jar");

		final CompletableFuture<ArtifactData> future = client().getArtifact(coords);

		Throwable t = null;
		try {
			future.get(timeout(), TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			t = e.getCause();
		}

		try {
			assertInstanceOf(ForbiddenArtifactException.class, t,
					"missing artifact should throw ForbiddenArtifactException");
		} catch (Error e) {
			t.printStackTrace();
			throw e;
		} finally {
			shutdown();
		}
	}

}

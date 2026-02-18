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
package net.runeduniverse.lib.repo.maven.proxy.source.http;

import java.util.concurrent.CompletableFuture;

import net.runeduniverse.lib.repo.maven.proxy.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public class HttpSourceClient implements RepositorySourceClient {

	protected final HttpSource source;

	protected HttpSourceClient(final HttpSource source) {
		this.source = source;
	}

	public RepositorySource getSource() {
		return source;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> discoverMetadata(String groupId, String artifactId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<ArtifactData> downloadArtifact(String groupId, String artifactId, String classifier,
			String extension, String version) {
		// TODO Auto-generated method stub
		return null;
	}

}

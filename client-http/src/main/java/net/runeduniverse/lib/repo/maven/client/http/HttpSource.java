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
import java.nio.file.Path;
import java.util.Deque;
import java.util.LinkedList;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.api.RepoCredentials;
import net.runeduniverse.lib.repo.maven.client.ARepositorySource;
import net.runeduniverse.lib.repo.maven.client.http.auth.AuthStateProvider;
import net.runeduniverse.lib.repo.maven.client.http.auth.DefaultAuthStateProvider;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public class HttpSource extends ARepositorySource implements RepositorySource {

	private RepositorySourceClient client = null;
	private AuthStateProvider repoAuthStateProvider = null;
	private AuthStateProvider proxyAuthStateProvider = null;

	public HttpSource(final String key, final URI uri, final Path repoPath, final int maxRedirects,
			final int maxRetries) {
		super(key, uri, repoPath, maxRedirects, maxRetries, new LinkedList<>(), new LinkedList<>());
	}

	public HttpSource(final String key, final URI uri, final Path repoPath, final int maxRedirects,
			final int maxRetries, final Deque<MetadataValidator> metadataValidators,
			final Deque<ArtifactValidator> artifactValidators) {
		super(key, uri, repoPath, maxRedirects, maxRetries, metadataValidators, artifactValidators);
	}

	@Override
	public synchronized RepositorySourceClient client() {
		if (this.client != null)
			return this.client;
		return this.client = new HttpSourceClient(this, this.maxRedirects, this.maxRetries)
				.setValidator(getMetadataValidator())
				.setValidator(getArtifactValidator());
	}

	public AuthStateProvider getRepoAuthStateProvider() {
		if (this.repoAuthStateProvider != null)
			return this.repoAuthStateProvider;
		return this.repoAuthStateProvider = new DefaultAuthStateProvider(getRepoCredentials());
	}

	public AuthStateProvider getProxyAuthStateProvider() {
		if (this.proxyAuthStateProvider != null)
			return this.proxyAuthStateProvider;
		return this.proxyAuthStateProvider = new DefaultAuthStateProvider(getProxyCredentials());
	}

	@Override
	public HttpSource setRepoCredentials(final RepoCredentials credentials) {
		super.setRepoCredentials(credentials);
		return this;
	}

	@Override
	public HttpSource setProxyCredentials(final RepoCredentials credentials) {
		super.setProxyCredentials(credentials);
		return this;
	}
}

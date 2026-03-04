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

import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public class HttpSource implements RepositorySource {

	protected final String key;
	protected final URI uri;
	protected final Path repoPath;
	protected final int maxRedirects;
	protected final int maxRetries;

	public HttpSource(final String key, final URI uri, final Path repoPath, final int maxRedirects,
			final int maxRetries) {
		this.key = key;
		this.uri = uri;
		this.repoPath = repoPath;
		this.maxRedirects = maxRedirects;
		this.maxRetries = maxRetries;
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
	public RepositorySourceClient client() {
		return new HttpSourceClient(this, this.maxRedirects, this.maxRetries);
	}

}

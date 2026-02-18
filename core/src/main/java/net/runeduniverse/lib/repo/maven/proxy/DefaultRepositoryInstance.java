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
package net.runeduniverse.lib.repo.maven.proxy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class DefaultRepositoryInstance implements MavenRepositoryInstance {

	protected final Map<String, RepositorySourceClient> sources = new LinkedHashMap<>();

	protected final String path;
	protected final Cache cache;

	public DefaultRepositoryInstance(final String path, final Cache cache) {
		this.path = path;
		this.cache = cache;
	}

	public String getPath() {
		return this.path;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(String groupId, String artifactId) {
		// TODO handle metadata download
		return null;
	}

	@Override
	public CompletableFuture<ArtifactData> getArtifact(String groupId, String artifactId, String classifier,
			String extension, String version) {
		// TODO handle artifact download
		return null;
	}

}

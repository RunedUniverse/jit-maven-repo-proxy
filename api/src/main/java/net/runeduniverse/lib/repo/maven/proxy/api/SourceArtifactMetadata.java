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
package net.runeduniverse.lib.repo.maven.proxy.api;

import java.util.Set;

import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;

public interface SourceArtifactMetadata extends ArtifactMetadata {

	public RepositorySource source();

	public static SourceArtifactMetadata wrap(final RepositorySource source, final ArtifactMetadata metadata) {
		return new WrappedAdapter(source, metadata);
	}

	public static class WrappedAdapter implements SourceArtifactMetadata {

		protected final RepositorySource source;
		protected final ArtifactMetadata metadata;

		public WrappedAdapter(final RepositorySource source, final ArtifactMetadata metadata) {
			this.source = source;
			this.metadata = metadata;
		}

		@Override
		public RepositorySource source() {
			return this.source;
		}

		public ArtifactMetadata wrappedData() {
			return this.metadata;
		}

		@Override
		public String getGroupId() {
			return this.metadata.getGroupId();
		}

		@Override
		public String getArtifactId() {
			return this.metadata.getArtifactId();
		}

		@Override
		public String getReleaseVersion() {
			return this.metadata.getReleaseVersion();
		}

		@Override
		public String getLatestVersion() {
			return this.metadata.getLatestVersion();
		}

		@Override
		public Set<String> getVersions() {
			return this.metadata.getVersions();
		}

		@Override
		public String getLastUpdated() {
			return this.metadata.getLastUpdated();
		}
	}
}

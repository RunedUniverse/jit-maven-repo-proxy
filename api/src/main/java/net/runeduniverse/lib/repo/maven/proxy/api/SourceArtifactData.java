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

import java.nio.file.Path;
import java.util.Map;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;

public interface SourceArtifactData extends ArtifactData {

	public RepositorySource source();

	public static SourceArtifactData wrap(final RepositorySource source, final ArtifactData data) {
		return new WrappedAdapter(source, data);
	}

	public static class WrappedAdapter implements SourceArtifactData {

		protected final RepositorySource source;
		protected final ArtifactData data;

		public WrappedAdapter(final RepositorySource source, final ArtifactData data) {
			this.source = source;
			this.data = data;
		}

		@Override
		public RepositorySource source() {
			return this.source;
		}

		public ArtifactData wrappedData() {
			return this.data;
		}

		@Override
		public String getGroupId() {
			return this.data.getGroupId();
		}

		@Override
		public String getArtifactId() {
			return this.data.getArtifactId();
		}

		@Override
		public String getVersion() {
			return this.data.getVersion();
		}

		@Override
		public String getClassifier() {
			return this.data.getClassifier();
		}

		@Override
		public String getExtension() {
			return this.data.getExtension();
		}

		@Override
		public Path getArtifactPath() {
			return this.data.getArtifactPath();
		}

		@Override
		public Path getSignaturePath() {
			return this.data.getSignaturePath();
		}

		@Override
		public Map<String, String> getChecksums() {
			return this.data.getChecksums();
		}
	}
}

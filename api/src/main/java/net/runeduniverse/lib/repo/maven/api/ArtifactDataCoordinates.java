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
package net.runeduniverse.lib.repo.maven.api;

public interface ArtifactDataCoordinates extends ArtifactCoordinates {

	public String getVersion();

	public String getClassifier();

	public String getExtension();

	public static String key(final ArtifactDataCoordinates coords) {
		return String.format("%s:%s:%s:%s:%s", coords.getGroupId(), coords.getArtifactId(), coords.getVersion(),
				coords.getClassifier(), coords.getExtension());
	}

	public static ArtifactDataCoordinates request(final String groupId, final String artifactId, final String version,
			final String classifier, final String extension) {
		return new Data(groupId, artifactId, version, classifier, extension);
	}

	public static class Data extends ArtifactCoordinates.Data implements ArtifactDataCoordinates {

		protected String classifier;
		protected String extension;
		protected String version;

		public Data(final String groupId, final String artifactId, final String version, final String classifier,
				final String extension) {
			super(groupId, artifactId);
			this.version = version;
			this.classifier = classifier;
			this.extension = extension;
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
	}
}

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

import java.util.Objects;

public interface ArtifactCoordinates {

	public String getGroupId();

	public String getArtifactId();

	public static String key(final ArtifactCoordinates coords) {
		return String.format("%s:%s", coords.getGroupId(), coords.getArtifactId());
	}

	public static ArtifactCoordinates build(final String groupId, final String artifactId) {
		return new Data(groupId, artifactId);
	}

	public static class Data implements ArtifactCoordinates {

		protected String groupId;
		protected String artifactId;

		public Data(final String groupId, final String artifactId) {
			this.groupId = groupId;
			this.artifactId = artifactId;
		}

		@Override
		public String getGroupId() {
			return this.groupId;
		}

		@Override
		public String getArtifactId() {
			return this.artifactId;
		}

		@Override
		public int hashCode() {
			return key(this).hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof ArtifactCoordinates))
				return false;
			final ArtifactCoordinates other = (ArtifactCoordinates) obj;
			return Objects.equals(this.groupId, other.getGroupId())//
					&& Objects.equals(this.artifactId, other.getArtifactId());
		}
	}
}

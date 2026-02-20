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

public interface ArtifactCoordinates {

	public String getGroupId();

	public String getArtifactId();

	public static ArtifactCoordinates request(final String groupId, final String artifactId) {
		return new Request(groupId, artifactId);
	}

	public static class Request implements ArtifactCoordinates {

		protected String groupId;
		protected String artifactId;

		public Request(final String groupId, final String artifactId) {
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
	}
}

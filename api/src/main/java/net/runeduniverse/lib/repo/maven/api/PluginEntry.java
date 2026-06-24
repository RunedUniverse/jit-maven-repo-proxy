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

public interface PluginEntry {

	public String getName();

	public String getPrefix();

	public String getArtifactId();

	public static class Data implements PluginEntry {

		protected String name;
		protected String prefix;
		protected String artifactId;

		public Data(final String name, final String prefix, final String artifactId) {
			this.name = name;
			this.prefix = prefix;
			this.artifactId = artifactId;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public String getPrefix() {
			return this.prefix;
		}

		@Override
		public String getArtifactId() {
			return this.artifactId;
		}

		@Override
		public int hashCode() {
			return this.prefix == null ? 0 : this.prefix.hashCode();
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (!(obj instanceof PluginEntry))
				return false;
			final PluginEntry other = (PluginEntry) obj;
			return Objects.equals(this.getPrefix(), other.getPrefix())//
					&& Objects.equals(this.getArtifactId(), other.getArtifactId())//
					&& Objects.equals(this.getName(), other.getName());
		}
	}
}
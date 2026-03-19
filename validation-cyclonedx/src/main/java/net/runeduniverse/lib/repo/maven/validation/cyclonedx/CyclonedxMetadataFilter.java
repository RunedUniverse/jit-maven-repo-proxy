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
package net.runeduniverse.lib.repo.maven.validation.cyclonedx;

import java.util.Collection;
import java.util.Iterator;
import org.cyclonedx.model.Component;

import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;

public class CyclonedxMetadataFilter implements MetadataValidator {

	protected final ComponentIndex componentIndex;

	public CyclonedxMetadataFilter(final ComponentIndex componentIndex) {
		this.componentIndex = componentIndex;
	}

	@Override
	public boolean validate(final ArtifactMetadata metadata) throws InvalidArtifactException {
		// remove all versions where there is no component backing it!
		for (Iterator<String> i = metadata.getVersionIterator(); i.hasNext();) {
			final String version = i.next();
			final Collection<Component> col = this.componentIndex
					.getComponentsByGA(metadata.getGroupId(), metadata.getArtifactId())
					.get(version);
			if (col == null || col.isEmpty())
				i.remove();
		}
		return true;
	}
}

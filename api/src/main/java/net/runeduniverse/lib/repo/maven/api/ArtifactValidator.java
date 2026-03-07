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

import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;

@FunctionalInterface
public interface ArtifactValidator {

	/**
	 * Validate ArtifactData, if validity can not be asserted return {@code false}.
	 * When problems are detected throw an {@link InvalidArtifactException} else
	 * return {@code true}.
	 *
	 * @param data to be validated
	 * @return {@code true}, if the ArtifactData could be validated else
	 *         {@code false}
	 * @throws InvalidArtifactException
	 */
	public boolean validate(ArtifactData data) throws InvalidArtifactException;

	public default ArtifactValidator andThen(ArtifactValidator after) {
		return after == null ? this : d -> validate(d) || after.validate(d);
	}
}

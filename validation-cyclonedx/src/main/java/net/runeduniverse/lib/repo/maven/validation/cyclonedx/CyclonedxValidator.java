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

import org.cyclonedx.model.Bom;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.validation.pgp.PGPArtifactSignatureValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class CyclonedxValidator implements ArtifactValidator {

	protected final Bom bom;
	protected final PublicKeyIndex keyIndex;

	public CyclonedxValidator(final Bom bom, final PublicKeyIndex keyIndex) {
		this.bom = bom;
		this.keyIndex = keyIndex;
	}

	@Override
	public boolean validate(final ArtifactData data) throws InvalidArtifactException {
		final PGPArtifactSignatureValidator pgpValidator = new PGPArtifactSignatureValidator(this.keyIndex);

		// TODO check the artifact signature

		if (pgpValidator.validate(data)) {
			// if the signature exists
			// -> fails if -> throws error -> accepted
			// -> if true, try to check the signature against the SBOM

			// TODO check the signature against the SBOM
		}

		return false;
	}
}

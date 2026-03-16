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

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Hash;
import org.cyclonedx.model.Property;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidChecksumArtifactException;
import net.runeduniverse.lib.repo.maven.error.SBomViolationException;
import net.runeduniverse.lib.repo.maven.validation.pgp.PGPArtifactSignatureValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class CyclonedxValidator implements ArtifactValidator {

	public static final String MSG_CHECKSUM_MISSMATCH = "SBOM: Artifact Checksum does not match expected value!";
	public static final String MSG_SIGNATURE_KEYID_MISSMATCH = "SBOM: Artifact PGP-Signature KeyID does not match expected value!";
	public static final String MSG_SIGNATURE_FINGERPRINT_MISSMATCH = "SBOM: Artifact PGP-Signature Fingerprint does not match expected value!";

	protected final PublicKeyIndex keyIndex;
	protected final ComponentIndex componentIndex;

	protected boolean skipMissing = false;

	public CyclonedxValidator(final PublicKeyIndex keyIndex, final ComponentIndex componentIndex) {
		this.keyIndex = keyIndex;
		this.componentIndex = componentIndex;
	}

	/**
	 * Set skipMissing components flag.
	 * <p>
	 * NOTE: Only use when intending to chain multiple SBOM-Validator, otherwise
	 * artifacts will slip through the cracks!
	 *
	 * @param value {@code true} to disarm the validate() method, prevents throwing
	 *              of {@link InvalidArtifactException} for missing components, else
	 *              {@code false}.
	 */
	public void setSkipMissing(final boolean value) {
		this.skipMissing = value;
	}

	@Override
	public boolean validate(final ArtifactData data) throws InvalidArtifactException {
		final PGPArtifactSignatureValidator pgpValidator = new PGPArtifactSignatureValidator(this.keyIndex);

		final Component comp = this.componentIndex.getComponentByPURL(data.getPURL());
		if (comp == null) {
			if (this.skipMissing)
				return false;
			else
				throw new SBomViolationException("SBOM: Artifact not listed in SBOM");
		}

		// -- create Property-Map
		final Map<String, String> properties = new LinkedHashMap<>();
		{
			final List<Property> propList = comp.getProperties();
			if (propList != null) {
				for (Property property : propList) {
					final String key = StringUtils.trimToNull(property.getName());
					if (key == null)
						continue;
					properties.put(key, property.getValue());
				}
			}
		}

		// -- verify checksums, if available
		final Collection<Hash> hashes = comp.getHashes();
		if (hashes != null && !hashes.isEmpty()) {
			final Map<String, String> dataHashes = data.getChecksums();

			for (Hash hash : hashes) {
				final String algorithm = hash.getAlgorithm();
				final String localChecksum = dataHashes.get(algorithm);
				final String remoteChecksum = hash.getValue();
				if (!localChecksum.equalsIgnoreCase(remoteChecksum)) {
					throw new InvalidChecksumArtifactException(MSG_CHECKSUM_MISSMATCH, algorithm, localChecksum,
							remoteChecksum);
				}
			}
		}

		// -- verify artifact signature, if available
		if (pgpValidator.validate(data)) {
			// if the signature exists
			// -> fails if -> throws error -> accepted
			// -> if true, try to check the signature against the SBOM
			final String pgpKeyID = properties.get("pgp:keyId");
			final String pgpFingerprint = properties.get("pgp:fingerprint");

			if (pgpKeyID != null || pgpFingerprint != null) {
				// TODO check the signature against the SBOM

				try {
					final PGPSignature pgpSignature = pgpValidator.getSignature(data.getSignaturePath());
					final long keyID = pgpSignature.getKeyID();

					if (pgpKeyID != null) {
						if (!pgpKeyID.trim()
								.equalsIgnoreCase(Long.toHexString(keyID)))
							throw new SBomViolationException(MSG_SIGNATURE_KEYID_MISSMATCH);
					}

					if (pgpFingerprint != null) {
						// the key must already be known otherwise pgpValidator.validate(data)
						// would not be true
						final PGPPublicKey pubKey = this.keyIndex.getKey(keyID);

						if (!pgpFingerprint.trim()
								.equalsIgnoreCase(Hex.encodeHexString(pubKey.getFingerprint())))
							throw new SBomViolationException(MSG_SIGNATURE_FINGERPRINT_MISSMATCH);
					}
				} catch (IOException | PGPException e) {
					throw new SBomViolationException("SBOM: Unexpected Exception occurred when loading PGPSignature",
							e);
				}
			}
		}

		return true;
	}
}

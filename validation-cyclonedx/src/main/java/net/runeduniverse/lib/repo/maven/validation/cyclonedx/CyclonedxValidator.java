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
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Hash;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidChecksumArtifactException;
import net.runeduniverse.lib.repo.maven.error.BomViolationException;
import net.runeduniverse.lib.repo.maven.validation.pgp.PGPArtifactSignatureValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

import static net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex.toHexFingerprint;
import static net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex.toHexKeyID;

public class CyclonedxValidator implements ArtifactValidator {

	public static final String MSG_ARTIFACT_NOT_FOUND = "BOM: Artifact not listed in BOM";
	public static final String MSG_CHECKSUM_MISSMATCH = "BOM: Artifact Checksum does not match expected value!";
	public static final String MSG_SIGNATURE_KEYID_MISSMATCH = "BOM: Artifact PGP-Signature KeyID does not match expected value!";
	public static final String MSG_SIGNATURE_FINGERPRINT_MISSMATCH = "BOM: Artifact PGP-Signature Fingerprint does not match expected value!";
	public static final String MSG_SIGNATURE_FAILED_MISSMATCH = "BOM: Artifact PGP-Signature could not be verified with a trusted PGP-Public-Key!";

	public static final String PROP_PGP_KEYID = "pgp:keyId";
	public static final String PROP_PGP_FINGERPRINT = "pgp:fingerprint";

	protected final PublicKeyIndex keyIndex;
	protected final ComponentIndex componentIndex;

	protected boolean skipMissing = false;
	protected boolean ignorePom = false;

	public CyclonedxValidator(final PublicKeyIndex keyIndex, final ComponentIndex componentIndex) {
		this.keyIndex = keyIndex;
		this.componentIndex = componentIndex;
	}

	/**
	 * Set skipMissing components flag.
	 * <p>
	 * NOTE: Only use when intending to chain multiple BOM-Validator, otherwise
	 * artifacts will slip through the cracks!
	 *
	 * @param value {@code true} to disarm the validate() method, prevents throwing
	 *              of {@link InvalidArtifactException} for missing components, else
	 *              {@code false}.
	 * @return this CyclonedxValidator instance, for chaining
	 */
	public CyclonedxValidator setSkipMissing(final boolean value) {
		this.skipMissing = value;
		return this;
	}

	public CyclonedxValidator setIgnorePom(final boolean value) {
		this.ignorePom = value;
		return this;
	}

	@Override
	public boolean validate(final ArtifactProvider provider, final ArtifactData data) throws InvalidArtifactException {
		final Component comp = this.componentIndex.getComponentByPURL(data.getPURL());
		if (comp == null) {
			if (this.skipMissing || this.ignorePom && data.isPOM())
				return false;
			else
				throw new BomViolationException(MSG_ARTIFACT_NOT_FOUND);
		}

		// -- create Property-Map
		final Map<String, String> properties = ComponentIndex.getProperties(comp);

		// -- verify checksums, if available
		final Collection<Hash> hashes = comp.getHashes();
		if (hashes != null && !hashes.isEmpty()) {
			final Map<String, String> dataHashes = data.getChecksums();

			for (Hash hash : hashes) {
				final String algorithm = hash.getAlgorithm();
				final String localChecksum = dataHashes.get(algorithm);
				final String remoteChecksum = hash.getValue();
				if (localChecksum != null && !localChecksum.equalsIgnoreCase(remoteChecksum)) {
					throw new InvalidChecksumArtifactException(MSG_CHECKSUM_MISSMATCH, algorithm, localChecksum,
							remoteChecksum);
				}
			}
		}

		// -- verify artifact signature, if available
		final PGPArtifactSignatureValidator pgpValidator = new PGPArtifactSignatureValidator(this.keyIndex) {

			protected boolean keysEliminated = false;

			@Override
			public Collection<PGPPublicKey> validatePublicKeys(final ArtifactData data, final PGPSignature signature,
					final Collection<PGPPublicKey> keys) {
				// remove all keys that do not match BOM values!
				final String pgpKeyID = StringUtils.trimToNull(properties.get(PROP_PGP_KEYID));
				final String pgpFingerprint = StringUtils.trimToNull(properties.get(PROP_PGP_FINGERPRINT));

				if (pgpKeyID != null || pgpFingerprint != null) {

					for (Iterator<PGPPublicKey> i = keys.iterator(); i.hasNext();) {
						final PGPPublicKey pubKey = i.next();
						if (pubKey == null) {
							i.remove();
							continue;
						}

						if (pgpKeyID != null && !pgpKeyID.equalsIgnoreCase(toHexKeyID(pubKey.getKeyID()))) {
							this.keysEliminated = true;
							i.remove();
						}

						if (pgpFingerprint != null
								&& !pgpFingerprint.equalsIgnoreCase(toHexFingerprint(pubKey.getFingerprint()))) {
							this.keysEliminated = true;
							i.remove();
						}
					}
				}

				return keys;
			}

			@Override
			public void onValidateSuccess(final ArtifactData data, final PGPSignature signature,
					final PGPPublicKey pubKey) throws InvalidArtifactException {
				// if the signature had a key-fingerprint attached, a key in violation can
				// sucessfully verify a signature and reach this point!
				final String pgpKeyID = StringUtils.trimToNull(properties.get(PROP_PGP_KEYID));
				final String pgpFingerprint = StringUtils.trimToNull(properties.get(PROP_PGP_FINGERPRINT));

				if (pgpKeyID != null && !pgpKeyID.equalsIgnoreCase(toHexKeyID(pubKey.getKeyID()))) {
					throw new BomViolationException(MSG_SIGNATURE_KEYID_MISSMATCH);
				}

				if (pgpFingerprint != null
						&& !pgpFingerprint.equalsIgnoreCase(toHexFingerprint(pubKey.getFingerprint()))) {
					throw new BomViolationException(MSG_SIGNATURE_FINGERPRINT_MISSMATCH);
				}
			}

			@Override
			public InvalidArtifactException onValidateFailure(final ArtifactData data, final PGPSignature signature,
					final Throwable cause) {
				if (cause == null && this.keysEliminated) {
					// there's a chance we filtered out all public-keys
					return new BomViolationException(MSG_SIGNATURE_FAILED_MISSMATCH);
				}
				return super.onValidateFailure(data, signature, cause);
			}
		};

		// true if the signature exists
		// -> fails if -> throws error -> accepted
		pgpValidator.validate(provider, data);

		return true;
	}
}

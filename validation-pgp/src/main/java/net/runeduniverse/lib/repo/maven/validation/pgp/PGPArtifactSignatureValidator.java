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
package net.runeduniverse.lib.repo.maven.validation.pgp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Provider;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.bcpg.BCPGInputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactSignatureException;

import static net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex.toHexFingerprint;
import static net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex.toHexKeyID;

public class PGPArtifactSignatureValidator implements ArtifactValidator {

	public static final Provider BOUNCY_CASTLE_PROVIDER = new BouncyCastleProvider();

	static {
		Security.insertProviderAt(BOUNCY_CASTLE_PROVIDER, 1);
	}

	protected final PublicKeyIndex index;

	public PGPArtifactSignatureValidator(final PublicKeyIndex index) {
		this.index = index;
	}

	@Override
	public boolean validate(final ArtifactData data) throws InvalidArtifactException {
		final Path sigPath = data.getSignaturePath();
		if (!Files.exists(sigPath))
			return false;

		final PGPSignature signature;
		try {
			signature = getSignature(sigPath);
		} catch (IOException | PGPException e) {
			e.printStackTrace(System.err);
			return false;
		}
		if (signature == null) {
			System.err.println("No Signature found for " + ArtifactDataCoordinates.key(data));
			return false;
		}

		// 1) if key-fingerprint metadata accessible -> get that key!
		final PGPSignatureSubpacketVector hashedSubPackets = signature.getHashedSubPackets();
		final PGPSignatureSubpacketVector unhashedSubPackets = signature.getUnhashedSubPackets();

		byte[] fingerprint = null;

		// Prefer hashed subpackets
		if (hashedSubPackets != null && hashedSubPackets.getIssuerFingerprint() != null) {
			fingerprint = hashedSubPackets.getIssuerFingerprint()
					.getFingerprint();
		}
		// fallback (rare, but possible)
		else if (unhashedSubPackets != null && unhashedSubPackets.getIssuerFingerprint() != null) {
			fingerprint = unhashedSubPackets.getIssuerFingerprint()
					.getFingerprint();
		}
		if (fingerprint != null)
			return validateByFingerprint(data, signature, fingerprint);

		// 2) if not search all keys with matching keyID until found!
		return validateByKeyID(data, signature, signature.getKeyID());
	}

	public boolean validateByFingerprint(final ArtifactData data, final PGPSignature signature,
			final byte[] fingerprint) throws InvalidArtifactException {
		final PGPPublicKey pubKey;

		try {
			pubKey = getPublicKey(fingerprint);
			if (pubKey == null) {
				System.err.println("----------------- HERE » 1 --------------");
				System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " FP: "
						+ toHexFingerprint(fingerprint));
				return false;
			}
		} catch (InterruptedException | CancellationException e) {
			System.err.println("----------------- HERE » 2 --------------");
			System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " FP: "
					+ toHexFingerprint(fingerprint));
			e.printStackTrace(System.err);
			return false;
		} catch (ExecutionException e) {
			System.err.println("----------------- HERE » 3 --------------");
			System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " FP: "
					+ toHexFingerprint(fingerprint));
			e.getCause()
					.printStackTrace(System.err);
			return false;
		} catch (TimeoutException e) {
			System.err.println("----------------- HERE » 4 --------------");
			System.err.println("Timeout reached when loading Public-Key for " + ArtifactDataCoordinates.key(data)
					+ toHexFingerprint(fingerprint));
			return false;
		}

		Throwable cause = null;
		try {
			final PGPPublicKey matchedPubKey = verifyArtifact(signature, data.getArtifactPath(),
					Collections.singleton(pubKey), (pubKeyEx, pgpEx) -> {
						PGPArtifactSignatureValidator.this.handleVerifyArtifactError(data, pubKeyEx, pgpEx);
					});
			if (matchedPubKey != null) {
				onValidateSuccess(data, signature, matchedPubKey);
				return true;
			}
		} catch (IOException e) {
			cause = e;
			System.err.println(
					"Failed to load Artifact " + ArtifactDataCoordinates.key(data) + " for Signature verification");
			e.getCause()
					.printStackTrace(System.err);
		}

		throw new InvalidArtifactSignatureException("Failed to verify Artifact Signature!", cause);
	}

	public boolean validateByKeyID(final ArtifactData data, final PGPSignature signature, final long sigKeyID)
			throws InvalidArtifactException {
		final Iterator<CompletableFuture<Collection<PGPPublicKey>>> locator = this.index.fetchKeysById(sigKeyID);
		Throwable cause = null;

		while (locator.hasNext()) {
			Collection<PGPPublicKey> keys;
			// get set of public-keys
			try {
				keys = awaitPublicKeys(locator.next());
			} catch (InterruptedException | CancellationException e) {
				System.err.println("----------------- HERE » 2 --------------");
				System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
						+ toHexKeyID(sigKeyID));
				e.printStackTrace(System.err);
				continue;
			} catch (ExecutionException e) {
				System.err.println("----------------- HERE » 3 --------------");
				System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
						+ toHexKeyID(sigKeyID));
				e.getCause()
						.printStackTrace(System.err);
				continue;
			} catch (TimeoutException e) {
				System.err.println("----------------- HERE » 4 --------------");
				System.err.println("Timeout reached when loading Public-Key for " + ArtifactDataCoordinates.key(data)
						+ " ID: " + toHexKeyID(sigKeyID));
				continue;
			}
			// validate the keys
			keys = validatePublicKeys(data, signature, keys);
			// try to verify the artifact using the keys
			try {
				final PGPPublicKey pubKey = verifyArtifact(signature, data.getArtifactPath(), keys,
						(pubKeyEx, pgpEx) -> {
							PGPArtifactSignatureValidator.this.handleVerifyArtifactError(data, pubKeyEx, pgpEx);
						});
				if (pubKey != null) {
					onValidateSuccess(data, signature, pubKey);
					return true;
				}
			} catch (IOException e) {
				cause = e;
				System.err.println(
						"Failed to load Artifact " + ArtifactDataCoordinates.key(data) + " for Signature verification");
				e.getCause()
						.printStackTrace(System.err);
			}
		}

		throw new InvalidArtifactSignatureException("Failed to verify Artifact Signature!", cause);
	}

	public Collection<PGPPublicKey> validatePublicKeys(final ArtifactData data, final PGPSignature signature,
			final Collection<PGPPublicKey> keys) {
		// downstream classes may filter out invalid keys
		return keys;
	}

	public void onValidateSuccess(final ArtifactData data, final PGPSignature signature, final PGPPublicKey pubKey) {
		// downstream classes log the success
	}

	public PGPSignature getSignature(final Path signaturePath) throws IOException, PGPException {
		if (!Files.exists(signaturePath))
			return null;
		try (final InputStream sigStream = Files.newInputStream(signaturePath, StandardOpenOption.READ)) {
			return getSignature(sigStream);
		}
	}

	public PGPSignature getSignature(InputStream sigStream) throws IOException, PGPException {
		if (!(sigStream instanceof ArmoredInputStream)) {
			sigStream = new ArmoredInputStream(sigStream);
		}
		PGPObjectFactory objFactory = new PGPObjectFactory(sigStream, new JcaKeyFingerprintCalculator());

		Object obj;
		while ((obj = objFactory.nextObject()) != null) {
			if (obj instanceof PGPSignatureList) {
				// try to find a PGPSignature in the list
				for (Iterator<PGPSignature> i = ((PGPSignatureList) obj).iterator(); i.hasNext();) {
					final PGPSignature sig = i.next();
					if (sig != null)
						return sig;
				}
			} else if (obj instanceof PGPCompressedData) {
				// if the data is compress -> decompress
				objFactory = new PGPObjectFactory(((PGPCompressedData) obj).getDataStream(),
						new JcaKeyFingerprintCalculator());
			}
		}
		return null;
	}

	protected PGPSignature copySignature(final PGPSignature signature) {
		try {
			final byte[] encodedData = signature.getEncoded();
			try (final BCPGInputStream bcpgIn = new BCPGInputStream(//
					new ByteArrayInputStream(encodedData))) {
				return new PGPSignature(bcpgIn);
			}
		} catch (IOException | PGPException unexpected) {
			// TODO add logging!
			unexpected.printStackTrace();
		}
		return null;
	}

	protected Collection<PGPPublicKey> awaitPublicKeys(final CompletableFuture<Collection<PGPPublicKey>> future)
			throws InterruptedException, CancellationException, ExecutionException, TimeoutException {
		return future.get(5, TimeUnit.MINUTES);
	}

	public PGPPublicKey getPublicKey(final byte[] fingerprint)
			throws InterruptedException, CancellationException, ExecutionException, TimeoutException {
		final CompletableFuture<PGPPublicKey> pubKeyFuture = getPublicKeyFuture(fingerprint);
		return pubKeyFuture.get(5, TimeUnit.MINUTES);
	}

	public CompletableFuture<PGPPublicKey> getPublicKeyFuture(final byte[] fingerprint) {
		return this.index.fetchKeyIfAbsent(fingerprint);
	}

	public PGPPublicKey verifyArtifact(final PGPSignature signature, final Path artifactPath,
			final Collection<PGPPublicKey> publicKeys, final BiConsumer<PGPPublicKey, PGPException> errorHandler)
			throws IOException {
		try (final InputStream artifactStream = Files.newInputStream(artifactPath, StandardOpenOption.READ)) {
			return verifyArtifact(signature, artifactStream, publicKeys, errorHandler);
		}
	}

	public PGPPublicKey verifyArtifact(final PGPSignature signature, final InputStream artifactStream,
			final Collection<PGPPublicKey> publicKeys, final BiConsumer<PGPPublicKey, PGPException> errorHandler)
			throws IOException {
		final Map<PGPPublicKey, PGPSignature> sigs = new LinkedHashMap<>();

		for (PGPPublicKey pubKey : publicKeys) {
			if (pubKey == null)
				continue;
			final PGPSignature sig = copySignature(signature);
			if (sig == null)
				continue;
			try {
				sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider(BOUNCY_CASTLE_PROVIDER), pubKey);
			} catch (PGPException e) {
				errorHandler.accept(pubKey, e);
			}
			sigs.put(pubKey, sig);
		}

		byte[] buffer = new byte[8192];
		int len;

		while ((len = artifactStream.read(buffer)) != -1) {
			for (PGPSignature sig : sigs.values()) {
				sig.update(buffer, 0, len);
			}
		}

		for (Map.Entry<PGPPublicKey, PGPSignature> entry : sigs.entrySet()) {
			try {
				// for every signature there exists 1 publicKey -> if found stop!
				if (entry.getValue()
						.verify())
					return entry.getKey();
			} catch (PGPException unexpected) {
				// TODO add logging!
				unexpected.printStackTrace();
			}
		}
		return null;
	}

	protected void handleVerifyArtifactError(final ArtifactData data, final PGPPublicKey pubKeyEx,
			final PGPException pgpEx) {
		System.err.println("Invalid Public-Key for the Signature provided by " + ArtifactDataCoordinates.key(data));
		System.out.println("Pub-Key Algorithm: " + pubKeyEx.getAlgorithm());
		pgpEx.getCause()
				.printStackTrace(System.err);
	}
}

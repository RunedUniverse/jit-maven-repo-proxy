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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Provider;
import java.security.Security;
import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;

import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactSignatureException;

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

		final long sigKeyID = signature.getKeyID();
		final PGPPublicKey pubKey;

		try {
			final PGPPublicKeyRing pubKeyRing = getPublicKeyRing(sigKeyID);
			if (pubKeyRing == null) {
				System.err.println("----------------- HERE » 1 --------------");
				System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
						+ Long.toHexString(sigKeyID)
								.toUpperCase());
				return false;
			}

			pubKey = findMatchingPublicKey(pubKeyRing, sigKeyID);
			if (pubKey == null) {
				System.err.println("----------------- HERE » 1.2 --------------");
				System.err.println("No matching Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
						+ Long.toHexString(sigKeyID)
								.toUpperCase());
				return false;
			}
		} catch (InterruptedException | CancellationException e) {
			System.err.println("----------------- HERE » 2 --------------");
			System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
					+ Long.toHexString(sigKeyID)
							.toUpperCase());
			e.printStackTrace(System.err);
			return false;
		} catch (ExecutionException e) {
			System.err.println("----------------- HERE » 3 --------------");
			System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
					+ Long.toHexString(sigKeyID)
							.toUpperCase());
			e.getCause()
					.printStackTrace(System.err);
			return false;
		} catch (TimeoutException e) {
			System.err.println("----------------- HERE » 4 --------------");
			System.err.println("Timeout reached when loading Public-Key for " + ArtifactDataCoordinates.key(data)
					+ " ID: " + Long.toHexString(sigKeyID)
							.toUpperCase());
			return false;
		}

		Throwable cause = null;
		try {
			if (verifyArtifact(signature, pubKey, data.getArtifactPath()))
				return true;
		} catch (IOException e) {
			System.err.println(
					"Failed to load Artifact " + ArtifactDataCoordinates.key(data) + " for Signature verification");
			e.getCause()
					.printStackTrace(System.err);
			return false;
		} catch (PGPException e) {
			cause = e;
			System.err.println("Invalid Public-Key for the Signature provided by " + ArtifactDataCoordinates.key(data));
			System.out.println("Pub-Key Algorithm: " + pubKey.getAlgorithm());
			e.getCause()
					.printStackTrace(System.err);
		}

		throw new InvalidArtifactSignatureException("Failed to verify Artifact Signature!", cause);
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

	public PGPPublicKeyRing getPublicKeyRing(final long keyID)
			throws InterruptedException, CancellationException, ExecutionException, TimeoutException {
		final CompletableFuture<PGPPublicKeyRing> pubKeyRingFuture = getPublicKeyRingFuture(keyID);
		return pubKeyRingFuture.get(5, TimeUnit.MINUTES);
	}

	public CompletableFuture<PGPPublicKeyRing> getPublicKeyRingFuture(final long keyID) {
		return this.index.fetchKeyRingIfAbsent(keyID);
	}

	public PGPPublicKey findMatchingPublicKey(final PGPPublicKeyRing publicKeyRing, final long keyID) {
		for (Iterator<PGPPublicKey> i = publicKeyRing.getPublicKeys(); i.hasNext();) {
			final PGPPublicKey key = i.next();
			if (key.getKeyID() == keyID)
				return key;
		}
		return null;
	}

	public boolean verifyArtifact(final PGPSignature signature, final PGPPublicKey publicKey, final Path artifactPath)
			throws IOException, PGPException {
		try (final InputStream artifactStream = Files.newInputStream(artifactPath, StandardOpenOption.READ)) {
			return verifyArtifact(signature, publicKey, artifactStream);
		}
	}

	public boolean verifyArtifact(final PGPSignature signature, final PGPPublicKey publicKey,
			final InputStream artifactStream) throws IOException, PGPException {
		signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(BOUNCY_CASTLE_PROVIDER), publicKey);

		byte[] buffer = new byte[8192];
		int len;

		while ((len = artifactStream.read(buffer)) != -1) {
			signature.update(buffer, 0, len);
		}

		return signature.verify();
	}
}

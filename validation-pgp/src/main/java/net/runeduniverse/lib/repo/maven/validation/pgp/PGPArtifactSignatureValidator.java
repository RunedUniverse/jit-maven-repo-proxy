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
		Security.addProvider(BOUNCY_CASTLE_PROVIDER);
	}

	protected final PublicKeyIndex index;

	public PGPArtifactSignatureValidator(final PublicKeyIndex index) {
		this.index = index;
	}

	protected PGPSignature extractSignature(InputStream sigStream) throws IOException, PGPException {
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

	@Override
	public boolean validate(final ArtifactData data) throws InvalidArtifactException {
		final Path sigPath = data.getSignaturePath();
		if (!Files.exists(sigPath))
			return false;

		final PGPSignature signature;
		final InputStream artifactStream;
		try {
			final InputStream sigStream = Files.newInputStream(sigPath, StandardOpenOption.READ);
			signature = extractSignature(sigStream);
			artifactStream = Files.newInputStream(data.getArtifactPath(), StandardOpenOption.READ);
		} catch (IOException | PGPException e) {
			e.printStackTrace(System.err);
			return false;
		}
		if (signature == null) {
			System.err.println("No Signature found for " + ArtifactDataCoordinates.key(data));
			return false;
		}

		final CompletableFuture<PGPPublicKeyRing> pubKeyRingFuture = this.index
				.fetchKeyRingIfAbsent(signature.getKeyID());
		final PGPPublicKey pubKey;

		try {
			final PGPPublicKeyRing pubKeyRing = pubKeyRingFuture.get();
			if (pubKeyRing == null) {
				System.err.println("----------------- HERE » 1 --------------");
				System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
						+ Long.toHexString(signature.getKeyID())
								.toUpperCase());
				System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
						+ signature.getKeyID());
				return false;
			}
			pubKey = pubKeyRing.getPublicKey();
		} catch (InterruptedException | CancellationException e) {
			System.err.println("----------------- HERE » 2 --------------");
			System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
					+ Long.toHexString(signature.getKeyID())
							.toUpperCase());
			e.printStackTrace(System.err);
			return false;
		} catch (ExecutionException e) {
			System.err.println("----------------- HERE » 3 --------------");
			System.err.println("No Public-Key found for " + ArtifactDataCoordinates.key(data) + " ID: "
					+ Long.toHexString(signature.getKeyID())
							.toUpperCase());
			e.getCause()
					.printStackTrace(System.err);
			return false;
		}

		try {
			signature.init(new JcaPGPContentVerifierBuilderProvider().setProvider(BOUNCY_CASTLE_PROVIDER), pubKey);
		} catch (PGPException e) {
			System.err.println("Invalid Public-Key for the Signature provided by" + ArtifactDataCoordinates.key(data));
			e.getCause()
					.printStackTrace(System.err);
			return false;
		}

		byte[] buffer = new byte[8192];
		int len;

		try {
			while ((len = artifactStream.read(buffer)) != -1) {
				signature.update(buffer, 0, len);
			}
		} catch (IOException e) {
			System.err.println(
					"Failed to load Artifact " + ArtifactDataCoordinates.key(data) + " for Signature verification");
			e.getCause()
					.printStackTrace(System.err);
			return false;
		}

		Throwable cause = null;
		try {
			if (signature.verify())
				return true;
		} catch (PGPException e) {
			cause = e;
		}

		throw new InvalidArtifactSignatureException("Failed to verify Artifact Signature!", cause);
	}
}

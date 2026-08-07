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
package net.runeduniverse.lib.repo.maven.proxy.validation.pgp.test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

import static net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex.toHexFingerprint;
import static net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex.toHexKeyID;

public class PublicKeyIndexTest {

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

	@Test
	@Tag("live-with-internet")
	public void findByKeyID() throws InterruptedException, TimeoutException {
		PublicKeyIndex index = PublicKeyIndex.createDefaultKeyIndex();

		long keyID = -7155602096415788222L;
		String fingerprint = "78BC87F32F7607FC3411CCB89CB231CE2918B342";

		Iterator<CompletableFuture<Collection<PGPPublicKey>>> i = index.fetchKeysById(keyID);

		PGPPublicKey publicKey = null;

		// search cache & keyservers
		searchLoop: while (i.hasNext()) {
			Future<Collection<PGPPublicKey>> future = i.next();

			Collection<PGPPublicKey> col;
			try {
				col = future.get(30, TimeUnit.SECONDS);
			} catch (ExecutionException e) {
				e.printStackTrace();
				continue;
			}

			if (col.isEmpty())
				continue;

			Iterator<PGPPublicKey> j = col.iterator();
			while (j.hasNext()) {
				publicKey = j.next();
				if (fingerprint.equals(toHexFingerprint(publicKey.getFingerprint())))
					break searchLoop;
			}
		}

		Assertions.assertNotNull(publicKey, "Failed to fetch PublicKey, ID: 9CB231CE2918B342");

		print(String.format("Fetched PublicKey with ID: %s / Fingerprint: %s",
				toHexKeyID(publicKey.getKeyID()).toUpperCase(), toHexFingerprint(publicKey.getFingerprint())));
	}

	@Test
	@Tag("live-with-internet")
	public void findByFingerprint() throws InterruptedException, TimeoutException {
		PublicKeyIndex index = PublicKeyIndex.createDefaultKeyIndex();

		String fingerprint = "78BC87F32F7607FC3411CCB89CB231CE2918B342";

		PGPPublicKey publicKey = null;
		try {
			CompletableFuture<PGPPublicKey> future = index.fetchKeyIfAbsent(fingerprint);
			publicKey = future.get(30, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		Assertions.assertNotNull(publicKey, "Failed to fetch PublicKey, ID: 9CB231CE2918B342");

		print(String.format("Fetched PublicKey with ID: %s / Fingerprint: %s",
				toHexKeyID(publicKey.getKeyID()).toUpperCase(), toHexFingerprint(publicKey.getFingerprint())));
	}
}

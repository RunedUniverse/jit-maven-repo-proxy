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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class PublicKeyIndexTest {

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

	@Test
	@Tag("live")
	public void exec() throws InterruptedException, TimeoutException {
		PublicKeyIndex index = PublicKeyIndex.createDefaultKeyIndex();

		Future<PGPPublicKeyRing> keyringFuture = index.fetchKeyRingIfAbsent(-7155602096415788222L);

		PGPPublicKeyRing keyring = null;
		try {
			keyring = keyringFuture.get(30, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		Assertions.assertNotNull(keyring, "Failed to fetch PublicKeyRing, ID: 9CB231CE2918B342");
		PGPPublicKey publicKey = keyring.getPublicKey();
		Assertions.assertNotNull(publicKey, "PublicKey was null, ID: 9CB231CE2918B342");

		print(String.format("Fetched PublicKey with ID: %s / Fingerprint: %s", Long.toHexString(publicKey.getKeyID())
				.toUpperCase(), publicKey.getFingerprint()));
	}

}

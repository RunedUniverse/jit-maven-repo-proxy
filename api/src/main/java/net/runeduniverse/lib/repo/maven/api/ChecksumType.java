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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class ChecksumType {

	private static final Map<String, ChecksumType> KNWON_VALUES = new ConcurrentHashMap<>();

	public static final ChecksumType MD5 = new ChecksumType("md5", "MD5");
	public static final ChecksumType SHA_1 = new ChecksumType("sha1", "SHA-1");
	public static final ChecksumType SHA_256 = new ChecksumType("sha256", "SHA-256");
	public static final ChecksumType SHA_512 = new ChecksumType("sha512", "SHA-512");

	protected final String extension;
	protected final String algorithm;

	protected ChecksumType(final String extension, final String algorithm) {
		Objects.requireNonNull(extension, "extension was null");
		Objects.requireNonNull(algorithm, "algorithm was null");
		this.extension = extension;
		this.algorithm = algorithm;
		ChecksumType.KNWON_VALUES.put(extension, this);
	}

	public String extension() {
		return this.extension;
	}

	public String algorithm() {
		return this.algorithm;
	}

	@Override
	public int hashCode() {
		return this.extension.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj;
	}

	public MessageDigest newMessageDigest() throws NoSuchAlgorithmException {
		return MessageDigest.getInstance(this.algorithm);
	}

	public static boolean register(final String extension, final String algorithm) {
		if (KNWON_VALUES.containsKey(extension))
			return false;
		new ChecksumType(extension, algorithm);
		return true;
	}

	public static ChecksumType find(final String extension) {
		Objects.requireNonNull(extension, "extension was null");
		return KNWON_VALUES.get(extension);
	}

	public static Set<String> allExtensions() {
		return Collections.unmodifiableSet(KNWON_VALUES.keySet());
	}

	public static Collection<ChecksumType> allEntries() {
		return Collections.unmodifiableCollection(KNWON_VALUES.values());
	}

	public static MessageDigest newMessageDigestFor(final String extension) throws NoSuchAlgorithmException {
		final ChecksumType type = find(extension);
		if (type == null)
			throw new NoSuchAlgorithmException("Checksum extension <" + extension + "> not recognized!");
		return type.newMessageDigest();
	}

	public static <T> Map<String, T> fill(final Map<String, T> checksumMap, final Function<ChecksumType, T> function) {
		for (Entry<String, ChecksumType> entry : KNWON_VALUES.entrySet()) {
			checksumMap.put(entry.getKey(), function.apply(entry.getValue()));
		}
		return checksumMap;
	}

	public static Map<String, MessageDigest> tryFill(final Map<String, MessageDigest> checksumMap,
			final Consumer<Throwable> handler) {
		for (Entry<String, ChecksumType> entry : KNWON_VALUES.entrySet()) {
			try {
				checksumMap.put(entry.getKey(), entry.getValue()
						.newMessageDigest());
			} catch (NoSuchAlgorithmException ex) {
				if (handler == null)
					continue;
				handler.accept(ex);
			}
		}
		return checksumMap;
	}
}

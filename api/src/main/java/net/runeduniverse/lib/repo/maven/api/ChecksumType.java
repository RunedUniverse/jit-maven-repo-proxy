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

	private static final Map<String, ChecksumType> KNOWN_ALGORITHM = new ConcurrentHashMap<>();
	private static final Map<String, ChecksumType> KNOWN_EXTENSIONS = new ConcurrentHashMap<>();

	public static final ChecksumType MD5 = new ChecksumType("MD5", "md5");
	public static final ChecksumType SHA_1 = new ChecksumType("SHA-1", "sha1");
	public static final ChecksumType SHA_256 = new ChecksumType("SHA-256", "sha256");
	public static final ChecksumType SHA_512 = new ChecksumType("SHA-512", "sha512");

	protected final String algorithm;
	protected final String extension;

	protected ChecksumType(final String algorithm, final String extension) {
		Objects.requireNonNull(algorithm, "algorithm was null");
		Objects.requireNonNull(extension, "extension was null");
		this.algorithm = algorithm;
		this.extension = extension;
		ChecksumType.KNOWN_ALGORITHM.put(algorithm, this);
		ChecksumType.KNOWN_EXTENSIONS.put(extension, this);
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
		if (KNOWN_EXTENSIONS.containsKey(extension))
			return false;
		new ChecksumType(extension, algorithm);
		return true;
	}

	public static ChecksumType findByExtension(final String extension) {
		Objects.requireNonNull(extension, "extension was null");
		return KNOWN_EXTENSIONS.get(extension);
	}

	public static Set<String> allExtensions() {
		return Collections.unmodifiableSet(KNOWN_EXTENSIONS.keySet());
	}

	public static Collection<ChecksumType> allEntries() {
		return Collections.unmodifiableCollection(KNOWN_EXTENSIONS.values());
	}

	public static MessageDigest newMessageDigestFor(final String extension) throws NoSuchAlgorithmException {
		final ChecksumType type = findByExtension(extension);
		if (type == null)
			throw new NoSuchAlgorithmException("Checksum extension <" + extension + "> not recognized!");
		return type.newMessageDigest();
	}

	public static <T> Map<String, T> fillWithExtensions(final Map<String, T> checksumMap,
			final Function<ChecksumType, T> function) {
		for (Entry<String, ChecksumType> entry : KNOWN_EXTENSIONS.entrySet()) {
			checksumMap.put(entry.getKey(), function.apply(entry.getValue()));
		}
		return checksumMap;
	}

	public static Map<String, MessageDigest> tryFillWithAlgorithm(final Map<String, MessageDigest> checksumMap,
			final Consumer<Throwable> handler) {
		for (ChecksumType type : KNOWN_EXTENSIONS.values()) {
			try {
				checksumMap.put(type.algorithm(), type.newMessageDigest());
			} catch (NoSuchAlgorithmException ex) {
				if (handler == null)
					continue;
				handler.accept(ex);
			}
		}
		return checksumMap;
	}
}

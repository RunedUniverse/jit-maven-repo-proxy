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
package net.runeduniverse.lib.repo.maven.http.data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Map;

public class FileProcessor extends AContentProcessor<Void> {

	protected final OutputStream stream;
	protected final Map<String, MessageDigest> checksums;

	public FileProcessor(final Path path, final Map<String, MessageDigest> checksums) throws IOException {
		super();
		this.stream = Files.newOutputStream(path, //
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		this.checksums = checksums;
	}

	@Override
	public void process(final byte[] bytes) {
		if (hasCompleted() || bytes == null)
			return;
		try {
			this.stream.write(bytes);
			// update all checksum calculators
			if (this.checksums != null) {
				for (MessageDigest checksum : this.checksums.values()) {
					checksum.update(bytes);
				}
			}
		} catch (IOException e) {
			this.future.completeExceptionally(e);
		}
	}

	@Override
	public void complete() {
		try {
			this.stream.close();
			this.future.complete(null);
		} catch (IOException e) {
			this.future.completeExceptionally(e);
		}
	}
}

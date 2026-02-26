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
package net.runeduniverse.lib.repo.maven.data;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Map;

public class FileProcessor extends AContentProcessor<Void> {

	protected final Path resultPath;
	protected final Path partPath;
	protected final Map<String, MessageDigest> checksums;
	protected OutputStream stream = null;

	public FileProcessor(final Path path, final Map<String, MessageDigest> checksums) {
		super();
		this.resultPath = path;
		this.partPath = this.resultPath.resolveSibling(this.resultPath.getFileName()
				.toString() + ".part");
		this.checksums = checksums;
	}

	protected OutputStream stream() throws IOException {
		synchronized (this.future) {
			if (this.stream != null)
				return this.stream;

			Files.createDirectories(this.resultPath.getParent());
			this.stream = Files.newOutputStream(this.partPath, //
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

			return this.stream;
		}
	}

	@Override
	public void reset() {
		synchronized (this.future) {
			if (isDone())
				return;
			reset(true);
		}
	}

	protected void reset(boolean failFuture) {
		synchronized (this.future) {
			try {
				if (this.stream != null) {
					// if stream is open -> deal with it
					this.stream.close();
					this.stream = null;
				}
			} catch (IOException e) {
				if (failFuture)
					this.future.completeExceptionally(e);
			}
			// even when the stream has errors try to remove the .part file
			try {
				Files.deleteIfExists(this.partPath);
			} catch (IOException e) {
				if (failFuture)
					this.future.completeExceptionally(e);
			}
		}
	}

	@Override
	public void process(final byte[] bytes) {
		synchronized (this.future) {
			if (isDone() || bytes == null)
				return;
			try {
				stream().write(bytes);
				// update all checksum calculators
				if (this.checksums != null) {
					for (MessageDigest checksum : this.checksums.values()) {
						checksum.update(bytes);
					}
				}
			} catch (IOException e) {
				completeExceptionally(e);
			}
		}
	}

	@Override
	public void complete() {
		synchronized (this.future) {
			if (isDone())
				return;
			try {
				if (this.stream != null) {
					this.stream.flush();
					this.stream.close();
					Files.move(this.partPath, this.resultPath, //
							StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
				this.future.complete(null);
			} catch (IOException e) {
				completeExceptionally(e);
			}
		}
	}

	@Override
	public void completeExceptionally(final Throwable ex) {
		synchronized (this.future) {
			if (isDone())
				return;
			reset(false);
			super.completeExceptionally(ex);
		}
	}

	@Override
	public void cancel(final boolean mayInterruptIfRunning) {
		synchronized (this.future) {
			if (isDone())
				return;
			reset(false);
			super.cancel(mayInterruptIfRunning);
		}
	}
}

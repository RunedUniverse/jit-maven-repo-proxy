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
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {

	protected final ByteBuffer buffer;

	public ByteBufferInputStream(final ByteBuffer buffer) {
		this.buffer = buffer;
	}

	@Override
	public int available() {
		return this.buffer.remaining();
	}

	@Override
	public int read() throws IOException {
		if (this.buffer.hasRemaining())
			return this.buffer.get() & 0xFF;
		return -1;
	}

	@Override
	public int read(byte[] bytes, int off, int len) throws IOException {
		if (!this.buffer.hasRemaining())
			return -1;
		len = Math.min(len, this.buffer.remaining());
		this.buffer.get(bytes, off, len);
		return len;
	}
}

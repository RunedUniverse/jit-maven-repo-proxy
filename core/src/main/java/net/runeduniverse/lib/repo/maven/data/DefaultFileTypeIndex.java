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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.runeduniverse.lib.repo.maven.api.FileContentType;
import net.runeduniverse.lib.repo.maven.api.FileType;
import net.runeduniverse.lib.repo.maven.api.FileTypeIndex;

import static net.runeduniverse.lib.repo.maven.api.FileType.META_HTTP_CONTENT_TYPE_HEADER;

public class DefaultFileTypeIndex implements FileTypeIndex {

	protected final Map<String, DefaultFileType> byExtMap = new ConcurrentHashMap<>();

	public DefaultFileTypeIndex() {
		loadDefaults();
		loadHttpDefaults();
	}

	@Override
	public FileType getByExtension(final String extension) {
		return this.byExtMap.getOrDefault(extension, new DefaultFileType(extension));
	}

	@Override
	public void forEach(Consumer<? super FileType> action) {
		this.byExtMap.values()
				.forEach(action);
	}

	public DefaultFileType edit(final String extension) {
		return this.byExtMap.computeIfAbsent(extension, DefaultFileType::new);
	}

	public void putMetadataIfAbsent(final String extension, final String key, final Object obj) {
		edit(extension).metadata()
				.putIfAbsent(key, obj);
	}

	protected void loadDefaults() {
		edit("pom").setContentType(FileContentType.POM);
		// signatures
		edit("asc").setContentType(FileContentType.SIGNATURE);
		// hashes
		edit("md5").setContentType(FileContentType.CHECKSUM);
		edit("sha1").setContentType(FileContentType.CHECKSUM);
		edit("sha256").setContentType(FileContentType.CHECKSUM);
		edit("sha512").setContentType(FileContentType.CHECKSUM);
		// all others fall back to DATA by default
	}

	protected void loadHttpDefaults() {
		// xml files
		putMetadataIfAbsent("xml", META_HTTP_CONTENT_TYPE_HEADER, "application/xml");
		putMetadataIfAbsent("pom", META_HTTP_CONTENT_TYPE_HEADER, "application/xml");
		// signatures
		putMetadataIfAbsent("asc", META_HTTP_CONTENT_TYPE_HEADER, "application/pgp-signature");
		// hashes as hex text
		putMetadataIfAbsent("md5", META_HTTP_CONTENT_TYPE_HEADER, "text/plain; charset=UTF-8");
		putMetadataIfAbsent("sha1", META_HTTP_CONTENT_TYPE_HEADER, "text/plain; charset=UTF-8");
		putMetadataIfAbsent("sha256", META_HTTP_CONTENT_TYPE_HEADER, "text/plain; charset=UTF-8");
		putMetadataIfAbsent("sha512", META_HTTP_CONTENT_TYPE_HEADER, "text/plain; charset=UTF-8");
		// all others fall back to "application/octet-stream" by default
	}
}

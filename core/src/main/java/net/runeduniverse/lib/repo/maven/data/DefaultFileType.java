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

import net.runeduniverse.lib.repo.maven.api.FileContentType;
import net.runeduniverse.lib.repo.maven.api.FileType;

public class DefaultFileType implements FileType {

	protected final Map<String, Object> metadata = new ConcurrentHashMap<>();

	protected final String extension;
	protected FileContentType contentType;

	public DefaultFileType(final String extension) {
		this(extension, FileContentType.DATA);
	}

	public DefaultFileType(final String extension, final FileContentType contentType) {
		this.extension = extension;
		this.contentType = contentType;
	}

	@Override
	public String extension() {
		return this.extension;
	}

	@Override
	public FileContentType contentType() {
		return this.contentType;
	}

	@Override
	public Map<String, Object> metadata() {
		return this.metadata;
	}

	public void setContentType(final FileContentType contentType) {
		this.contentType = contentType;
	}
}

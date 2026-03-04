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

import java.util.Map;

public interface FileType {

	public static final String META_HTTP_CONTENT_TYPE_HEADER = "http.content.header";

	public String extension();

	public FileContentType contentType();

	public Map<String, Object> metadata();

	public default <V> V getMetadata(final Class<V> type, final String key) {
		return getMetadata(type, key, null);
	}

	@SuppressWarnings("unchecked")
	public default <V> V getMetadata(final Class<V> type, final String key, final V defaultValue) {
		final Object value = metadata().get(key);
		if (type.isInstance(value))
			return (V) value;
		return defaultValue;
	}

	public default void putMetadata(final String key, final Object value) {
		metadata().put(key, value);
	}
}

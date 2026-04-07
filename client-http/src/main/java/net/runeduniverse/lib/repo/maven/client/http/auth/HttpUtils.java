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
package net.runeduniverse.lib.repo.maven.client.http.auth;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HttpUtils {

	public static List<Section> parseAuthHeaderData(final String value) {

		// handle values like:
		// WWW-Authenticate: Negotiate
		// WWW-Authenticate: Bearer realm="repo"
		// WWW-Authenticate: Digest realm="repo", nonce="abc", qop="auth"
		// WWW-Authenticate: Basic realm="repo"

		final List<Section> sections = new LinkedList<>();

		StringBuilder buffer = null;
		StringBuilder word = null;
		Section section = null;
		String key = null;
		boolean escaped = false;
		boolean quoted = false;
		boolean kvend = true;
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);

			if (quoted) {
				if (escaped == false) {
					if (c == '\\') {
						escaped = true;
						continue;
					}
					if (c == '"') {
						// defines closing of quotes
						quoted = false;
						// after quotes close the word is set
						// NOTE: no values in quotes => EMPTY String
						word = buffer == null ? new StringBuilder() : buffer;
						buffer = null;
						continue;
					}
				}
			} else {
				if (c == '"') {
					// defines opening of quotes
					quoted = true;
					kvend = false;
					continue;
				}
				// check for word termination symbol
				boolean nonCharDetected = false;
				if (nonCharDetected = !isTokenChar(c)) {
					if (buffer != null) {
						word = buffer;
						buffer = null;
					}
				}
				// check for special symbols
				if (c == '=') {
					// defines end of <key>
					key = word == null ? null : word.toString();
					kvend = false;
				} else if (c == ',') {
					// defines end of <key="value">
					if (word != null) {
						if (section != null) {
							// NOTE: if no key is defined, null is used
							section.entries()
									.put(key == null ? null : key.toLowerCase(Locale.ROOT), word.toString());
						}

					}
					kvend = true;
					key = null;
					word = null;
				} else if (nonCharDetected) {
					// at this point the symbol is expected to be a whitespace symbol
					if (kvend) {
						if (word != null) {
							// defines new section head
							section = new Section(word.toString());
							sections.add(section);
							word = null;
							kvend = false;
						}
					}
				}
				// all non valid chars are skipped!
				if (nonCharDetected)
					continue;
			}

			// any char could be escaped
			escaped = false;
			if (buffer == null)
				buffer = new StringBuilder();
			buffer.append(c);
		}

		if (section != null && (buffer != null || word != null)) {
			if (buffer != null) {
				word = buffer;
				buffer = null;
			}
			if (!(key == null && word == null)) {
				// NOTE: if no key is defined, null is used
				section.entries()
						.put(key == null ? null : key.toLowerCase(Locale.ROOT), word.toString());
			}
		}

		return sections;
	}

	public static boolean isTokenChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
				|| "!#$%&'*+-.^_`|~".indexOf(c) != -1;
	}

	public static class Section {

		protected final Map<String, String> entries = new LinkedHashMap<>();
		protected final String header;

		public Section(final String header) {
			this.header = header;
		}

		public String header() {
			return this.header;
		}

		public Map<String, String> entries() {
			return this.entries;
		}
	}
}

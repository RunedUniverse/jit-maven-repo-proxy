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

import java.util.ArrayList;
import java.util.List;

import net.runeduniverse.lib.repo.maven.api.BasicCredentials;
import net.runeduniverse.lib.repo.maven.api.Credentials;
import net.runeduniverse.lib.repo.maven.api.TokenCredentials;

public class DefaultAuthStateProvider implements AuthStateProvider {

	protected final Credentials credentials;
	protected final List<String> supportedAuthSections;

	public DefaultAuthStateProvider(final Credentials credentials) {
		this.credentials = credentials;
		this.supportedAuthSections = selectSupportedAuthSections();
	}

	protected List<String> selectSupportedAuthSections() {
		final List<String> col = new ArrayList<>();
		if (credentials instanceof TokenCredentials) {
			col.add("bearer");
		}
		if (credentials instanceof BasicCredentials) {
			col.add("digest");
			col.add("basic");
		}
		return col;
	}

	@Override
	public List<String> supportedAuthSections() {
		return this.supportedAuthSections;
	}

	@Override
	public AuthState forHttpAuthenticate(final AuthHeaderSection section) {
		// check if the section is supported
		if (!this.supportedAuthSections.contains(section.type()))
			return null;
		// factory
		switch (section.type()) {
		case "bearer":
			return new BearerAuthState((TokenCredentials) this.credentials);
		case "digest":
			return new DigestAuthState((BasicCredentials) this.credentials, section);
		case "basic":
			return new BasicAuthState((BasicCredentials) this.credentials);
		}
		return null;
	}
}

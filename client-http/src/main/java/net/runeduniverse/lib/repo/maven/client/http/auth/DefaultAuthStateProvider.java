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

import net.runeduniverse.lib.repo.maven.api.BasicRepoCredentials;
import net.runeduniverse.lib.repo.maven.api.RepoCredentials;
import net.runeduniverse.lib.repo.maven.api.TokenRepoCredentials;
import net.runeduniverse.lib.repo.maven.client.http.auth.HttpUtils.Section;

public class DefaultAuthStateProvider implements AuthStateProvider {

	protected final RepoCredentials credentials;
	protected final List<String> supportedAuthSections;

	public DefaultAuthStateProvider(final RepoCredentials credentials) {
		this.credentials = credentials;
		this.supportedAuthSections = selectSupportedAuthSections();
	}

	protected List<String> selectSupportedAuthSections() {
		final List<String> col = new ArrayList<>();
		if (credentials instanceof TokenRepoCredentials) {
			col.add("bearer");
		}
		if (credentials instanceof BasicRepoCredentials) {
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
	public AuthState forHttpAuthenticate(final Section section) {
		// check if the section is supported
		if (!this.supportedAuthSections.contains(section.header()))
			return null;
		// factory
		switch (section.header()) {
		case "bearer":
			return new BearerAuthState((TokenRepoCredentials) this.credentials);
		case "digest":
			// TODO implement
			return null;
		case "basic":
			return new BasicAuthState((BasicRepoCredentials) this.credentials);
		}
		return null;
	}
}

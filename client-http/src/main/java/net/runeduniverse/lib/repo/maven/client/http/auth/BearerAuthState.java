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

import java.util.List;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AsciiString;
import net.runeduniverse.lib.repo.maven.api.TokenCredentials;

public class BearerAuthState implements AuthState {

	protected final TokenCredentials creds;

	public BearerAuthState(final TokenCredentials creds) {
		this.creds = creds;
	}

	@Override
	public String authType() {
		return "bearer";
	}

	@Override
	public boolean nextAuthorizationHeader(final HttpRequest request, final AsciiString header) {
		request.headers()
				.add(header, "Bearer " + this.creds.getToken());
		return true;
	}

	@Override
	public boolean retryOnRejection(final List<AuthHeaderSection> sections) {
		final AuthHeaderSection response = sections.stream()
				.filter(s -> authType().equals(s.type()))
				.findFirst()
				.orElse(null);
		return response != null;
	}
}

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
package net.runeduniverse.lib.repo.maven.client.http;

import io.netty.util.AttributeKey;
import net.runeduniverse.lib.repo.maven.client.http.auth.AuthState;
import net.runeduniverse.lib.repo.maven.client.http.auth.AuthStateProvider;

public class HttpClientUtils {

	public static final AttributeKey<HttpDataRequest> ATTKEY_HTTP_DATA_REQUEST = AttributeKey
			.valueOf(HttpDataRequest.class.getCanonicalName());
	public static final AttributeKey<AuthStateProvider> ATTKEY_HTTP_AUTH_PROVIDER = AttributeKey
			.valueOf(AuthStateProvider.class.getCanonicalName());
	public static final AttributeKey<AuthStateProvider> ATTKEY_HTTP_PROXY_AUTH_PROVIDER = AttributeKey
			.valueOf(AuthStateProvider.class.getCanonicalName() + "»proxy");
	public static final AttributeKey<AuthState> ATTKEY_HTTP_AUTH_STATE = AttributeKey
			.valueOf(AuthState.class.getCanonicalName());
	public static final AttributeKey<AuthState> ATTKEY_HTTP_PROXY_AUTH_STATE = AttributeKey
			.valueOf(AuthState.class.getCanonicalName() + "»proxy");

}

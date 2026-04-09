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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;
import net.runeduniverse.lib.repo.maven.client.http.HttpClientUtils;
import net.runeduniverse.lib.repo.maven.client.http.HttpDataRequest;
import net.runeduniverse.lib.repo.maven.error.RepoException;
import net.runeduniverse.lib.repo.maven.error.UnauthorizedArtifactException;

public class AuthHandler extends ChannelDuplexHandler {

	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
		if (msg instanceof FullHttpResponse) {
			final FullHttpResponse response = (FullHttpResponse) msg;
			final int code = response.status()
					.code();

			if (code == 401 || code == 407) {
				handleAuthChallenge(ctx, response, code == 407);
				// consume response, will retry
				return;
			}
		}
		ctx.fireChannelRead(msg);
	}

	private void handleAuthChallenge(final ChannelHandlerContext ctx, final FullHttpResponse response,
			final boolean proxy) {
		final HttpDataRequest dataRequest = ctx.channel()
				.attr(HttpClientUtils.ATTKEY_HTTP_DATA_REQUEST)
				.get();

		AuthState state = ctx.channel()
				.attr(proxy ? HttpClientUtils.ATTKEY_HTTP_PROXY_AUTH_STATE : HttpClientUtils.ATTKEY_HTTP_AUTH_STATE)
				.get();
		// generate a new AuthState when ...
		// ... proxy requires it
		// ... the reposerver requires it
		// ... but not if the reposerver was redirected and now requires authentication
		// -> prevents credential leakage!
		if (proxy || !dataRequest.hasRepoChanged()) {
			final AsciiString authHeader = proxy ? //
					HttpHeaderNames.PROXY_AUTHENTICATE : HttpHeaderNames.WWW_AUTHENTICATE;
			// locate authentication provider
			final AuthStateProvider authStateProvider = ctx.channel()
					.attr(proxy ? HttpClientUtils.ATTKEY_HTTP_PROXY_AUTH_PROVIDER
							: HttpClientUtils.ATTKEY_HTTP_AUTH_PROVIDER)
					.get();
			if (authStateProvider == null) {
				dataRequest.processor()
						.completeExceptionally(
								new RepoException("HTTP: No " + (proxy ? "proxy" : "repo") + "-auth provider found!"));
				ctx.close();
				return;
			}
			// parse header sections
			final List<AuthHeaderSection> sections = new LinkedList<>();
			for (String headerData : response.headers()
					.getAll(authHeader)) {
				sections.addAll(AuthHeaderSection.parseAuthHeaderData(headerData));
			}

			// check if the state can be recovered
			if (state == null || !state.retryOnRejection(sections)) {
				// try supported sections in the provided order
				Iterator<AuthHeaderSection> sectionIter;
				headerLoop: for (String sectionHeader : authStateProvider.supportedAuthSections()) {
					sectionIter = sections.stream()
							.filter(s -> sectionHeader.equals(s.type()))
							.iterator();
					while (sectionIter.hasNext()) {
						state = authStateProvider.forHttpAuthenticate(sectionIter.next());
						if (state != null)
							break headerLoop;
					}
				}
			}
		}
		if (state == null) {
			dataRequest.processor()
					.completeExceptionally(new UnauthorizedArtifactException());
			ctx.close();
			return;
		}

		// store state
		ctx.channel()
				.attr(proxy ? HttpClientUtils.ATTKEY_HTTP_PROXY_AUTH_STATE : HttpClientUtils.ATTKEY_HTTP_AUTH_STATE)
				.set(state);

		// retry the request
		final FullHttpRequest retry = dataRequest.asHttpRequest();
		addAuthHeader(ctx, retry);
		ctx.writeAndFlush(retry);
	}

	@Override
	public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise)
			throws Exception {
		if (msg instanceof FullHttpRequest) {
			final FullHttpRequest request = (FullHttpRequest) msg;
			addAuthHeader(ctx, request);
		}
		super.write(ctx, msg, promise);
	}

	protected void addAuthHeader(final ChannelHandlerContext ctx, final FullHttpRequest request) {
		// inject preemptive auth if we have credentials
		// -- REPO AUTH --
		AuthState authState = ctx.channel()
				.attr(HttpClientUtils.ATTKEY_HTTP_AUTH_STATE)
				.get();
		if (authState != null) {
			authState.nextAuthorizationHeader(request, HttpHeaderNames.AUTHORIZATION);
		}
		// -- PROXY AUTH --
		authState = ctx.channel()
				.attr(HttpClientUtils.ATTKEY_HTTP_PROXY_AUTH_STATE)
				.get();
		if (authState != null) {
			authState.nextAuthorizationHeader(request, HttpHeaderNames.PROXY_AUTHORIZATION);
		}
	}
}

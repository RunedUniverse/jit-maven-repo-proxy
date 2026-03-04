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
package net.runeduniverse.lib.repo.maven.server.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class HttpRepoRoutingHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	protected final Function<String, MavenRepositoryInstance> repoProvider;

	public HttpRepoRoutingHandler(final Function<String, MavenRepositoryInstance> repoProvider) {
		this.repoProvider = repoProvider;
	}

	@Override
	protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
		final QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
		final LinkedList<String> pathFragments = Arrays.stream(decoder.path()
				.split("/"))
				.collect(Collectors.toCollection(LinkedList::new));
		if (pathFragments.size() < 2) {
			HttpServerUtils.sendError(ctx, request, BAD_REQUEST);
			return;
		}

		final MavenRepositoryInstance repoInst = findRepo(pathFragments);
		if (repoInst == null) {
			HttpServerUtils.sendError(ctx, request, BAD_REQUEST);
			return;
		}

		// note: findRepo() removed used path fragments
		request.setUri(rebuildUri(decoder, String.join("/", pathFragments)));
		ctx.channel()
				.attr(HttpServerUtils.ATTKEY_ARTIFACT_PROVIDER)
				.set(repoInst);

		ctx.fireChannelRead(request.retain());
	}

	protected MavenRepositoryInstance findRepo(final List<String> pathFragments) {
		final Iterator<String> seek = pathFragments.iterator();
		// void first (it's empty) -> uri starts with /
		seek.next();
		String path = null;
		MavenRepositoryInstance provider = null;
		while (provider == null && seek.hasNext()) {
			final String fragment = seek.next();
			seek.remove();
			path = path == null ? fragment : path + '/' + fragment;
			provider = this.repoProvider.apply(path);
		}
		return provider;
	}

	protected String rebuildUri(final QueryStringDecoder decoder, final String newPath) {
		final String rawUri = decoder.uri();
		final int rawUriLen = rawUri.length();
		// sadly the QueryStringDecoder does not expose the query splitter or it's
		// index, so we have to calculate backwards

		final String rawQuery = decoder.rawQuery();
		final int rawQueryLen = rawQuery.length();

		if (0 < rawQueryLen) {
			final char splitter = rawUri.charAt(rawUriLen - rawQueryLen - 1);
			return newPath + splitter + rawQuery;
		}
		return newPath;
	}
}

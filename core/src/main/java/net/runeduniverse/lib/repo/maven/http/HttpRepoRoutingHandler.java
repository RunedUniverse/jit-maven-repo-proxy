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
package net.runeduniverse.lib.repo.maven.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

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
		if (pathFragments.isEmpty()) {
			HttpRepoUtils.sendError(ctx, request, BAD_REQUEST);
			return;
		}

		// NOTE: repos with path lengths >1 are currently not supported!

		final String repoPath = StringUtils.trimToNull(pathFragments.pollFirst());
		final MavenRepositoryInstance repoInst = this.repoProvider.apply(repoPath);

		if (repoInst == null) {
			HttpRepoUtils.sendError(ctx, request, BAD_REQUEST);
			return;
		}

		request.setUri(String.join("/", pathFragments));
		ctx.channel()
				.attr(HttpRepoUtils.ATTKEY_REPO_INSTANCE)
				.set(repoInst);

		ctx.fireChannelRead(request.retain());
	}
}

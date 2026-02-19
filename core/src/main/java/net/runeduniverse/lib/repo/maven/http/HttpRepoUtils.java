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

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_0;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;

public class HttpRepoUtils {

	public static final AttributeKey<ArtifactProvider> ATTKEY_ARTIFACT_PROVIDER = AttributeKey
			.valueOf(ArtifactProvider.class.getCanonicalName());

	public static void sendError(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final HttpResponseStatus status) {
		final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
				Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
		response.headers()
				.set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		sendAndCleanupConnection(ctx, request, response);
	}

	public static void sendAndCleanupConnection(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final FullHttpResponse response) {
		final boolean keepAlive = HttpUtil.isKeepAlive(request);
		HttpUtil.setContentLength(response, response.content()
				.readableBytes());
		if (!keepAlive) {
			// We're going to close the connection as soon as the response is sent,
			// so we should also make it clear for the client
			response.headers()
					.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (request.protocolVersion()
				.equals(HTTP_1_0)) {
			response.headers()
					.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		final ChannelFuture flushPromise = ctx.writeAndFlush(response);

		if (!keepAlive) {
			// Close the connection as soon as the response is sent
			flushPromise.addListener(ChannelFutureListener.CLOSE);
		}
	}
}

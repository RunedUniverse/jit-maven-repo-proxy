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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import net.runeduniverse.lib.repo.maven.data.AContentProcessor;
import net.runeduniverse.lib.repo.maven.error.ForbiddenArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;
import net.runeduniverse.lib.repo.maven.error.UnauthorizedArtifactException;

public class HttpRepoClientHandler extends SimpleChannelInboundHandler<HttpObject> {

	@Override
	protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) throws Exception {
		final HttpDataRequest dataRequest = ctx.channel()
				.attr(HttpClientUtils.ATTKEY_HTTP_DATA_REQUEST)
				.get();
		final AContentProcessor<?> processor = dataRequest.processor();

		if (processor.isDone()) {
			// already done or canceled
			ctx.close();
			return;
		}

		if (msg instanceof HttpResponse) {
			readResponse(ctx, (HttpResponse) msg, dataRequest, processor);
		}

		if (msg instanceof HttpContent) {
			readContent(ctx, (HttpContent) msg, processor);
		}
	}

	protected void readResponse(final ChannelHandlerContext ctx, final HttpResponse response,
			final HttpDataRequest dataRequest, final AContentProcessor<?> processor) {
		final int statusCode = response.status()
				.code();
		// TODO add propper logging
		System.out.println("CLIENT | " + statusCode + " » " + dataRequest.uri());
		switch (statusCode) {
		case 200: // [ OK ]
		default:
			break;
		case 204: // [ No Content ]
			processor.complete();
			ctx.close();
			return;
		// [ Redirects ]
		case 301:
		case 302:
		case 307:
		case 308:
			dataRequest.redirect(response.headers()
					.get(HttpHeaderNames.LOCATION));
			ctx.close();
			return;
		case 401: // [ Unauthorized ]
			processor.completeExceptionally(new UnauthorizedArtifactException());
			ctx.close();
			return;
		case 403: // [ Forbidden ]
			processor.completeExceptionally(new ForbiddenArtifactException());
			ctx.close();
			return;
		case 404: // [ Not Found ]
			processor.completeExceptionally(new NotFoundArtifactException());
			ctx.close();
			return;
		}
		if (500 <= statusCode) {
			ctx.close();
			// -> retry -> it eventually throws RepoException
		}
		if (400 <= statusCode) {
			// -> I got invalid request data ...
			processor.completeExceptionally(new InvalidArtifactException());
			ctx.close();
		}

		// process checksum headers if available
		final HttpHeaders headers = response.headers();
		// NOTE: headers may be written a lot of funny ways -> so we normalize all to
		// lower case!
		final Map<String, String> normNames = new HashMap<>();
		for (String name : headers.names()) {
			normNames.put(name.toLowerCase(), name);
		}

		for (Entry<String, AContentProcessor<?>> entry : dataRequest.subProcessorMap()
				.entrySet()) {
			final String name = normNames.get("x-checksum-" + entry.getKey());
			if (name == null)
				continue;
			final String value = StringUtils.trimToNull(headers.get(name));
			final AContentProcessor<?> checksumProcessor = entry.getValue();
			if (checksumProcessor.isDone() || value == null)
				continue;
			checksumProcessor.process(value.getBytes());
			checksumProcessor.complete();
		}
		return;
	}

	protected void readContent(final ChannelHandlerContext ctx, final HttpContent httpContent,
			final AContentProcessor<?> processor) {
		final ByteBuf buf = httpContent.content();

		if (buf.isReadable()) {
			final byte[] bytes = new byte[buf.readableBytes()];

			buf.readBytes(bytes);
			processor.process(bytes);
		}

		if (httpContent instanceof LastHttpContent) {
			processor.complete();
			ctx.close();
		}
	}
}

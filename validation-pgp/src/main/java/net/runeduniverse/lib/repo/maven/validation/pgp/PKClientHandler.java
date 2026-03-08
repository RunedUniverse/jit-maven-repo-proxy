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
package net.runeduniverse.lib.repo.maven.validation.pgp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import net.runeduniverse.lib.repo.maven.data.AContentProcessor;

public class PKClientHandler extends SimpleChannelInboundHandler<HttpObject> {

	@Override
	protected void channelRead0(final ChannelHandlerContext ctx, final HttpObject msg) throws Exception {
		final KeyDataRequest dataRequest = ctx.channel()
				.attr(KeyDataRequest.ATTKEY_KEY_DATA_REQUEST)
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
			final KeyDataRequest dataRequest, final AContentProcessor<?> processor) {
		final int statusCode = response.status()
				.code();
		// TODO add propper logging
		System.out.println("PK-CLIENT | " + statusCode + " » " + dataRequest.uri());
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
			processor.completeExceptionally(new RuntimeException("HTTP-CODE-401"));
			ctx.close();
			return;
		case 403: // [ Forbidden ]
			processor.completeExceptionally(new RuntimeException("HTTP-CODE-403"));
			ctx.close();
			return;
		case 404: // [ Not Found ]
			processor.completeExceptionally(new RuntimeException("HTTP-CODE-404"));
			ctx.close();
			return;
		}
		if (500 <= statusCode) {
			ctx.close();
			// -> retry -> it eventually throws RedirectException
		}
		if (400 <= statusCode) {
			// -> I got invalid request data ...
			processor.completeExceptionally(new RuntimeException("HTTP-CODE-" + statusCode));
			ctx.close();
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

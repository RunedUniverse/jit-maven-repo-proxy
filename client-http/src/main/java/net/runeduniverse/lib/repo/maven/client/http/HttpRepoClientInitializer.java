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

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.SslContext;
import net.runeduniverse.lib.repo.maven.client.http.auth.AuthHandler;
import net.runeduniverse.lib.repo.maven.error.RepoException;

public class HttpRepoClientInitializer extends ChannelInitializer<SocketChannel> {

	protected final SslContext sslContext;

	public HttpRepoClientInitializer(final SslContext sslContext) {
		this.sslContext = sslContext;
	}

	@Override
	protected void initChannel(final SocketChannel ch) {
		final ChannelPipeline pipeline = ch.pipeline();
		final HttpDataRequest dataRequest = ch.attr(HttpClientUtils.ATTKEY_HTTP_DATA_REQUEST)
				.get();

		if (dataRequest.withSSL()) {
			if (this.sslContext == null) {
				dataRequest.processor()
						.completeExceptionally(new RepoException("SslContext was not initialized!"));
				ch.close();
				return;
			}
			pipeline.addLast(sslContext.newHandler(ch.alloc(), dataRequest.getHost(), dataRequest.getPort()));
		}
		pipeline.addLast(new HttpClientCodec());
		pipeline.addLast(new AuthHandler());
		pipeline.addLast(new HttpRepoClientHandler());
	}
}

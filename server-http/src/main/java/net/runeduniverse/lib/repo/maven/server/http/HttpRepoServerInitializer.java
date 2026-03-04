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

import java.util.function.Function;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import net.runeduniverse.lib.repo.maven.api.FileTypeIndex;
import net.runeduniverse.lib.repo.maven.api.MavenRepositoryInstance;

public class HttpRepoServerInitializer extends ChannelInitializer<SocketChannel> {

	protected final Function<String, MavenRepositoryInstance> repoProvider;
	protected final FileTypeIndex typeIndex;

	public HttpRepoServerInitializer(final Function<String, MavenRepositoryInstance> repoProvider,
			final FileTypeIndex typeIndex) {
		this.repoProvider = repoProvider;
		this.typeIndex = typeIndex;
	}

	@Override
	protected void initChannel(final SocketChannel ch) throws Exception {
		final ChannelPipeline pipeline = ch.pipeline();

		pipeline.addLast(new HttpServerCodec());
		pipeline.addLast(new HttpObjectAggregator(65536));
		pipeline.addLast(new ChunkedWriteHandler());
		pipeline.addLast(new HttpRepoDecodeValidationHandler());
		pipeline.addLast(new HttpRepoRoutingHandler(this.repoProvider));
		pipeline.addLast(new HttpRepoServerHandler(this.typeIndex));
	}
}

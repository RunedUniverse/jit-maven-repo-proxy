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

import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLException;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public class HttpSourceClient implements RepositorySourceClient {

	protected final HttpSource source;
	protected final EventLoopGroup loopGroup;
	protected final int maxRedirects;
	protected final int maxRetries;

	protected MetadataValidator metadataValidator;
	protected ArtifactValidator artifactValidator;

	protected Bootstrap bootstrap = null;

	protected HttpSourceClient(final HttpSource source, final int maxRedirects, final int maxRetries) {
		this(source, new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()), maxRedirects, maxRetries);
	}

	protected HttpSourceClient(final HttpSource source, final EventLoopGroup loopGroup, final int maxRedirects,
			final int maxRetries) {
		this.source = source;
		this.loopGroup = loopGroup;
		this.maxRedirects = maxRedirects;
		this.maxRetries = maxRetries;
	}

	@Override
	public RepositorySource getSource() {
		return this.source;
	}

	@Override
	public HttpSourceClient setValidator(final MetadataValidator validator) {
		this.metadataValidator = validator;
		return this;
	}

	@Override
	public HttpSourceClient setValidator(final ArtifactValidator validator) {
		this.artifactValidator = validator;
		return this;
	}

	protected synchronized Bootstrap bootstrap() {
		if (this.bootstrap != null)
			return this.bootstrap;

		SslContext sslCtx = null;
		try {
			sslCtx = SslContextBuilder.forClient()
					.build();
		} catch (SSLException ignored) {
			ignored.printStackTrace();
		}

		return this.bootstrap = new Bootstrap()//
				.group(this.loopGroup)
				.channel(NioSocketChannel.class)
				.handler(new HttpRepoClientInitializer(sslCtx))
				.attr(HttpClientUtils.ATTKEY_HTTP_AUTH_PROVIDER, this.source.getRepoAuthStateProvider())
				.attr(HttpClientUtils.ATTKEY_HTTP_PROXY_AUTH_PROVIDER, this.source.getProxyAuthStateProvider());
	}

	@Override
	public CompletableFuture<ArtifactMetadata> getMetadata(final ArtifactCoordinates coords) {
		final HttpArtifactMetadata artifactMetadata = new HttpArtifactMetadata(this.source.getRepoUri(),
				this.maxRedirects, coords);

		for (HttpDataRequest dataRequest : artifactMetadata.getDataRequests()) {
			execRequest(dataRequest);
		}

		return artifactMetadata.asFuture();
	}

	@Override
	public CompletableFuture<? extends ArtifactData> getArtifact(final ArtifactDataCoordinates coords) {
		return getArtifact(this, coords);
	}

	@Override
	public CompletableFuture<? extends ArtifactData> getArtifact(final ArtifactProvider providerProxy,
			final ArtifactDataCoordinates coords) {
		final HttpArtifactData artifactData;
		if (coords.isPOM()) {
			artifactData = new HttpArtifactPOM(this.source.getLocalRepoPath(), this.source.getRepoUri(),
					this.maxRedirects, providerProxy, coords.toPomCoordinates());
		} else {
			artifactData = new HttpArtifactData(this.source.getLocalRepoPath(), this.source.getRepoUri(),
					this.maxRedirects, providerProxy, coords);
		}

		applyArtifactValidator(artifactData);
		execRequest(artifactData);

		return artifactData.asFuture();
	}

	protected void applyArtifactValidator(final HttpArtifactData artifactData) {
		artifactData.setValidator(this.artifactValidator);
	}

	protected void execRequest(final HttpArtifactData artifactData) {
		for (HttpDataRequest dataRequest : artifactData.getDataRequests()) {
			execRequest(dataRequest);
		}
	}

	@Override
	public void shutdownGracefully() {
		this.loopGroup.shutdownGracefully();
	}

	protected void completeSubRequests(final HttpDataRequest parentDataRequest) {
		// if the parent failed we don't go deeper
		final boolean abort = parentDataRequest.processor()
				.isCompletedExceptionally();

		for (HttpDataRequest dataRequest : parentDataRequest.remainingSubRequests()) {
			if (abort) {
				// this might be redundant
				dataRequest.processor()
						.cancel(true);
			} else
				execRequest(dataRequest);
		}
	}

	protected void execRequest(final HttpDataRequest dataRequest) {
		final Bootstrap bootstrap = bootstrap().clone();
		bootstrap.attr(HttpClientUtils.ATTKEY_HTTP_DATA_REQUEST, dataRequest);

		bootstrap.connect(dataRequest.getHost(), dataRequest.getPort())
				.addListener(new RequestingChannelFutureListener(bootstrap, dataRequest, this.maxRetries)
						.setAfter(this::completeSubRequests));
	}

}

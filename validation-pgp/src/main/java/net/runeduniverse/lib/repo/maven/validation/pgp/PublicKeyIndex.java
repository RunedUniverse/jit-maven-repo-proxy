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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.runeduniverse.lib.repo.maven.data.TextProcessor;

public class PublicKeyIndex {

	protected final Map<URI, KeyserverType> keyservers;
	protected final Map<Long, CompletableFuture<PGPPublicKeyRing>> pkMap;
	protected final EventLoopGroup loopGroup;
	protected final int maxRedirects;
	protected final int maxRetries;

	private Bootstrap bootstrap = null;

	public PublicKeyIndex(final int maxRedirects, final int maxRetries) {
		this(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()), maxRedirects, maxRetries);
	}

	public PublicKeyIndex(final EventLoopGroup loopGroup, final int maxRedirects, final int maxRetries) {
		this(new LinkedHashMap<>(), new ConcurrentHashMap<>(), loopGroup, maxRedirects, maxRetries);
	}

	protected PublicKeyIndex(final Map<URI, KeyserverType> keyservers,
			final Map<Long, CompletableFuture<PGPPublicKeyRing>> pkMap, final EventLoopGroup loopGroup,
			final int maxRedirects, final int maxRetries) {
		this.keyservers = keyservers;
		this.pkMap = pkMap;
		this.loopGroup = loopGroup;
		this.maxRedirects = maxRedirects;
		this.maxRetries = maxRetries;
	}

	public PublicKeyIndex addKeyserver(final URI uri, final KeyserverType type) {
		Objects.requireNonNull(uri);
		Objects.requireNonNull(type);
		this.keyservers.put(uri, type);
		return this;
	}

	public PublicKeyIndex addKeyRing(final PGPPublicKeyRing keyRing) {
		if (keyRing == null)
			return this;
		final PGPPublicKey key = keyRing.getPublicKey();
		if (key == null)
			return this;
		this.pkMap.put(key.getKeyID(), CompletableFuture.completedFuture(keyRing));
		return this;
	}

	public PGPPublicKeyRingCollection toKeyRingCollection() {
		return new PGPPublicKeyRingCollection(this.pkMap.values()
				.stream()
				.filter(f -> f.isDone() && !f.isCompletedExceptionally())
				.map(f -> f.getNow(null))
				.collect(Collectors.toList()));
	}

	public PGPPublicKeyRing getKeyRing(final long keyID) {
		final CompletableFuture<PGPPublicKeyRing> future = this.pkMap.get(keyID);
		if (future == null)
			return null;
		try {
			return future.getNow(null);
		} catch (CancellationException | CompletionException e) {
			return null;
		}
	}

	public CompletableFuture<PGPPublicKeyRing> fetchKeyRingIfAbsent(final long keyID) {
		return this.pkMap.computeIfAbsent(keyID, this::fetchKeyRing);
	}

	protected CompletableFuture<PGPPublicKeyRing> fetchKeyRing(final long keyID) {
		final String keyString = Long.toHexString(keyID)
				.toUpperCase();
		final CompletableFuture<PGPPublicKeyRing> future = new CompletableFuture<>();
		final List<CompletableFuture<PGPPublicKeyRing>> futures = new LinkedList<>();

		for (Entry<URI, KeyserverType> entry : this.keyservers.entrySet()) {
			final URI uri = entry.getKey();
			System.out.println("Build Request for Keyserver: " + uri.toString());
			// extract path & query from uri
			final StringBuffer pathBuffer = new StringBuffer();
			final StringBuffer queryBuffer = new StringBuffer();
			{
				String path = uri.getPath();
				if ((path = StringUtils.trimToNull(path)) != null) {
					// rebuild path
					for (String part : path.split("/")) {
						if (part.length() == 0)
							continue;
						pathBuffer.append('/')
								.append(path);
					}
				}
				String query = uri.getQuery();
				if ((query = StringUtils.trimToNull(query)) != null) {
					queryBuffer.append(query);
				}
			}
			// apply the request protocol
			switch (entry.getValue()) {
			case VKS:
				// -- Verifying Keyserver --
				// /vks/v1/by-keyid/<KEY-ID>
				pathBuffer.append("/vks/v1/by-keyid/")
						.append(keyString);
				break;
			case HKP:
				// -- HTTP Keyserver Protocol --
				// /pks/lookup?op=get&options=mr&search=<QUERY>
				pathBuffer.append("/pks/lookup");
				if (0 < queryBuffer.length())
					queryBuffer.append('&');
				queryBuffer.append("op=get&options=mr&search=")
						.append(keyString);
				break;
			default:
				continue;
			}
			// request the KeyRing
			try {
				futures.add(fetchKeyRing(new URI(uri.getScheme(), uri.getAuthority(), pathBuffer.toString(),
						StringUtils.trimToNull(queryBuffer.toString()), uri.getFragment()))
								.whenComplete((keyRing, throwable) -> {
									if (keyRing != null)
										future.complete(keyRing);
									// TODO log error!
								}));
			} catch (URISyntaxException unexpected) {
				unexpected.printStackTrace(System.err);
				continue;
			}
		}

		// short-circurt if there are no active requests!
		if (futures.isEmpty())
			return CompletableFuture.completedFuture(null);

		// after all futures are completed
		// -> supply default result
		// -> required if all fetch tries failed!
		CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[futures.size()]))
				.thenAccept(v -> {
					future.complete(null);
				});

		return future;
	}

	protected CompletableFuture<PGPPublicKeyRing> fetchKeyRing(final URI uri) {
		final TextProcessor processor = new TextProcessor();
		final KeyDataRequest dataRequest = new KeyDataRequest(uri, processor, this.maxRedirects);

		System.out.println("FETCH: " + uri.toString());

		execRequest(dataRequest);

		return processor.future()
				.thenApply(this::parsePublicKey);
	}

	protected PGPPublicKeyRing parsePublicKey(final String armoredKey) {
		try {
			return new PGPPublicKeyRing(//
					new ArmoredInputStream(//
							new ByteArrayInputStream(armoredKey.getBytes(StandardCharsets.UTF_8))),
					new JcaKeyFingerprintCalculator());
		} catch (IOException e) {
			// TODO add propper logging
			e.printStackTrace(System.err);
		}
		return null;
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
				.handler(new PKClientInitializer(sslCtx));
	}

	public void shutdownGracefully() {
		this.loopGroup.shutdownGracefully();
	}

	protected void execRequest(final KeyDataRequest dataRequest) {
		final Bootstrap bootstrap = bootstrap().clone();
		bootstrap.attr(KeyDataRequest.ATTKEY_KEY_DATA_REQUEST, dataRequest);

		bootstrap.connect(dataRequest.getHost(), dataRequest.getPort())
				.addListener(new RequestingChannelFutureListener(bootstrap, dataRequest, this.maxRetries));
	}

	public static void addDefaultKeyservers(final PublicKeyIndex index) {
		addKeyserverOpenPGP(index);
	}

	public static void addKeyserverOpenPGP(final PublicKeyIndex index) {
		try {
			index.addKeyserver(new URI("http://keys.openpgp.org"), KeyserverType.VKS);
		} catch (URISyntaxException e) {
			// impossible!
		}
	}

	public static PublicKeyIndex createDefaultKeyIndex() {
		final PublicKeyIndex index = new PublicKeyIndex(3, 5);
		addDefaultKeyservers(index);
		return index;
	}
}

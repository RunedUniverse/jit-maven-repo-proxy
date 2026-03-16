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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.runeduniverse.lib.repo.maven.data.TextProcessor;

public class PublicKeyIndex {

	protected final NavigableMap<Integer, Set<Keyserver>> keyservers;
	protected final Map<Long, CompletableFuture<PGPPublicKeyRing>> pkMap;
	protected final EventLoopGroup loopGroup;
	protected final int maxRedirects;
	protected final int maxRetries;

	private Bootstrap bootstrap = null;

	public PublicKeyIndex(final int maxRedirects, final int maxRetries) {
		this(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()), maxRedirects, maxRetries);
	}

	public PublicKeyIndex(final EventLoopGroup loopGroup, final int maxRedirects, final int maxRetries) {
		this(new TreeMap<>(), new ConcurrentHashMap<>(), loopGroup, maxRedirects, maxRetries);
	}

	protected PublicKeyIndex(final NavigableMap<Integer, Set<Keyserver>> keyservers,
			final Map<Long, CompletableFuture<PGPPublicKeyRing>> pkMap, final EventLoopGroup loopGroup,
			final int maxRedirects, final int maxRetries) {
		this.keyservers = keyservers;
		this.pkMap = pkMap;
		this.loopGroup = loopGroup;
		this.maxRedirects = maxRedirects;
		this.maxRetries = maxRetries;
	}

	public PublicKeyIndex addKeyserver(final Keyserver keyserver) {
		Objects.requireNonNull(keyserver);
		this.keyservers.computeIfAbsent(keyserver.priority(), k -> new HashSet<>())
				.add(keyserver);
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

	public PGPPublicKey getKey(final long keyID) {
		final PGPPublicKeyRing keyRing = getKeyRing(keyID);
		if (keyRing == null)
			return null;
		for (Iterator<PGPPublicKey> i = keyRing.getPublicKeys(); i.hasNext();) {
			final PGPPublicKey key = i.next();
			if (key.getKeyID() == keyID)
				return key;
		}
		return null;
	}

	public CompletableFuture<PGPPublicKeyRing> fetchKeyRingIfAbsent(final long keyID) {
		return this.pkMap.computeIfAbsent(keyID, this::fetchKeyRing);
	}

	protected CompletableFuture<PGPPublicKeyRing> fetchKeyRing(final long keyID) {
		// ensure <keyString> is 16 hex chars long
		final String keyString = String.format("%016X", keyID);
		final CompletableFuture<PGPPublicKeyRing> future = new CompletableFuture<>();
		final List<CompletableFuture<PGPPublicKeyRing>> futures = new LinkedList<>();

		CompletableFuture<?> blocker = CompletableFuture.completedFuture(null);

		for (Entry<Integer, Set<Keyserver>> entry : this.keyservers.entrySet()) {
			// schedule the requests for this set of Keyservers
			final Set<Keyserver> set = entry.getValue();
			if (set.isEmpty())
				continue;

			for (Keyserver keyserver : set) {
				final URI uri = buildURI(keyString, keyserver.uri(), keyserver.type());
				if (uri == null)
					continue;

				// request the KeyRing
				// -> request awaits blocker ref
				// -> again tracked in list
				// -> next blocker is based on list
				futures.add(blocker.thenApply(v -> {
					// short-circurt if the data was already found!
					if (future.isDone())
						return null;

					fetchKeyRing(keyserver, uri)//
							.whenComplete((keyRing, throwable) -> {
								if (keyRing != null) {
									// keyRing successful acquired!
									future.complete(keyRing);
								}
								if (throwable != null)
									System.err.println("IDX-ERR: " + throwable.getMessage());
								// TODO log error!
								System.err.println("done: " + keyString);
							})
							.join();
					return null;
				}));
			}
			// could happen if all built uri's were null
			if (futures.isEmpty())
				continue;
			// build new blocker
			blocker = CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[futures.size()]));
			futures.clear();
		}

		// after all futures are completed
		// -> supply default result
		// -> required if all fetch tries failed!
		blocker.thenAccept(v -> {
			System.err.println("finalize: " + keyString);
			future.complete(null);
		});

		return future;
	}

	protected URI buildURI(final String keyString, final URI uri, final KeyserverType type) {
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
		switch (type) {
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
			queryBuffer.append("op=get&options=mr&search=0x")
					.append(keyString);
			break;
		default:
			return null;
		}
		try {
			return new URI(uri.getScheme(), uri.getAuthority(), pathBuffer.toString(),
					StringUtils.trimToNull(queryBuffer.toString()), uri.getFragment());
		} catch (URISyntaxException unexpected) {
			unexpected.printStackTrace(System.err);
			return null;
		}
	}

	protected CompletableFuture<PGPPublicKeyRing> fetchKeyRing(final Keyserver keyserver, final URI uri) {
		final TextProcessor processor = new TextProcessor();
		final KeyDataRequest dataRequest = new KeyDataRequest(keyserver, uri, processor, this.maxRedirects);

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
		addKeyserverUbuntu(index);
		addKeyserverMIT(index);
	}

	public static void addKeyserverOpenPGP(final PublicKeyIndex index) {
		index.addKeyserver(new KeyserverOpenPGP(0, KeyserverType.VKS));
	}

	public static void addKeyserverUbuntu(final PublicKeyIndex index) {
		try {
			index.addKeyserver(new Keyserver(0, new URI("https://keyserver.ubuntu.com"), KeyserverType.HKP));
		} catch (URISyntaxException impossible) {
		}
	}

	public static void addKeyserverMIT(final PublicKeyIndex index) {
		try {
			// lower priority starts first -> MIT is extremely slow
			index.addKeyserver(new Keyserver(100, new URI("https://pgp.mit.edu"), KeyserverType.HKP));
		} catch (URISyntaxException impossible) {
		}
	}

	public static PublicKeyIndex createDefaultKeyIndex() {
		final PublicKeyIndex index = new PublicKeyIndex(3, 5);
		addDefaultKeyservers(index);
		return index;
	}

	public static class Keyserver {
		protected final int priority;
		protected final URI uri;
		protected final KeyserverType type;

		public Keyserver(final int priority, final URI uri, final KeyserverType type) {
			this.priority = priority;
			this.uri = uri;
			this.type = type;
		}

		public int priority() {
			return this.priority;
		}

		public URI uri() {
			return this.uri;
		}

		public KeyserverType type() {
			return this.type;
		}

		public boolean handleHttpError(final ChannelHandlerContext ctx, final HttpResponse response,
				final KeyDataRequest dataRequest) {
			return false;
		}
	}

	public static class KeyserverOpenPGP extends Keyserver {

		public static final URI URI_OPENPGP;
		static {
			URI uri;
			try {
				uri = new URI("https://keys.openpgp.org");
			} catch (URISyntaxException impossible) {
				uri = null;
			}
			URI_OPENPGP = uri;
		}

		public KeyserverOpenPGP(final int priority, final KeyserverType type) {
			super(priority, URI_OPENPGP, type);
		}

		@Override
		public boolean handleHttpError(final ChannelHandlerContext ctx, final HttpResponse response,
				final KeyDataRequest dataRequest) {
			final int statusCode = response.status()
					.code();
			if (statusCode == 429) {
				// Rate Limited
				ctx.close();
				try {
					TimeUnit.SECONDS.sleep(1l);
				} catch (InterruptedException ignored) {
				}
				return true;
			}
			return false;
		}
	}
}

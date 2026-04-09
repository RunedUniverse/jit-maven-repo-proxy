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
package net.runeduniverse.lib.repo.maven.client.http.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AsciiString;
import net.runeduniverse.lib.repo.maven.api.BasicCredentials;

public class DigestAuthState implements AuthState {

	protected static final SecureRandom SECURE_RANDOM = new SecureRandom();

	protected final BasicCredentials creds;

	// even when overridden - only access via Getter
	private String realm = null;
	private String nonce = null;
	private String opaque = null;
	private String algorithm = null;
	private String qop = null;
	private AtomicInteger nonceCount = new AtomicInteger(1);

	public DigestAuthState(final BasicCredentials creds, final AuthHeaderSection section) {
		this.creds = creds;
		update(section);
	}

	@Override
	public String authType() {
		return "Digest";
	}

	public synchronized void update(final AuthHeaderSection section) {
		final Map<String, String> data = section.entries();
		{
			// realm
			this.realm = StringUtils.trimToNull(data.get("realm"));
		}
		{
			// algorithm
			this.algorithm = StringUtils.trimToNull(data.get("algorithm"));
		}
		{
			// qop
			final List<String> qopValues = Arrays.stream(data.getOrDefault("qop", "")
					.split(","))
					.map(StringUtils::trimToNull)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
			if (qopValues.contains("auth"))
				this.qop = "auth";
			else
				this.qop = qopValues.isEmpty() ? null : qopValues.get(0);
		}
		{
			// nonce
			this.nonce = StringUtils.trimToNull(data.get("nonce"));
			this.nonceCount.set(1);
		}
		{
			// opaque
			this.opaque = StringUtils.trimToNull(data.get("opaque"));
		}
	}

	public boolean matches(final Map<String, String> data, final boolean skipNonce) {
		final List<String> qopValues = Arrays.stream(data.getOrDefault("qop", "")
				.split(","))
				.map(StringUtils::trimToNull)
				.collect(Collectors.toList());
		return //
		Objects.equals(this.realm, data.get("realm")) //
				&& Objects.equals(this.algorithm, data.get("algorithm")) //
				&& qopValues.contains(this.qop) //
				&& (skipNonce || Objects.equals(this.nonce, data.get("nonce")));
	}

	@Override
	public boolean retryOnRejection(final List<AuthHeaderSection> sections) {
		// for digest we check if the nonce has gone stale
		for (Iterator<AuthHeaderSection> i = sections.stream()
				.filter(s -> authType().equals(s.type()))
				.iterator(); i.hasNext();) {
			final AuthHeaderSection section = i.next();
			if (matches(section.entries(), true)) {
				final Map<String, String> data = section.entries();
				if ("true".equals(StringUtils.trimToNull(data.get("stale")))) {
					update(section);
					return true;
				}
			}
		}
		return false;
	}

	protected String buildNextAuthorizationHeader(final HttpMethod method, final String uriPath)
			throws NoSuchAlgorithmException {
		final short qopMode;
		if (this.qop == null)
			qopMode = 0;
		else if ("auth".equals(this.qop))
			qopMode = 1;
		else
			throw new UnsupportedOperationException("QOP Not Supported! qop=" + this.qop);

		final String nc = String.format("%08x", nonceCount.getAndIncrement());
		final String cnonce = newCNonce();

		final String HA1 = hashAsHex(this.algorithm, new StringBuilder()//
				.append(this.creds.getUser())
				.append(':')
				.append(this.realm)
				.append(':')
				.append(this.creds.getPassword())
				.toString());
		final String HA2 = hashAsHex(this.algorithm, new StringBuilder().append(method.asciiName())
				.append(':')
				.append(uriPath)
				.toString());

		// qop=null - H( HA1 : nonce : HA2 )
		// qop=auth - H( HA1 : nonce : nc : cnonce : qop : HA2 )
		final StringBuilder dataBuilder = new StringBuilder()//
				.append(HA1)
				.append(':')
				.append(this.nonce)
				.append(':');
		if (qopMode == 1) {
			dataBuilder.append(nc)
					.append(':')
					.append(cnonce)
					.append(':')
					.append(this.qop)
					.append(':');
		}
		dataBuilder.append(HA2);

		final String response = hashAsHex(this.algorithm, dataBuilder.toString());

		final StringBuilder builder = new StringBuilder(String.format(
				"Digest realm=\"%s\", algorithm=%s, username=\"%s\", nonce=\"%s\", uri=\"%s\", response=\"%s\"",
				this.realm, this.algorithm, this.creds.getUser(), this.nonce, uriPath, response));
		if (0 < qopMode)
			builder.append(String.format(", qop=%s, nc=%s, cnonce=\"%s\"", this.qop, nc, cnonce));
		if (this.opaque != null)
			builder.append(", opaque=\"" + this.opaque + "\"");

		return builder.toString();

	}

	@Override
	public boolean nextAuthorizationHeader(final HttpRequest request, final AsciiString header) {
		try {
			final String path = new QueryStringDecoder(request.uri()).path();
			request.headers()
					.add(header,
							buildNextAuthorizationHeader(request.method(), StringUtils.isBlank(path) ? "/" : path));
			return true;
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
		return false;
	}

	protected String hashAsHex(final String algo, final String data) throws NoSuchAlgorithmException {
		final MessageDigest md = MessageDigest.getInstance(algo == null ? "MD5" : algo);
		final StringBuilder builder = new StringBuilder();
		for (byte b : md.digest(data.getBytes(StandardCharsets.ISO_8859_1)))
			builder.append(String.format("%02x", b));
		return builder.toString();
	}

	public static String newCNonce() {
		byte[] bytes = new byte[8];
		SECURE_RANDOM.nextBytes(bytes);

		final StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
			sb.append(String.format("%02x", b));
		return sb.toString();
	}
}

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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import net.runeduniverse.lib.repo.maven.data.AContentProcessor;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.error.RedirectRepoException;

public class HttpDataRequest {

	protected final Map<String, AContentProcessor<?>> subProcessorMap = new HashMap<>();
	protected final List<String> redirects = new LinkedList<>();

	protected final CompletableFuture<?> future;
	protected final AContentProcessor<?> processor;
	protected final int maxRedirects;

	protected URI uri;

	public HttpDataRequest(final URI uri, final AContentProcessor<?> processor, final int maxRedirects) {
		this(uri, processor, processor::future, maxRedirects);
	}

	public HttpDataRequest(final URI uri, final AContentProcessor<?> processor,
			final Supplier<CompletableFuture<?>> supplier, final int maxRedirects) {
		this.uri = uri;
		this.processor = processor;
		this.future = supplier.get();
		this.maxRedirects = maxRedirects;
		this.redirects.add(this.uri.toString());
	}

	public URI uri() {
		return this.uri;
	}

	public AContentProcessor<?> processor() {
		return this.processor;
	}

	public CompletableFuture<?> future() {
		return this.future;
	}

	public Map<String, AContentProcessor<?>> subProcessorMap() {
		return this.subProcessorMap;
	}

	public String getHost() {
		return this.uri.getHost();
	}

	public String getScheme() {
		final String scheme = uri.getScheme();
		if (scheme == null)
			return "http";
		return scheme;
	}

	public int getPort() {
		int port = uri.getPort();
		if (port != -1)
			return port;
		return "https".equalsIgnoreCase(getScheme()) ? 443 : 80;
	}

	public boolean withSSL() {
		return "https".equalsIgnoreCase(getScheme());
	}

	public URI redirect(final String location) throws RedirectRepoException {
		System.out.println("Redirect to: " + location);
		if (StringUtils.isBlank(location)) {
			throw new RedirectRepoException(RedirectRepoException.MSG_INVALID_REDIRECT, this.uri.toString(),
					this.redirects, location);
		}
		if (this.maxRedirects <= this.redirects.size())
			throw new RedirectRepoException(RedirectRepoException.MSG_TOO_MANY_REDIRECTS, this.uri.toString(),
					this.redirects, location);
		URI redirected;
		try {
			redirected = new URI(location);
			// handle maven central (sonatype) special
			if (redirected.getPath()
					.endsWith("501-https-required.html")) {
				return this.uri = new URI(redirected.getScheme(), this.uri.getUserInfo(), redirected.getHost(),
						redirected.getPort(), this.uri.getPath(), this.uri.getQuery(), this.uri.getFragment());
			}
		} catch (URISyntaxException cause) {
			throw new RedirectRepoException(RedirectRepoException.MSG_INVALID_REDIRECT, this.uri.toString(),
					this.redirects, location, cause);
		}

		this.uri = redirected.isAbsolute() ? redirected : this.uri.resolve(redirected);
		this.redirects.add(this.uri.toString());
		return this.uri;
	}

	public FullHttpRequest asHttpRequest() {
		String path = this.uri.getRawPath();
		if (path == null || path.isEmpty()) {
			path = "/";
		}
		final FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
		final HttpHeaders headers = request.headers();
		headers.set(HttpHeaderNames.HOST, getHost());
		headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		return request;
	}

	public List<HttpDataRequest> remainingSubRequests() {
		final List<HttpDataRequest> requests = new LinkedList<>();
		for (Entry<String, AContentProcessor<?>> entry : this.subProcessorMap.entrySet()) {
			final AContentProcessor<?> processor = entry.getValue();
			if (processor == null || processor.isDone())
				continue;
			final URI uri;
			try {
				uri = new URI(this.uri.getScheme(), this.uri.getAuthority(), this.uri.getPath() + '.' + entry.getKey(),
						this.uri.getQuery(), this.uri.getFragment());
			} catch (URISyntaxException e) {
				processor.completeExceptionally(new ArtifactException("Artifact can not be requested!", e));
				continue;
			}
			requests.add(new HttpDataRequest(uri, processor, this.maxRedirects));
		}
		return requests;
	}
}

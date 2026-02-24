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
package net.runeduniverse.lib.repo.maven.http.data;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

public class HttpDataRequest {

	protected final Map<String, AContentProcessor<?>> checksumMap = new HashMap<>();
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

	public Map<String, AContentProcessor<?>> checksumMap() {
		return this.checksumMap;
	}

	public URI redirect(final String location) throws URISyntaxException {
		if (StringUtils.isBlank(location))
			throw new URISyntaxException(location, "Invalid Redirect");
		if (this.maxRedirects <= this.redirects.size())
			throw new URISyntaxException(location, "Too Many Redirects");
		final URI redirected = new URI(location);
		this.uri = redirected.isAbsolute() ? redirected : this.uri.resolve(redirected);
		this.redirects.add(this.uri.toString());
		return this.uri;
	}

	public HttpDataRequest newChecksumRequest(final String ext) {
		final AContentProcessor<?> processor = this.checksumMap.get(ext);
		if (processor == null)
			return null;
		final URI uri;
		try {
			uri = new URI(this.uri.getScheme(), this.uri.getAuthority(), this.uri.getPath() + '.' + ext,
					this.uri.getQuery(), this.uri.getFragment());
		} catch (URISyntaxException ignored) {
			return null;
		}
		return new HttpDataRequest(uri, processor, this.maxRedirects);
	}
}

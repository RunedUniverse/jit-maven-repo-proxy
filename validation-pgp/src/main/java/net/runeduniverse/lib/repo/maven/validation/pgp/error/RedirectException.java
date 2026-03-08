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
package net.runeduniverse.lib.repo.maven.validation.pgp.error;

import java.util.ArrayList;
import java.util.List;

public class RedirectException extends RuntimeException {

	public static final String MSG_INVALID_REDIRECT = "Invalid Redirect";
	public static final String MSG_TOO_MANY_REDIRECTS = "Too Many Redirects";

	private static final long serialVersionUID = 1L;

	protected final String originalLocation;
	protected final List<String> redirects;

	public RedirectException(final String reason, final String originalLocation, final List<String> redirects) {
		super(reason);
		this.originalLocation = originalLocation;
		this.redirects = new ArrayList<>(redirects);
	}

	public RedirectException(final String reason, final String originalLocation, final List<String> redirects,
			final String nextLocation) {
		super(reason);
		this.originalLocation = originalLocation;
		this.redirects = new ArrayList<>(redirects);
		this.redirects.add(nextLocation);
	}

	public RedirectException(final String reason, final String originalLocation, final List<String> redirects,
			final Throwable cause) {
		super(reason, cause);
		this.originalLocation = originalLocation;
		this.redirects = new ArrayList<>(redirects);
	}

	public RedirectException(final String reason, final String originalLocation, final List<String> redirects,
			final String nextLocation, final Throwable cause) {
		super(reason, cause);
		this.originalLocation = originalLocation;
		this.redirects = new ArrayList<>(redirects);
		this.redirects.add(nextLocation);
	}

	public String getReason() {
		return super.getMessage();
	}

	public String getOriginalLocation() {
		return this.originalLocation;
	}

	public List<String> getRedirects() {
		return this.redirects;
	}

	@Override
	public String getMessage() {
		final StringBuffer sb = new StringBuffer();
		sb.append(getReason());
		sb.append(" [ ");
		sb.append(this.originalLocation);
		for (String location : this.redirects) {
			sb.append(" » ");
			sb.append(location);
		}
		sb.append(" ]");
		return sb.toString();
	}
}

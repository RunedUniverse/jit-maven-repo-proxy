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
package net.runeduniverse.lib.repo.maven.error;

public class ArtifactException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private static final int PRIORITY = 0;

	protected final int priority;

	protected String packageUrl = null;

	public ArtifactException() {
		this(PRIORITY);
	}

	public ArtifactException(final int priority) {
		super();
		this.priority = priority;
	}

	public ArtifactException(final String message) {
		this(PRIORITY, message);
	}

	public ArtifactException(final int priority, final String message) {
		super(message);
		this.priority = priority;
	}

	public ArtifactException(final String message, final Throwable cause) {
		this(PRIORITY, message, cause);
	}

	public ArtifactException(final int priority, final String message, final Throwable cause) {
		super(message, cause);
		this.priority = priority;
	}

	public int priority() {
		return this.priority;
	}

	public String getPURL() {
		return this.packageUrl;
	}

	public void setPURL(final String purl) {
		this.packageUrl = purl;
	}
}

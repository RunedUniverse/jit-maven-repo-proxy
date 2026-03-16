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

public class SBomViolationException extends InvalidArtifactException {

	private static final long serialVersionUID = 1L;
	private static final int PRIORITY = -30;

	public SBomViolationException() {
		super(PRIORITY);
	}

	public SBomViolationException(final int priority) {
		super(priority);
	}

	public SBomViolationException(final String message) {
		super(PRIORITY, message);
	}

	public SBomViolationException(final int priority, final String message) {
		super(priority, message);
	}

	public SBomViolationException(final String message, final Throwable cause) {
		super(PRIORITY, message, cause);
	}

	public SBomViolationException(final int priority, final String message, final Throwable cause) {
		super(priority, message, cause);
	}
}

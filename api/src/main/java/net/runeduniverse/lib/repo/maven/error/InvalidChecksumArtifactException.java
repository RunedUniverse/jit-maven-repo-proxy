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

public class InvalidChecksumArtifactException extends InvalidArtifactException {

	private static final long serialVersionUID = 1L;

	protected final String checksumExt;
	protected final String localChecksum;
	protected final String remoteChecksum;

	public InvalidChecksumArtifactException(final String checksumExt, final String localChecksum,
			final String remoteChecksum) {
		super("Checksum missmatch detected upon Artifact download!");
		this.checksumExt = checksumExt;
		this.localChecksum = localChecksum;
		this.remoteChecksum = remoteChecksum;
	}

	public String getChecksumExt() {
		return this.checksumExt;
	}

	public String getLocalChecksum() {
		return this.localChecksum;
	}

	public String getRemoteChecksum() {
		return this.remoteChecksum;
	}
}

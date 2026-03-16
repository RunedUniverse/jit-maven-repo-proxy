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

	public static final String MSG_MISSMATCH = "Checksum missmatch detected upon Artifact download!";

	private static final long serialVersionUID = 1L;
	private static final int PRIORITY = -25;

	protected final String checksumAlgorithm;
	protected final String localChecksum;
	protected final String remoteChecksum;

	public InvalidChecksumArtifactException(final String checksumAlgorithm, final String localChecksum,
			final String remoteChecksum) {
		this(PRIORITY, MSG_MISSMATCH, checksumAlgorithm, localChecksum, remoteChecksum);
	}

	public InvalidChecksumArtifactException(final int priority, final String checksumAlgorithm,
			final String localChecksum, final String remoteChecksum) {
		this(priority, MSG_MISSMATCH, checksumAlgorithm, localChecksum, remoteChecksum);
	}

	public InvalidChecksumArtifactException(final String reason, final String checksumAlgorithm,
			final String localChecksum, final String remoteChecksum) {
		this(PRIORITY, reason, checksumAlgorithm, localChecksum, remoteChecksum);
	}

	public InvalidChecksumArtifactException(final int priority, final String reason, final String checksumAlgorithm,
			final String localChecksum, final String remoteChecksum) {
		super(priority, reason);
		this.checksumAlgorithm = checksumAlgorithm;
		this.localChecksum = localChecksum;
		this.remoteChecksum = remoteChecksum;
	}

	public String getReason() {
		return super.getMessage();
	}

	public String getChecksumAlgorithm() {
		return this.checksumAlgorithm;
	}

	public String getLocalChecksum() {
		return this.localChecksum;
	}

	public String getRemoteChecksum() {
		return this.remoteChecksum;
	}

	@Override
	public String getMessage() {
		final StringBuffer sb = new StringBuffer();
		sb.append(getReason());
		sb.append(" [ ");
		sb.append(this.checksumAlgorithm);
		sb.append(" » ");
		sb.append(this.localChecksum);
		sb.append(" / ");
		sb.append(this.remoteChecksum);
		sb.append(" ]");
		return sb.toString();
	}
}

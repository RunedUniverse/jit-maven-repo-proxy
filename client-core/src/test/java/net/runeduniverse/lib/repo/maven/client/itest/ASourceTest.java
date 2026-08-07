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
package net.runeduniverse.lib.repo.maven.client.itest;

import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySourceClient;

public abstract class ASourceTest {

	@TempDir
	protected Path repoPath;

	protected RepositorySource source = null;
	protected RepositorySourceClient client = null;

	public long timeout() {
		return 10;
	}

	public Path repoPath() {
		return this.repoPath;
	}

	public RepositorySourceClient client() {
		return this.client;
	}

	protected abstract RepositorySource configure();

	@BeforeEach
	public void before() throws InterruptedException {
		this.source = configure();
		this.client = this.source.client();

		assert this.source.getLocalRepoPath() != null;
		assert this.client != null;
	}

	@AfterEach
	public void shutdown() {
		if (this.client != null) {
			this.client.shutdownGracefully();
		}
	}

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

	public String systemProperty_repo_mvnCentral_urlHttp() {
		return System.getProperty("test.infra.repo.maven-central.url-http",
				"https://nexus.runeduniverse.net/repository/maven-central/");
	}

	public String systemProperty_r4m_version() {
		return System.getProperty("test.infra.r4m.version", "1.1.0");
	}

	public boolean systemProperty_offline() {
		return Boolean.parseBoolean(System.getProperty("test.infra.offline", "false"));
	}
}

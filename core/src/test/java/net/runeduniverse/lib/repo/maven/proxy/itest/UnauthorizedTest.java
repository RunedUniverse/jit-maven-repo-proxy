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
package net.runeduniverse.lib.repo.maven.proxy.itest;

import java.net.URI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.runeduniverse.lib.repo.maven.proxy.api.RepositorySource;
import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.proxy.itest.dummy.UnauthorizedRepoInstance;
import net.runeduniverse.lib.repo.maven.proxy.source.http.HttpSource;

public class UnauthorizedTest extends ARepoTest {

	@Override
	protected ProxyServerBuilder configureServer(ProxyServerBuilder builder) {
		return builder.instance("maven-central", instance -> {
			// ensure no artifact is ever found
			instance.setInstanceFacory(UnauthorizedRepoInstance::new);
		});
	}

	@Override
	protected RepositorySource configureClient() {
		return new HttpSource("proxy",
				URI.create(String.format("http://%s/repository/maven-central/", this.socketAddress.getHostString())),
				clientPath(), 3, 5);
	}

	@Test
	@Tag("live")
	public void unauthorized() throws InterruptedException {
		// TODO implement client!
	}

}

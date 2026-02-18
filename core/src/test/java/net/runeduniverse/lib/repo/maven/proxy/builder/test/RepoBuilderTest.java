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
package net.runeduniverse.lib.repo.maven.proxy.builder.test;

import java.net.URI;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.proxy.source.http.HttpSource;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;

public class RepoBuilderTest {

	public void print(String line) {
		System.out.println(LocalDateTime.now() + ": " + line);
	}

	@Test
	@Tag("system")
	public void exec() throws InterruptedException {
		print("starting test");

		ProxyServer server = new ProxyServerBuilder()//
				.cacheFactory(path -> null)
				.instance("maven-central", instance -> {
					instance.putSource(
							new HttpSource("repo1.maven.org", URI.create("https://repo1.maven.org/maven2/")));
				})
				.build();

		assert server != null;
	}

}

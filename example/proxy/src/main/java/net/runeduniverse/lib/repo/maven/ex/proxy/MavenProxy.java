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
package net.runeduniverse.lib.repo.maven.ex.proxy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.netty.channel.Channel;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.client.http.HttpSource;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupArtifactListener;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupMetadataListener;
import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;
import net.runeduniverse.lib.repo.maven.validation.pgp.PGPArtifactSignatureValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class MavenProxy {

	protected static Path workspacePath;
	protected static Path repoPath;
	protected static InetSocketAddress socketAddress = null;
	protected static int port = 4444;
	protected static ProxyServer proxy;
	protected static Channel channel;

	public static void main(String[] args) throws InterruptedException, URISyntaxException {
		System.out.println("--- MavenProxy ---");
		workspacePath = Paths.get(MavenProxy.class.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI())
				.getParent();

		ArtifactValidator pgpValidator = new PGPArtifactSignatureValidator(PublicKeyIndex.createDefaultKeyIndex());
		LookupMetadataListener lookupMetadataListener = new LookupMetadataListener() {
			@Override
			public void preLookup(ArtifactCoordinates coords) throws ArtifactException {
				System.out.println("lookup-metadata: " + ArtifactCoordinates.key(coords));
			}
		};
		LookupArtifactListener lookupArtifactListener = new LookupArtifactListener() {
			@Override
			public void preLookup(ArtifactDataCoordinates coords) throws ArtifactException {
				System.out.println("lookup-artifact: " + ArtifactDataCoordinates.key(coords));
			}

			@Override
			public void postLookup(Future<ArtifactData> future) {
				Object data = null;
				Throwable throwable = null;

				try {
					data = future.get(1, TimeUnit.MINUTES);
				} catch (Throwable t) {
					throwable = t;
				}

				System.out.println("DATA: " + data);
				System.out.println("ERROR: " + throwable);
			}
		};

		ProxyServerBuilder builder = new ProxyServerBuilder();
		builder.setServerChannelInitializer(HttpRepoServerInitializer::new);
		builder.instance("maven-central", instance -> {
			instance.putSource(new HttpSource("repo1", URI.create("https://repo1.maven.org/maven2/"),
					workspacePath.resolve("repo1"), 3, 10).addFirstValidator(pgpValidator))
					.addListener(lookupMetadataListener)
					.addListener(lookupArtifactListener);
			instance.putSource(new HttpSource("rnet-releases",
					URI.create("https://nexus.runeduniverse.net/repository/maven-releases/"),
					workspacePath.resolve("rnet-releases"), 3, 10).addFirstValidator(pgpValidator))
					.addListener(lookupMetadataListener)
					.addListener(lookupArtifactListener);
			instance.putSource(new HttpSource("rnet-development",
					URI.create("https://nexus.runeduniverse.net/repository/maven-development/"),
					workspacePath.resolve("rnet-development"), 3, 10).addFirstValidator(pgpValidator))
					.addListener(lookupMetadataListener)
					.addListener(lookupArtifactListener);
		});

		proxy = builder.build();

		socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);

		channel = proxy.bindChannel(socketAddress)
				.sync()
				.channel();

		System.out.println(String.format("  open:  http://%s:%d/maven-central/", socketAddress.getHostString(),
				socketAddress.getPort()));

		// use it!
		for (int i = 30; 0 < i; i--) {
			System.out.println(String.format("Server will stop in %d min ...", i));
			TimeUnit.MINUTES.sleep(1);
		}

		System.out.println("  Stopping Server ...");

		try {
			channel.close();
		} finally {
			proxy.shutdownGracefully();
		}
		System.out.println("  done!");
	}
}

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
package net.runeduniverse.lib.repo.maven.ex.proxy2;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.model.Bom;
import org.cyclonedx.parsers.BomParserFactory;
import org.cyclonedx.parsers.Parser;

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
import net.runeduniverse.lib.repo.maven.proxy.builder.RepoInstanceBuilder;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.CyclonedxValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

public class MavenProxy {

	protected static Path workspacePath;
	protected static Path repoPath;
	protected static InetSocketAddress socketAddress = null;
	protected static int port = 4444;
	protected static ProxyServer proxy;
	protected static Channel channel;

	protected final LookupMetadataListener lookupMetadataListener;
	protected final LookupArtifactListener lookupArtifactListener;
	protected final LookupMetadataListener lookupPluginMetadataListener;
	protected final LookupArtifactListener lookupPluginArtifactListener;

	public MavenProxy() {
		this.lookupMetadataListener = initLookupMetadataListener();
		this.lookupArtifactListener = initLookupArtifactListener();

		this.lookupPluginMetadataListener = initLookupPluginMetadataListener();
		this.lookupPluginArtifactListener = initLookupPluginArtifactListener();
	}

	public void initInstance(RepoInstanceBuilder instance, Consumer<RepoInstanceBuilder> consumer) {
		consumer.accept(instance);

		instance.addListener(lookupMetadataListener);
		instance.addListener(lookupArtifactListener);
	}

	public void initPluginInstance(RepoInstanceBuilder instance, Consumer<RepoInstanceBuilder> consumer) {
		consumer.accept(instance);

		instance.addListener(lookupPluginMetadataListener);
		instance.addListener(lookupPluginArtifactListener);
	}

	protected LookupMetadataListener initLookupMetadataListener() {
		return new LookupMetadataListener() {
			@Override
			public void preLookup(ArtifactCoordinates coords) throws ArtifactException {
				System.out.println("lookup-metadata: " + ArtifactCoordinates.key(coords));
			}
		};
	}

	protected LookupArtifactListener initLookupArtifactListener() {
		return new LookupArtifactListener() {
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
	}

	protected LookupMetadataListener initLookupPluginMetadataListener() {
		return new LookupMetadataListener() {
			@Override
			public void preLookup(ArtifactCoordinates coords) throws ArtifactException {
				System.out.println("lookup-plugin-metadata: " + ArtifactCoordinates.key(coords));
			}
		};
	}

	protected LookupArtifactListener initLookupPluginArtifactListener() {
		return new LookupArtifactListener() {
			@Override
			public void preLookup(ArtifactDataCoordinates coords) throws ArtifactException {
				System.out.println("lookup-plugin-artifact: " + ArtifactDataCoordinates.key(coords));
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
	}

	public static void main(String[] argsArr) throws InterruptedException, URISyntaxException {
		LinkedList<String> args = new LinkedList<>(Arrays.asList(argsArr));

		System.out.println("--- MavenProxy ---");
		workspacePath = Paths.get(MavenProxy.class.getProtectionDomain()
				.getCodeSource()
				.getLocation()
				.toURI())
				.getParent();
		repoPath = workspacePath.resolve("repo");
		PublicKeyIndex keyIndex = PublicKeyIndex.createDefaultKeyIndex();
		ComponentIndex compIndex = new ComponentIndex();

		List<Bom> sbomLst = extractSBomList(args);
		final ArtifactValidator sbomValidator;

		if (sbomLst.isEmpty())
			sbomValidator = null;
		else {
			for (Bom sbom : sbomLst)
				compIndex.addBom(sbom);
			sbomValidator = new CyclonedxValidator(keyIndex, compIndex);
		}

		MavenProxy mvnProxy = new MavenProxy();

		ProxyServerBuilder builder = new ProxyServerBuilder();
		builder.setServerChannelInitializer(HttpRepoServerInitializer::new);
		// maven-central
		mvnProxy.initInstance(builder.instance("maven-central"), instance -> {
			instance.putSource(createHttpSource("repo1", URI.create("https://repo1.maven.org/maven2/"))
					.addLastValidator(sbomValidator));
		});
		mvnProxy.initPluginInstance(builder.instance("maven-central-plugins"), instance -> {
			instance.putSource(createHttpSource("repo1-plugins", URI.create("https://repo1.maven.org/maven2/"))
					.addLastValidator(sbomValidator));
		});
		// rnet-releases
		mvnProxy.initInstance(builder.instance("rnet-releases"), instance -> {
			instance.putSource(createHttpSource("rnet-releases",
					URI.create("https://nexus.runeduniverse.net/repository/maven-releases/"))
							.addLastValidator(sbomValidator));
		});
		// rnet-development
		mvnProxy.initInstance(builder.instance("rnet-development"), instance -> {
			instance.putSource(createHttpSource("rnet-development",
					URI.create("https://nexus.runeduniverse.net/repository/maven-development/"))
							.addLastValidator(sbomValidator));
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

	public static HttpSource createHttpSource(final String key, final URI uri) {
		return new HttpSource(key, uri, repoPath.resolve(key), 3, 10);
	}

	public static List<Bom> extractSBomList(List<String> args) {
		final List<Bom> col = new LinkedList<>();

		for (String arg : args) {
			Bom sbom = parseSBom(arg);
			if (sbom == null)
				continue;
			col.add(sbom);
		}
		return col;
	}

	public static Bom parseSBom(String arg) {
		final Pattern pattern = Pattern.compile("--sbom=(.*)");

		// locate sbom-path
		Path sbomPath = null;
		Matcher matcher = pattern.matcher(arg);
		if (!matcher.matches())
			return null;

		sbomPath = Path.of(matcher.group(1));

		// get parser -> either xml or json
		Parser parser;
		try (final InputStream stream = Files.newInputStream(sbomPath)) {
			final byte[] bytes = IOUtils.toByteArray(stream, 1);
			parser = BomParserFactory.createParser(bytes);
		} catch (ParseException | IOException e) {
			e.printStackTrace(System.err);
			return null;
		}

		// parse sbom
		try (final InputStream stream = Files.newInputStream(sbomPath)) {
			return parser.parse(stream);
		} catch (IOException | ParseException e) {
			e.printStackTrace(System.err);
		}
		return null;
	}
}

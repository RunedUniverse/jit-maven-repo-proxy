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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.cyclonedx.Version;
import org.cyclonedx.exception.ParseException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Hash;
import org.cyclonedx.parsers.BomParserFactory;
import org.cyclonedx.parsers.Parser;
import org.cyclonedx.util.BomUtils;

import io.netty.channel.Channel;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactValidator;
import net.runeduniverse.lib.repo.maven.api.MetadataValidator;
import net.runeduniverse.lib.repo.maven.client.http.HttpSource;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupArtifactListener;
import net.runeduniverse.lib.repo.maven.proxy.api.LookupMetadataListener;
import net.runeduniverse.lib.repo.maven.proxy.builder.ProxyServerBuilder;
import net.runeduniverse.lib.repo.maven.proxy.builder.RepoInstanceBuilder;
import net.runeduniverse.lib.repo.maven.server.http.HttpRepoServerInitializer;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.CyclonedxMetadataFilter;
import net.runeduniverse.lib.repo.maven.validation.cyclonedx.CyclonedxValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PGPArtifactSignatureValidator;
import net.runeduniverse.lib.repo.maven.validation.pgp.PublicKeyIndex;

import static net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex.ignoreExcludedDependency;
import static net.runeduniverse.lib.repo.maven.validation.cyclonedx.ComponentIndex.ignoreTestDependency;

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

	protected final ComponentIndex dependencyIndex = new ComponentIndex();
	protected final ComponentIndex pluginIndex = new ComponentIndex();

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
				ArtifactData data = null;

				try {
					data = future.get(1, TimeUnit.MINUTES);
				} catch (Throwable ignored) {
				}

				if (data != null) {
					final Component component = new Component();
					final String purl = data.getPURL();
					component.setBomRef(purl);
					component.setGroup(data.getGroupId());
					component.setName(data.getArtifactId());
					component.setVersion(data.getVersion());
					component.setPurl(purl);
					component.setHashes(data.getChecksums()
							.entrySet()
							.stream()
							.map(e -> new Hash(e.getKey(), e.getValue()))
							.toList());

					MavenProxy.this.dependencyIndex.addComponent(component);
				}
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
				ArtifactData data = null;

				try {
					data = future.get(1, TimeUnit.MINUTES);
				} catch (Throwable ignored) {
				}

				if (data != null) {
					final Component component = new Component();
					final String purl = data.getPURL();
					component.setBomRef(purl);
					component.setGroup(data.getGroupId());
					component.setName(data.getArtifactId());
					component.setVersion(data.getVersion());
					component.setPurl(purl);
					component.setHashes(data.getChecksums()
							.entrySet()
							.stream()
							.map(e -> new Hash(e.getKey(), e.getValue()))
							.toList());

					MavenProxy.this.pluginIndex.addComponent(component);
				}
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
		ComponentIndex componentIndex = new ComponentIndex();
		ComponentIndex pluginIndex = new ComponentIndex();

		final MetadataValidator sbomMetadataValidator;
		final ArtifactValidator sbomValidator;
		final MetadataValidator sbomPluginMetadataValidator;
		final ArtifactValidator sbomPluginValidator;
		{
			// index sbom - for dependency use
			List<Bom> sbomLst = extractParamList("sbom", args).stream()
					.map(MavenProxy::parseSBom)
					.filter(Objects::nonNull)
					.toList();

			if (sbomLst.isEmpty()) {
				sbomMetadataValidator = null;
				sbomValidator = null;
			} else {
				for (Bom sbom : sbomLst)
					componentIndex.addBom(sbom);

				sbomMetadataValidator = new CyclonedxMetadataFilter(componentIndex);
				sbomValidator = new CyclonedxValidator(keyIndex, componentIndex).setIgnorePom(true);
			}

			// index sbom - for providing plugins
			sbomLst = extractParamList("sbom-plugin", args).stream()
					.map(MavenProxy::parseSBom)
					.filter(Objects::nonNull)
					.toList();

			if (sbomLst.isEmpty()) {
				sbomPluginMetadataValidator = null;
				sbomPluginValidator = null;
			} else {
				for (Bom sbom : sbomLst)
					pluginIndex.addBom(sbom, ignoreExcludedDependency().and(ignoreTestDependency()));

				sbomPluginMetadataValidator = new CyclonedxMetadataFilter(pluginIndex);
				sbomPluginValidator = new CyclonedxValidator(keyIndex, pluginIndex).setIgnorePom(true);
			}
		}
		final ArtifactValidator pomValidator = new PGPArtifactSignatureValidator(keyIndex) {
			public boolean validate(final ArtifactData data) throws InvalidArtifactException {
				if (data.isPOM())
					return super.validate(data);
				return false;
			};
		};

		MavenProxy mvnProxy = new MavenProxy();

		ProxyServerBuilder builder = new ProxyServerBuilder();
		builder.setServerChannelInitializer(HttpRepoServerInitializer::new);
		// maven-central
		mvnProxy.initInstance(builder.instance("maven-central"), instance -> {
			instance.putSource(createHttpSource("repo1", URI.create("https://repo1.maven.org/maven2/"))
					.addFirstValidator(sbomMetadataValidator)
					.addLastValidator(pomValidator)
					.addLastValidator(sbomValidator));
		});
		mvnProxy.initPluginInstance(builder.instance("maven-central-plugins"), instance -> {
			instance.putSource(createHttpSource("repo1-plugins", URI.create("https://repo1.maven.org/maven2/"))
					.addFirstValidator(sbomPluginMetadataValidator)
					.addLastValidator(pomValidator)
					.addLastValidator(sbomPluginValidator));
		});
		// rnet-releases
		mvnProxy.initInstance(builder.instance("rnet-releases"), instance -> {
			instance.putSource(createHttpSource("rnet-releases",
					URI.create("https://nexus.runeduniverse.net/repository/maven-releases/"))
							.addFirstValidator(sbomMetadataValidator)
							.addLastValidator(pomValidator)
							.addLastValidator(sbomValidator));
		});
		// rnet-development
		mvnProxy.initInstance(builder.instance("rnet-development"), instance -> {
			instance.putSource(createHttpSource("rnet-development",
					URI.create("https://nexus.runeduniverse.net/repository/maven-development/"))
							.addFirstValidator(sbomMetadataValidator)
							.addLastValidator(pomValidator)
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

		try (Scanner scanner = new Scanner(System.in)) {
			loop: while (true) {
				System.out.print("> ");
				String input = scanner.nextLine()
						.trim();
				final String cmd;
				{
					String[] arr = input.split(" ", 2);
					cmd = arr.length == 0 ? "" : arr[0].toLowerCase();
					input = arr.length == 2 ? arr[1] : "";
				}

				switch (cmd) {
				case "exit":
					break loop;
				case "info":
					System.out.println(//
							"  ---- Info ----\n    dependencies: "//
									+ mvnProxy.dependencyIndex.getComponents()
											.size()
									+ "\n    plugins: "//
									+ mvnProxy.pluginIndex.getComponents()
											.size()
									+ "\n");
					break;
				case "save":
					final String[] params = input.split(" ", 2);
					if (input.length() == 0 || params.length != 2) {
						System.err.println("  save <dep-sbom / plugin-sbom> <path>\n");
						break;
					}
					// select index
					final ComponentIndex index;
					if ("dep-sbom".equals(params[0]))
						index = mvnProxy.dependencyIndex;
					else if ("plugin-sbom".equals(params[0]))
						index = mvnProxy.pluginIndex;
					else {
						System.err.println("  save <dep-sbom / plugin-sbom> <path>");
						System.err.println("  err: unknown section » " + params[0] + "\n");
						break;
					}
					// to SBOM
					final Bom sbom = new Bom();
					for (Component component : index.getComponents()) {
						sbom.addComponent(component);
					}
					final String sbomText;
					if (params[1].endsWith(".xml")) {
						sbomText = BomGeneratorFactory.createXml(Version.VERSION_16, sbom)
								.toString();
					} else if (params[1].endsWith(".json")) {
						sbomText = BomGeneratorFactory.createJson(Version.VERSION_16, sbom)
								.toString();
					} else {
						System.err.println("  save <dep-sbom / plugin-sbom> <path>");
						System.err.println("  err: path must end in either .xml or .json\n");
						break;
					}
					// get Path
					final Path path = Path.of(params[1]);
					try {
						Files.createDirectories(path.getParent());
					} catch (IOException e) {
						System.err.println("  save <dep-sbom / plugin-sbom> <path>");
						System.err.println("  err: " + e.getMessage() + "\n");
						break;
					}
					// write
					try {
						Files.write(path, sbomText.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					} catch (IOException e) {
						System.err.println("  save <dep-sbom / plugin-sbom> <path>");
						System.err.println("  err: " + e.getMessage() + "\n");
						break;
					}
					System.out.println("  SBOM written to " + path.toString());
					break;
				}
			}
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

	public static List<String> extractParamList(final String key, final List<String> args) {
		final List<String> col = new LinkedList<>();

		for (String arg : args) {
			String param = extractParam(key, arg);
			if (param == null)
				continue;
			col.add(param);
		}
		return col;
	}

	public static String extractParam(final String key, final String arg) {
		final Pattern pattern = Pattern.compile("--" + key + "=(.*)");
		final Matcher matcher = pattern.matcher(arg);
		if (!matcher.matches())
			return null;

		return matcher.group(1);
	}

	public static Bom parseSBom(final String param) {
		final Path sbomPath;
		try {
			sbomPath = Path.of(param);
		} catch (InvalidPathException e) {
			e.printStackTrace(System.err);
			return null;
		}

		// get parser -> either xml or json
		final Parser parser;
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

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
package net.runeduniverse.lib.repo.maven.proxy.builder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import net.runeduniverse.lib.repo.maven.api.FileContentType;
import net.runeduniverse.lib.repo.maven.proxy.DefaultCache;
import net.runeduniverse.lib.repo.maven.proxy.ProxyServer;
import net.runeduniverse.lib.repo.maven.proxy.api.MavenRepositoryProxyInstance;
import net.runeduniverse.lib.repo.maven.proxy.cache.api.Cache;

public class ProxyServerBuilder {

	protected final Map<String, RepoInstanceBuilder> instanceMap = new LinkedHashMap<>();
	protected final Map<String, String> fType2cTypeMap = new LinkedHashMap<>();
	protected final Map<String, FileContentType> fTypeMap = new LinkedHashMap<>();

	protected Function<String, RepoInstanceBuilder> repoBuilderFactory = RepoInstanceBuilder::new;
	protected Function<MavenRepositoryProxyInstance, Cache> cacheFactory = DefaultCache::new;

	public ProxyServerBuilder setInstanceBuilderFactory(Function<String, RepoInstanceBuilder> factory) {
		this.repoBuilderFactory = factory;
		return this;
	}

	public RepoInstanceBuilder instance(String path) {
		path = path.trim();
		path = path.replace("/", "");
		path = path.replace(".", "");
		return this.instanceMap.computeIfAbsent(path, this.repoBuilderFactory);
	}

	public ProxyServerBuilder instance(final String path, final Consumer<RepoInstanceBuilder> consumer) {
		consumer.accept(instance(path));
		return this;
	}

	public ProxyServerBuilder setCacheFactory(final Function<MavenRepositoryProxyInstance, Cache> factory) {
		this.cacheFactory = factory == null ? DefaultCache::new : factory;
		return this;
	}

	public ProxyServerBuilder mapFileType2ContentType(final String fileType, final String httpContentType) {
		this.fType2cTypeMap.put(fileType, httpContentType);
		return this;
	}

	public ProxyServerBuilder registerFileContentType(final String fileType, final FileContentType type) {
		this.fTypeMap.put(fileType, type);
		return this;
	}

	public ProxyServer build() {
		final Map<String, String> fType2cTypeMap = defaultFileTypeMappings();
		final Map<String, FileContentType> fTypeMap = defaultFileTypes();
		// overrides
		fType2cTypeMap.putAll(this.fType2cTypeMap);
		fTypeMap.putAll(this.fTypeMap);

		final ProxyServer server = new ProxyServer(fType2cTypeMap, fTypeMap);

		for (Entry<String, RepoInstanceBuilder> entry : this.instanceMap.entrySet()) {
			server.addInstance(entry.getValue()
					.build(this.cacheFactory));
		}

		return server;
	}

	protected Map<String, String> defaultFileTypeMappings() {
		final Map<String, String> fType2cTypeMap = new LinkedHashMap<>();

		// xml files
		fType2cTypeMap.put("xml", "application/xml");
		fType2cTypeMap.put("pom", "application/xml");
		// signatures
		fType2cTypeMap.put("asc", "application/pgp-signature");
		// hashes as hex text
		fType2cTypeMap.put("md5", "text/plain; charset=UTF-8");
		fType2cTypeMap.put("sha1", "text/plain; charset=UTF-8");
		fType2cTypeMap.put("sha256", "text/plain; charset=UTF-8");
		fType2cTypeMap.put("sha512", "text/plain; charset=UTF-8");
		// all others fall back to "application/octet-stream" by default

		return fType2cTypeMap;
	}

	protected Map<String, FileContentType> defaultFileTypes() {
		final Map<String, FileContentType> fTypeMap = new LinkedHashMap<>();

		fTypeMap.put("pom", FileContentType.POM);
		// signatures
		fTypeMap.put("asc", FileContentType.SIGNATURE);
		// hashes
		fTypeMap.put("md5", FileContentType.CHECKSUM);
		fTypeMap.put("sha1", FileContentType.CHECKSUM);
		fTypeMap.put("sha256", FileContentType.CHECKSUM);
		fTypeMap.put("sha512", FileContentType.CHECKSUM);
		// all others fall back to DATA by default

		return fTypeMap;
	}

}

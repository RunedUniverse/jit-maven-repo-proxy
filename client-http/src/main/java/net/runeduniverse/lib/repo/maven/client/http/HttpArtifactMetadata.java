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
package net.runeduniverse.lib.repo.maven.client.http;

import java.io.StringReader;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.AArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.TextProcessor;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;

public class HttpArtifactMetadata extends AArtifactMetadata {

	protected final URI repoUri;
	protected final int maxRedirects;

	// even when overridden - only access via Getter
	private HttpDataRequest metadataRequest = null;

	public HttpArtifactMetadata(final URI repoUri, final int maxRedirects, //
			final ArtifactCoordinates coords) {
		super(coords);

		this.repoUri = repoUri;
		this.maxRedirects = maxRedirects;
	}

	public HttpArtifactMetadata(final URI repoUri, final int maxRedirects, //
			final String groupId, final String artifactId) {
		super(groupId, artifactId);

		this.repoUri = repoUri;
		this.maxRedirects = maxRedirects;
	}

	public List<HttpDataRequest> getDataRequests() {
		final List<HttpDataRequest> dataRequests = new LinkedList<>();
		dataRequests.add(getMetadataRequest(this.repoUri, this.maxRedirects));
		return dataRequests;
	}

	@Override
	public CompletableFuture<ArtifactMetadata> asFuture() {
		final List<HttpDataRequest> dataRequests = getDataRequests();
		final CompletableFuture<?>[] futures = new CompletableFuture<?>[dataRequests.size()];
		for (int i = 0; i < dataRequests.size(); i++) {
			futures[i] = dataRequests.get(i)
					.future();
		}
		return CompletableFuture.allOf(futures)
				.thenApply(v -> HttpArtifactMetadata.this);
	}

	public synchronized HttpDataRequest getMetadataRequest(final URI repoUri, final int maxRedirects) {
		if (this.metadataRequest != null)
			return this.metadataRequest;

		// --- Build Metadata Request ---
		final TextProcessor textProcessor = new TextProcessor();
		final HttpDataRequest metadataRequest = new HttpDataRequest(//
				this.repoUri.resolve(//
						String.join("/", this.groupId.replace('.', '/'), this.artifactId, "maven-metadata.xml")//
				), textProcessor, () -> {
					return textProcessor.future()
							.thenApply(AArtifactMetadata::throwNullAsNotFound)
							.thenApply(HttpArtifactMetadata.this::parseXmlText);
				}, this.maxRedirects);

		return this.metadataRequest = metadataRequest;
	}

	protected String parseXmlText(final String text) {
		if (text == null)
			return null;

		try {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder builder = factory.newDocumentBuilder();

			final Document document = builder.parse(new InputSource(new StringReader(text)));
			final Element docElement = document.getDocumentElement();
			final Element versioningElement = findElement(docElement, "versioning");

			if (versioningElement == null)
				return text;

			final Element latestElement = findElement(versioningElement, "latest");
			forTextContent(latestElement, this::setLatest);

			final Element releaseElement = findElement(versioningElement, "release");
			forTextContent(releaseElement, this::setRelease);

			final Element lastUpdatedElement = findElement(versioningElement, "lastUpdated");
			forTextContent(lastUpdatedElement, this::setLastUpdated);

			final Element versionsElement = findElement(versioningElement, "versions");
			forEachChildTextContent(versionsElement, "version", this::addVersion);

		} catch (Exception e) {
			throw new NotFoundArtifactException("unexpected exception while parsing in maven-metadata.xml", e);
		}

		return text;
	}

	protected Element findElement(final Element parentElement, final String tagName) {
		final NodeList list = parentElement.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			final Node node = list.item(i);
			if (!(node instanceof Element) || !tagName.equals(node.getNodeName()))
				continue;
			return (Element) node;
		}
		return null;
	}

	protected void forTextContent(final Element element, final Consumer<String> consumer) {
		if (element == null)
			return;
		String content;
		try {
			content = element.getTextContent();
		} catch (DOMException ignored) {
			content = null;
		}
		consumer.accept(content);
	}

	protected void forEachChildTextContent(final Element element, final String tagName,
			final Consumer<String> consumer) {
		final NodeList list = element.getChildNodes();
		for (int i = 0; i < list.getLength(); i++) {
			final Node node = list.item(i);
			if (!(node instanceof Element) || !tagName.equals(node.getNodeName()))
				continue;
			forTextContent((Element) node, consumer);
		}
	}
}

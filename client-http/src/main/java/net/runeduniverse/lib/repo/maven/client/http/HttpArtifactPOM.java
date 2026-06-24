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

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactPOM;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.error.ArtifactException;
import net.runeduniverse.lib.repo.maven.error.InvalidArtifactException;

public class HttpArtifactPOM extends HttpArtifactData implements ArtifactPOM {

	private String packagingProcedure = null;

	public HttpArtifactPOM(final Path repoPath, final URI repoUri, final int maxRedirects, //
			final ArtifactProvider providerProxy, //
			final String groupId, final String artifactId, final String version, //
			final String classifier, final String extension) {
		super(repoPath, repoUri, maxRedirects, providerProxy, groupId, artifactId, version, classifier, extension);
	}

	public HttpArtifactPOM(final Path repoPath, final URI repoUri, final int maxRedirects, //
			final ArtifactProvider providerProxy, //
			final ArtifactDataCoordinates coords) {
		super(repoPath, repoUri, maxRedirects, providerProxy, coords);
	}

	@Override
	public String getPackagingProcedure() {
		return this.packagingProcedure;
	}

	protected void setPackagingProcedure(final String value) {
		this.packagingProcedure = value;
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void parseData() throws ArtifactException {
		final Path pomPath = getArtifactPath();

		try {
			String xmlText = new String(Files.readAllBytes(pomPath), StandardCharsets.UTF_8);
			xmlText = org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4(xmlText);

			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder builder = factory.newDocumentBuilder();

			final Document document = builder.parse(new InputSource(new StringReader(xmlText)));
			final Element docElement = document.getDocumentElement();

			final Element packagingProcedure = findElement(docElement, "packaging");
			forTextContent(packagingProcedure, this::setPackagingProcedure);

		} catch (ParserConfigurationException | SAXException | IOException e) {
			throw new InvalidArtifactException("unexpected exception while parsing in " + pomPath.getFileName()
					.toString(), e);
		}
	}

	@Override
	public CompletableFuture<ArtifactPOM> asFuture() {
		return super.asFuture().thenApply(v -> HttpArtifactPOM.this);
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
}

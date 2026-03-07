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
package net.runeduniverse.lib.repo.maven.client;

import java.io.StringReader;
import java.util.NavigableSet;
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
import net.runeduniverse.lib.repo.maven.data.AArtifactMetadata;
import net.runeduniverse.lib.repo.maven.data.ComparableVersion;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;

public class XmlArtifactMetadata extends AArtifactMetadata {

	public XmlArtifactMetadata(final ArtifactCoordinates coords) {
		super(coords);
	}

	public XmlArtifactMetadata(final String groupId, final String artifactId) {
		super(groupId, artifactId);
	}

	public XmlArtifactMetadata(final NavigableSet<ComparableVersion> versions, final String groupId,
			final String artifactId) {
		super(versions, groupId, artifactId);
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

			// ignore "latest" and "release" -> they are computed!

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

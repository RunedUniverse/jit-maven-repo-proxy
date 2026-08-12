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
package net.runeduniverse.lib.repo.maven.validation.cyclonedx;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.Property;
import org.cyclonedx.model.Component.Scope;
import org.cyclonedx.model.Hash;

public class ComponentIndex {

	protected final Map<String, Component> purlComponents = new ConcurrentHashMap<>();
	protected final Map<String, Map<String, Map<String, Collection<Component>>>> gavComponents = new ConcurrentHashMap<>();

	public void addBom(final Bom bom) {
		addBom(bom, c -> true);
	}

	public void addBom(final Bom bom, final Predicate<Component> filter) {
		final Metadata metadata = bom.getMetadata();
		if (metadata != null) {
			addComponent(metadata.getComponent(), filter);
		}

		final List<Component> col = bom.getComponents();
		if (col != null) {
			for (Component component : col)
				addComponent(component, filter);
		}
	}

	public boolean addComponent(final Component component) {
		if (component == null)
			return false;
		trimComponentData(component);
		boolean updated = this.purlComponents.putIfAbsent(component.getPurl()
				.trim(), component) == null;
		if (!updated)
			return false;

		this.gavComponents.computeIfAbsent(component.getGroup(), k -> new ConcurrentHashMap<>())
				.computeIfAbsent(component.getName(), k -> new ConcurrentHashMap<>())
				.computeIfAbsent(component.getVersion(), k -> new ConcurrentLinkedQueue<>())
				.add(component);

		return updated;
	}

	public boolean addComponent(final Component component, final Predicate<Component> filter) {
		if (filter.test(component))
			return addComponent(component);
		return false;
	}

	public Collection<Component> getComponents() {
		return Collections.unmodifiableCollection(this.purlComponents.values());
	}

	public Component getComponentByPURL(final String purl) {
		return this.purlComponents.get(purl);
	}

	public Map<String, Collection<Component>> getComponentsByGA(final String groupId, final String artifactId) {
		return this.gavComponents.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(artifactId, k -> new ConcurrentHashMap<>());
	}

	public Collection<Component> getComponentsByGAV(final String groupId, final String artifactId,
			final String version) {
		return this.gavComponents.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(artifactId, k -> new ConcurrentHashMap<>())
				.computeIfAbsent(version, k -> new ConcurrentLinkedQueue<>());
	}

	public static Predicate<Component> ignoreExcludedDependency() {
		return new Predicate<Component>() {

			@Override
			public boolean test(final Component component) {
				if (component == null)
					return false;

				final Scope scope = component.getScope();
				if (scope != null && scope == Scope.EXCLUDED)
					return false;
				return true;
			}
		};
	}

	public static Predicate<Component> ignoreTestDependency() {
		return new Predicate<Component>() {

			@Override
			public boolean test(final Component component) {
				if (component == null)
					return false;

				if (ComponentIndex.getProperties(component)
						.getOrDefault("maven.scopes", "")
						.contains("test"))
					return false;
				return true;
			}
		};
	}

	public static Map<String, String> getProperties(final Component component) {
		final Map<String, String> properties = new LinkedHashMap<>();
		final List<Property> propList = component.getProperties();
		if (propList != null) {
			for (Property property : propList) {
				final String key = StringUtils.trimToNull(property.getName());
				if (key == null)
					continue;
				properties.put(key, property.getValue());
			}
		}
		return properties;
	}

	// NOTE: trim all component values!
	// autoformat may put a linebreak into the xml-element!
	@SuppressWarnings("deprecation")
	public static void trimComponentData(final Component component) {
		component.setBomRef(StringUtils.trimToNull(component.getBomRef()));
		component.setPurl(StringUtils.trimToNull(component.getPurl()));

		component.setMimeType(StringUtils.trimToNull(component.getMimeType()));
		component.setAuthor(StringUtils.trimToNull(component.getAuthor()));

		component.setPublisher(StringUtils.trimToNull(component.getPublisher()));
		component.setGroup(StringUtils.trimToNull(component.getGroup()));
		component.setName(StringUtils.trimToNull(component.getName()));
		component.setVersion(StringUtils.trimToNull(component.getVersion()));
		component.setDescription(StringUtils.trimToNull(component.getDescription()));
		component.setCopyright(StringUtils.trimToNull(component.getCopyright()));
		component.setCpe(StringUtils.trimToNull(component.getCpe()));

		// replace hash entries with equivalent trimmed entries, or remove empty ones
		final List<Hash> hashes = component.getHashes();
		if (hashes != null) {
			for (ListIterator<Hash> i = hashes.listIterator(); i.hasNext();) {
				final Hash hash = i.next();
				final String value = StringUtils.trimToNull(hash.getValue());
				if (value == null)
					i.remove();
				else
					i.set(new Hash(hash.getAlgorithm(), value));
			}
		}
	}
}

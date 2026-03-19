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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Metadata;

public class ComponentIndex {

	protected final Map<String, Component> purlComponents = new ConcurrentHashMap<>();
	protected final Map<String, Map<String, Map<String, Collection<Component>>>> gavComponents = new ConcurrentHashMap<>();

	public void addBom(final Bom bom) {
		final Metadata metadata = bom.getMetadata();
		if (metadata != null) {
			addComponent(metadata.getComponent());
		}

		for (Component component : bom.getComponents()) {
			addComponent(component);
		}
	}

	public boolean addComponent(final Component component) {
		if (component == null)
			return false;
		boolean updated = this.purlComponents.putIfAbsent(component.getPurl(), component) == null;
		if (!updated)
			return false;

		this.gavComponents.computeIfAbsent(component.getGroup(), k -> new ConcurrentHashMap<>())
				.computeIfAbsent(component.getName(), k -> new ConcurrentHashMap<>())
				.computeIfAbsent(component.getVersion(), k -> new ConcurrentLinkedQueue<>())
				.add(component);

		return updated;
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
}

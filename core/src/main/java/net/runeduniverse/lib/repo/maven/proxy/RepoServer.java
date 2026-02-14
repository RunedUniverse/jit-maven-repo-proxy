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
package net.runeduniverse.lib.repo.maven.proxy;

import java.util.LinkedHashMap;
import java.util.Map;

import net.runeduniverse.lib.repo.maven.proxy.api.RepositoryInstance;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositoryServer;

public class RepoServer implements RepositoryServer {

	protected final Map<String, RepositoryInstance> instances = new LinkedHashMap<>();

	public void addInstance(final RepositoryInstance instance) {
		this.instances.put(instance.getPath(), instance);
	}

}

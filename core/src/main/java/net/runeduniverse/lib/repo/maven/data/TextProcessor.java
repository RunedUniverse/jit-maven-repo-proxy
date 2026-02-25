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
package net.runeduniverse.lib.repo.maven.data;

import java.nio.charset.StandardCharsets;

public class TextProcessor extends AContentProcessor<String> {

	protected final boolean toLower;
	protected StringBuilder text = null;

	public TextProcessor() {
		this(false);
	}

	public TextProcessor(boolean toLower) {
		super();
		this.toLower = toLower;
	}

	@Override
	public void process(byte[] bytes) {
		// null is ignored
		if (isDone() || bytes == null)
			return;
		// init on first data
		if (this.text == null)
			this.text = new StringBuilder();
		final String content = new String(bytes, StandardCharsets.UTF_8);
		this.text.append(this.toLower ? content.toLowerCase() : content);
	}

	@Override
	public void complete() {
		// if no data was ever written, null is returned
		this.future.complete(this.text == null ? null : this.text.toString());
	}
}

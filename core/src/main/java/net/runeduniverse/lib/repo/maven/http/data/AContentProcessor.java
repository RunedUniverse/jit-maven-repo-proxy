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
package net.runeduniverse.lib.repo.maven.http.data;

import java.util.concurrent.CompletableFuture;

public abstract class AContentProcessor<T> {

	protected final CompletableFuture<T> future;

	public AContentProcessor() {
		this.future = new CompletableFuture<T>();
	}

	public CompletableFuture<T> future() {
		return this.future;
	}

	public boolean hasCompleted() {
		return this.future.isDone() || this.future.isCancelled();
	}

	public abstract void process(byte[] bytes);

	public abstract void complete();

	public void completeExceptionally(final Throwable ex) {
		this.future.completeExceptionally(ex);
	}
}

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
package net.runeduniverse.lib.repo.maven.http;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import net.runeduniverse.lib.repo.maven.data.AContentProcessor;
import net.runeduniverse.lib.repo.maven.error.RepoException;
import net.runeduniverse.lib.repo.maven.http.data.HttpDataRequest;

public class RequestingChannelFutureListener implements ChannelFutureListener {

	protected final AtomicInteger retryCnt = new AtomicInteger(0);

	protected final Bootstrap bootstrap;
	protected final HttpDataRequest dataRequest;
	protected final int maxRetries;
	protected final AContentProcessor<?> processor;

	protected Consumer<HttpDataRequest> after = null;

	public RequestingChannelFutureListener(final Bootstrap bootstrap, final HttpDataRequest dataRequest,
			final int maxRetries) {
		this.bootstrap = bootstrap;
		this.dataRequest = dataRequest;
		this.maxRetries = maxRetries;
		this.processor = dataRequest.processor();
	}

	public RequestingChannelFutureListener setAfter(final Consumer<HttpDataRequest> after) {
		this.after = after;
		return this;
	}

	@Override
	public void operationComplete(final ChannelFuture future) throws Exception {
		if (!future.isSuccess()) {
			retry();
			return;
		}

		final Channel ch = future.channel();

		ch.writeAndFlush(this.dataRequest.asHttpRequest());

		ch.closeFuture()
				.addListener(f -> {
					if (this.processor.isDone()) {
						RequestingChannelFutureListener.this.execAfter();
						return;
					}
					RequestingChannelFutureListener.this.retry();
				});
	}

	protected void retry() {
		if (this.maxRetries < this.retryCnt.incrementAndGet()) {
			// ok, we are done trying!
			this.processor.completeExceptionally(new RepoException("Max Retries exceeded!"));
			execAfter();
			return;
		}
		// retry
		this.bootstrap.connect(this.dataRequest.getHost(), this.dataRequest.getPort())
				.addListener(RequestingChannelFutureListener.this);
	}

	protected void execAfter() {
		if (this.after == null)
			return;
		this.after.accept(this.dataRequest);
	}
}

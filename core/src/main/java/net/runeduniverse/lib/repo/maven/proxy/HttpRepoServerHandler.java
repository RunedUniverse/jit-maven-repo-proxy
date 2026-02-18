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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.CharsetUtil;
import net.runeduniverse.lib.repo.maven.proxy.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.proxy.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.proxy.api.FileContentType;
import net.runeduniverse.lib.repo.maven.proxy.api.RepositoryInstance;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.lang3.StringUtils;

import io.netty.buffer.Unpooled;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class HttpRepoServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	protected static final Pattern PATTERN_GROUP_ID = Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$");

	protected static final Pattern PATTERN_ARTIFACT_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]*$");

	protected static final Pattern PATTERN_VERSION = Pattern.compile("^[0-9A-Za-z-]+([._-][0-9A-Za-z-]+)*$");

	protected static final Pattern PATTERN_CLASSIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]*$");

	protected final Function<String, RepositoryInstance> repoProvider;
	protected final Map<String, String> fType2cTypeMap;
	protected final Map<String, FileContentType> fTypeMap;

	public HttpRepoServerHandler(final Function<String, RepositoryInstance> repoProvider,
			final Map<String, String> fType2cTypeMap, final Map<String, FileContentType> fTypeMap) {
		this.repoProvider = repoProvider;
		this.fType2cTypeMap = fType2cTypeMap;
		this.fTypeMap = fTypeMap;
	}

	@Override
	protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
		// disable pipelineing
		ctx.channel()
				.config()
				.setAutoRead(false);

		if (!request.decoderResult()
				.isSuccess()) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}

		if (!GET.equals(request.method())) {
			sendError(ctx, request, METHOD_NOT_ALLOWED);
			return;
		}

		final QueryStringDecoder decoder = new QueryStringDecoder(request.uri());

		final LinkedList<String> pathFragments = Arrays.stream(decoder.path()
				.split("/"))
				.collect(Collectors.toCollection(LinkedList::new));
		if (pathFragments.size() < 4) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}

		final String repoPath = StringUtils.trimToNull(pathFragments.pollFirst());
		final RepositoryInstance repoInst = this.repoProvider.apply(repoPath);

		final String fileName = StringUtils.trimToNull(pathFragments.pollLast());

		if (repoInst == null || fileName == null) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}

		// maven repo structure example »
		// https://repo1.maven.org/maven2/net/runeduniverse/lib/utils/utils-common/

		if (fileName.startsWith("maven-metadata.xml")) {
			handleMavenMetadata(ctx, request, repoInst, fileName, pathFragments);
		} else {
			handleArtifact(ctx, request, repoInst, fileName, pathFragments);
		}
	}

	protected void handleMavenMetadata(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final RepositoryInstance repoInst, final String fileName, final LinkedList<String> pathFragments) {
		final String artifactId = StringUtils.trimToEmpty(pathFragments.pollLast());
		final String groupId = StringUtils.trimToEmpty(String.join(".", pathFragments));
		final String fileType;
		final String extension;
		final boolean isChecksum;

		// validate
		if (!PATTERN_ARTIFACT_ID.matcher(artifactId)
				.matches()
				|| !PATTERN_GROUP_ID.matcher(groupId)
						.matches()) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}
		{
			// split fileName = maven-metadata.<ext>.<fileType> | <name>.<ext=fileType>
			final LinkedList<String> splitExt = new LinkedList<>();
			for (String part : fileName.split(".")) {
				if (StringUtils.isEmpty(part)) {
					sendError(ctx, request, BAD_REQUEST);
					return;
				}
				splitExt.add(part);
			}
			if (splitExt.size() < 2) {
				sendError(ctx, request, BAD_REQUEST);
				return;
			}
			splitExt.removeFirst();
			if (FileContentType.CHECKSUM == this.fTypeMap.getOrDefault(splitExt.getLast(), FileContentType.DATA)) {
				isChecksum = true;
				fileType = splitExt.pollLast();
				extension = String.join(".", splitExt);
			} else {
				isChecksum = false;
				extension = fileType = String.join(".", splitExt);
			}
		}

		if (!"xml".equals(extension)) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}

		final CompletableFuture<ArtifactMetadata> artifactFuture = //
				repoInst.getMetadata(groupId, artifactId);

		artifactFuture.whenCompleteAsync((metadata, throwable) -> {
			if (!ctx.channel()
					.isActive()) {
				// client disconnected
				return;
			}
			if (throwable != null) {
				// handle errors
				// TODO -> if validation failed it -> FORBIDDEN (or similar)
				sendError(ctx, request, INTERNAL_SERVER_ERROR);
				return;
			}

			final String textData = toXml(metadata);

			if (isChecksum) {
				// calculate checksum
				final byte[] utf8Data = textData.getBytes(StandardCharsets.UTF_8);
				final String checksum;
				try {
					checksum = Hex.encodeHexString(//
							MessageDigest.getInstance(findAlgorithm(fileType))
									.digest(utf8Data));
				} catch (NoSuchAlgorithmException e) {
					sendError(ctx, request, NOT_FOUND);
					return;
				}
				sendTextData(ctx, request, checksum, fileName, fileType);
			} else {
				// send maven-metadata.xml content
				sendTextData(ctx, request, textData, fileName, fileType);
			}
		}, ctx.executor());
	}

	protected void handleArtifact(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final RepositoryInstance repoInst, final String fileName, final LinkedList<String> pathFragments) {
		final String version = StringUtils.trimToEmpty(pathFragments.pollLast());
		final String artifactId = StringUtils.trimToEmpty(pathFragments.pollLast());
		final String groupId = StringUtils.trimToEmpty(String.join(".", pathFragments));
		final String classifier;
		final FileContentType contentType;
		final String fileType;
		final String extension;

		// validate
		if (!PATTERN_ARTIFACT_ID.matcher(artifactId)
				.matches()
				|| !PATTERN_GROUP_ID.matcher(groupId)
						.matches()
				|| !PATTERN_VERSION.matcher(version)
						.matches()) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}
		{
			// split fileName = <artifactId>-<version><other>
			final String coreName = artifactId + '-' + version;
			final int nameSplit = coreName.length();
			final int fileNameLength = fileName.length();
			if (!fileName.startsWith(coreName) || (nameSplit + 1) < fileNameLength) {
				sendError(ctx, request, BAD_REQUEST);
				return;
			}
			final String other = fileName.substring(nameSplit, fileNameLength);
			// split other = <nameElements>.<ext>.<fileType> | <nameElements>.<ext=fileType>
			final LinkedList<String> splitExt = new LinkedList<>();
			for (String part : other.split("."))
				splitExt.add(part);
			if (splitExt.size() < 2) {
				sendError(ctx, request, BAD_REQUEST);
				return;
			}
			// split nameElements = "" | "-<classifier>"
			final String nameElements = splitExt.pollFirst();
			final int nameElementsLength = nameElements.length();
			if (0 < nameElementsLength) {
				if (nameElements.charAt(0) != '-') {
					sendError(ctx, request, BAD_REQUEST);
					return;
				}
				classifier = nameElements.substring(1, nameElementsLength);
				if (!PATTERN_CLASSIFIER.matcher(classifier)
						.matches()) {
					sendError(ctx, request, BAD_REQUEST);
					return;
				}
			} else
				classifier = null;
			// identify fileType
			contentType = this.fTypeMap.getOrDefault(splitExt.getLast(), FileContentType.DATA);
			switch (contentType) {
			case SIGNATURE:
			case CHECKSUM:
				fileType = splitExt.pollLast();
				extension = String.join(".", splitExt);
				break;
			default:
				fileType = extension = String.join(".", splitExt);
				break;
			}
		}

		final CompletableFuture<ArtifactData> artifactFuture = //
				repoInst.getArtifact(groupId, artifactId, classifier, extension, version);

		artifactFuture.whenCompleteAsync((data, throwable) -> {
			if (!ctx.channel()
					.isActive()) {
				// client disconnected
				return;
			}
			if (throwable != null) {
				// handle errors
				// TODO -> if validation failed it -> FORBIDDEN (or similar)
				sendError(ctx, request, INTERNAL_SERVER_ERROR);
				return;
			}

			if (fileType == extension) {
				// artifact
				sendFileData(ctx, request, data.getArtifactPath(), fileName, fileType);
			} else if (contentType == FileContentType.SIGNATURE) {
				// asc = pgp-signature
				sendFileData(ctx, request, data.getSignaturePath(), fileName, fileType);
			} else {
				// checksums
				final String textData = data.getHashes()
						.get(fileType);
				if (textData == null) {
					sendError(ctx, request, NOT_FOUND);
					return;
				}
				sendTextData(ctx, request, textData, fileName, fileType);
			}
		}, ctx.executor());
	}

	protected void sendFileData(final ChannelHandlerContext ctx, final FullHttpRequest request, final Path dataPath,
			final String fileName, final String fileType) {
		FileChannel fileChannel;
		final long fileLength;
		try {
			fileChannel = FileChannel.open(dataPath, StandardOpenOption.READ);
			fileLength = fileChannel.size();
		} catch (IOException e) {
			sendError(ctx, request, NOT_FOUND);
			return;
		}

		final HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		setContentHeaders(response, fileLength, fileName, fileType);
		setCommonHeaders(request, response);

		// Write the initial line and the header
		ctx.write(response);

		// Write the content
		final ChannelFuture sendFileFuture = ctx.write(new DefaultFileRegion(fileChannel, 0, fileLength));
		sendFileFuture.addListener(f -> {
			try {
				fileChannel.close();
			} catch (IOException ignored) {
			}
		});
		sendEnd(ctx, request);
	}

	protected void sendTextData(final ChannelHandlerContext ctx, final FullHttpRequest request, final String textData,
			final String fileName, final String fileType) {

		final byte[] utf8Data = textData.getBytes(StandardCharsets.UTF_8);

		final HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		setContentHeaders(response, utf8Data.length, fileName, fileType);
		setCommonHeaders(request, response);

		// Write the initial line and the header
		ctx.write(response);

		// Write the content
		ctx.write(Unpooled.wrappedBuffer(utf8Data));
		sendEnd(ctx, request);
	}

	protected void setContentHeaders(final HttpResponse response, final long length, final String fileName,
			final String fileType) {
		final HttpHeaders headers = response.headers();

		headers.set(HttpHeaderNames.CONTENT_LENGTH, length);

		headers.set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

		headers.set(HttpHeaderNames.CONTENT_TYPE,
				this.fType2cTypeMap.getOrDefault(fileType, "application/octet-stream"));
		headers.set("X-Content-Type-Options", "nosniff");
	}

	protected void setCommonHeaders(final FullHttpRequest request, final HttpResponse response) {
		final HttpHeaders headers = response.headers();

		if (!HttpUtil.isKeepAlive(request)) {
			headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (request.protocolVersion()
				.equals(HTTP_1_0)) {
			headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}
	}

	protected void sendEnd(final ChannelHandlerContext ctx, final FullHttpRequest request) {
		// Write the end marker
		final ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);

		// Decide whether to close the connection or not
		if (!HttpUtil.isKeepAlive(request)) {
			// Close the connection when the whole content is written out
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
		// unlock for next read
		ctx.channel()
				.config()
				.setAutoRead(true);
	}

	protected void sendError(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final HttpResponseStatus status) {
		final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
				Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
		response.headers()
				.set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

		sendAndCleanupConnection(ctx, request, response);
	}

	protected void sendAndCleanupConnection(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final FullHttpResponse response) {
		final boolean keepAlive = HttpUtil.isKeepAlive(request);
		HttpUtil.setContentLength(response, response.content()
				.readableBytes());
		if (!keepAlive) {
			// We're going to close the connection as soon as the response is sent,
			// so we should also make it clear for the client
			response.headers()
					.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
		} else if (request.protocolVersion()
				.equals(HTTP_1_0)) {
			response.headers()
					.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}

		final ChannelFuture flushPromise = ctx.writeAndFlush(response);

		if (!keepAlive) {
			// Close the connection as soon as the response is sent
			flushPromise.addListener(ChannelFutureListener.CLOSE);
		}
		ctx.channel()
				.config()
				.setAutoRead(true);
	}

	protected String toXml(final ArtifactMetadata metadata) {
		final StringBuilder text = new StringBuilder("<metadata>\n");

		text.append("  <groupId>")
				.append(metadata.getGroupId())
				.append("</groupId>\n");
		text.append("  <artifactId>")
				.append(metadata.getArtifactId())
				.append("</artifactId>\n");

		text.append("  <versioning>\n");
		final String latest = metadata.getLatestVersion();
		if (StringUtils.isNotEmpty(latest)) {
			text.append("    <latest>")
					.append(latest)
					.append("</latest>\n");
		}
		final String release = metadata.getReleaseVersion();
		if (StringUtils.isNotEmpty(release)) {
			text.append("    <release>")
					.append(release)
					.append("</release>\n");
		}

		text.append("    <versions>\n");
		for (String version : metadata.getVersions()) {
			text.append("      <version>")
					.append(version)
					.append("</version>\n");
		}
		text.append("    </versions>\n");

		final String lastUpdated = metadata.getLastUpdated();
		if (StringUtils.isNotEmpty(lastUpdated)) {
			text.append("    <lastUpdated>")
					.append(lastUpdated)
					.append("</lastUpdated>\n");
		}

		return text.append("  </versioning>\n</metadata>\n")
				.toString();
	}

	protected String findAlgorithm(final String fileType) throws NoSuchAlgorithmException {
		switch (fileType) {
		case "md5":
			return MessageDigestAlgorithms.MD5;
		case "sha1":
			return MessageDigestAlgorithms.SHA_1;
		case "sha256":
			return MessageDigestAlgorithms.SHA_256;
		case "sha512":
			return MessageDigestAlgorithms.SHA_512;
		default:
			throw new NoSuchAlgorithmException();
		}
	}
}

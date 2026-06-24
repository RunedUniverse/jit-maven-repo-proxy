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
package net.runeduniverse.lib.repo.maven.server.http;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import net.runeduniverse.lib.repo.maven.api.ArtifactCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactData;
import net.runeduniverse.lib.repo.maven.api.ArtifactDataCoordinates;
import net.runeduniverse.lib.repo.maven.api.ArtifactMetadata;
import net.runeduniverse.lib.repo.maven.api.ArtifactProvider;
import net.runeduniverse.lib.repo.maven.api.ChecksumType;
import net.runeduniverse.lib.repo.maven.api.FileContentType;
import net.runeduniverse.lib.repo.maven.api.FileType;
import net.runeduniverse.lib.repo.maven.api.FileTypeIndex;
import net.runeduniverse.lib.repo.maven.api.PluginEntry;
import net.runeduniverse.lib.repo.maven.error.ForbiddenArtifactException;
import net.runeduniverse.lib.repo.maven.error.NotFoundArtifactException;
import net.runeduniverse.lib.repo.maven.error.UnauthorizedArtifactException;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;

import io.netty.buffer.Unpooled;

import static io.netty.handler.codec.http.HttpMethod.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

public class HttpRepoServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	private final static Logger logger = Logger.getLogger(HttpRepoServerHandler.class.getCanonicalName());

	protected static final Pattern PATTERN_GROUP_ID = Pattern
			.compile("^[A-Za-z0-9]+([_-][A-Za-z0-9]+)*(\\.[A-Za-z0-9]+([_-][A-Za-z0-9]+)*)*$");

	protected static final Pattern PATTERN_ARTIFACT_ID = Pattern.compile("^[A-Za-z0-9]+([._-][A-Za-z0-9]+)*$");

	protected static final Pattern PATTERN_VERSION = Pattern.compile("^[A-Za-z0-9]+([._-][A-Za-z0-9]+)*$");

	protected static final Pattern PATTERN_CLASSIFIER = Pattern.compile("^[A-Za-z0-9]+([_-][A-Za-z0-9]+)*$");

	protected final FileTypeIndex typeIndex;
	protected final ArtifactProvider artifactProvider;

	public HttpRepoServerHandler(final FileTypeIndex typeIndex) {
		this(typeIndex, null);
	}

	public HttpRepoServerHandler(final FileTypeIndex typeIndex, final ArtifactProvider artifactProvider) {
		this.typeIndex = typeIndex;
		this.artifactProvider = artifactProvider;
	}

	protected ArtifactProvider getArtifactProvider(final ChannelHandlerContext ctx) {
		final ArtifactProvider artifactProvider = ctx.channel()
				.attr(HttpServerUtils.ATTKEY_ARTIFACT_PROVIDER)
				.get();
		if (artifactProvider != null)
			return artifactProvider;
		return this.artifactProvider;
	}

	protected FileTypeIndex getTypeIndex(final ChannelHandlerContext ctx) {
		final ArtifactProvider artifactProvider = getArtifactProvider(ctx);
		if (artifactProvider instanceof FileTypeIndex) {
			// if applicable override
			return (FileTypeIndex) this.artifactProvider;
		}
		return this.typeIndex;
	}

	@Override
	protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
		// disable pipelineing
		ctx.channel()
				.config()
				.setAutoRead(false);

		final HttpMethod httpMethod = request.method();
		if (!(GET.equals(httpMethod) || HEAD.equals(httpMethod))) {
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
		// void first (it's empty) -> uri starts with /
		pathFragments.removeFirst();

		final ArtifactProvider provider = getArtifactProvider(ctx);
		final String fileName = StringUtils.trimToNull(pathFragments.pollLast());

		if (provider == null || fileName == null) {
			sendError(ctx, request, BAD_REQUEST);
			return;
		}

		// maven repo structure example »
		// https://repo1.maven.org/maven2/net/runeduniverse/lib/utils/utils-common/

		if (fileName.startsWith("maven-metadata.xml")) {
			handleMetadata(ctx, request, provider, fileName, pathFragments);
		} else {
			handleArtifact(ctx, request, provider, fileName, pathFragments);
		}
	}

	protected void handleMetadata(final ChannelHandlerContext ctx, final FullHttpRequest request,
			final ArtifactProvider provider, final String fileName, final LinkedList<String> pathFragments) {
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
			System.err.println(String.format("SERVER | Invalid Coordinates Requested!\ngroupId: %s\nartifactId:    %s", //
					groupId, artifactId));
			sendError(ctx, request, BAD_REQUEST);
			return;
		}
		{
			// split fileName = maven-metadata.<ext>.<fileType> | <name>.<ext=fileType>
			final LinkedList<String> splitExt = new LinkedList<>();
			for (String part : fileName.split("\\.")) {
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
			if (FileContentType.CHECKSUM == getTypeIndex(ctx).getByExtension(splitExt.getLast())
					.contentType()) {
				isChecksum = true;
				fileType = splitExt.pollLast();
				extension = String.join(".", splitExt);
			} else {
				isChecksum = false;
				extension = fileType = String.join(".", splitExt);
			}
		}

		if (!"xml".equals(extension)) {
			sendError(ctx, request, NOT_FOUND);
			return;
		}

		final CompletableFuture<ArtifactMetadata> artifactFuture = //
				provider.getMetadata(ArtifactCoordinates.build(groupId, artifactId));

		artifactFuture.whenCompleteAsync((metadata, throwable) -> {
			if (!ctx.channel()
					.isActive()) {
				// client disconnected
				ctx.close();
				return;
			}
			// unwrap the exception, if wrapped
			if (throwable instanceof CompletionException)
				throwable = throwable.getCause();
			// handle errors
			if (throwable != null) {
				if (throwable instanceof NotFoundArtifactException)
					sendError(ctx, request, NOT_FOUND);
				else if (throwable instanceof ForbiddenArtifactException)
					sendError(ctx, request, FORBIDDEN);
				else if (throwable instanceof UnauthorizedArtifactException)
					sendError(ctx, request, UNAUTHORIZED);
				else {
					sendError(ctx, request, INTERNAL_SERVER_ERROR);
					logger.log(Level.WARNING, "artifact metadata resolution failed!", throwable);
					throwable.printStackTrace(System.err);
				}
				return;
			}
			if (metadata == null) {
				sendError(ctx, request, NOT_FOUND);
				return;
			}

			final String textData = toXml(metadata);

			if (isChecksum) {
				// calculate checksum
				final byte[] utf8Data = textData.getBytes(StandardCharsets.UTF_8);
				final String checksum;
				try {
					checksum = Hex.encodeHexString(//
							ChecksumType.newMessageDigestFor(fileType)
									.digest(utf8Data),
							true);
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
			final ArtifactProvider provider, final String fileName, final LinkedList<String> pathFragments) {
		final String version = StringUtils.trimToEmpty(pathFragments.pollLast());
		final String artifactId = StringUtils.trimToEmpty(pathFragments.pollLast());
		final String groupId = StringUtils.trimToEmpty(String.join(".", pathFragments));
		final String classifier;
		final FileContentType contentType;
		final String fileType;
		final String extension;

		// validate
		if (!PATTERN_GROUP_ID.matcher(groupId)
				.matches()
				|| !PATTERN_ARTIFACT_ID.matcher(artifactId)
						.matches()
				|| !PATTERN_VERSION.matcher(version)
						.matches()) {
			System.err.println(String.format(
					"SERVER | Invalid Coordinates Requested!\ngroupId:    %s\nartifactId: %s\nversion:    %s", //
					groupId, artifactId, version));
			sendError(ctx, request, BAD_REQUEST);
			return;
		}
		{
			// split fileName = <artifactId>-<version><other>
			final String coreName = artifactId + '-' + version;
			final int nameSplit = coreName.length();
			final int fileNameLength = fileName.length();
			if (!fileName.startsWith(coreName) || fileNameLength <= (nameSplit + 1)) {
				sendError(ctx, request, BAD_REQUEST);
				return;
			}
			final String other = fileName.substring(nameSplit, fileNameLength);
			// split other = <nameElements>.<ext>.<fileType> | <nameElements>.<ext=fileType>
			final LinkedList<String> splitExt = new LinkedList<>();
			for (String part : other.split("\\."))
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
			contentType = getTypeIndex(ctx).getByExtension(splitExt.getLast())
					.contentType();
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

		final CompletableFuture<? extends ArtifactData> artifactFuture = //
				provider.getArtifact(
						ArtifactDataCoordinates.build(groupId, artifactId, version, classifier, extension));

		artifactFuture.whenCompleteAsync((data, throwable) -> {
			if (!ctx.channel()
					.isActive()) {
				// client disconnected
				ctx.close();
				return;
			}
			if (throwable instanceof CompletionException)
				throwable = throwable.getCause();
			if (throwable != null) {
				// handle errors
				if (throwable instanceof NotFoundArtifactException)
					sendError(ctx, request, NOT_FOUND);
				else if (throwable instanceof ForbiddenArtifactException) {
					sendError(ctx, request, FORBIDDEN);
					logger.log(Level.WARNING,
							String.format("\033[1martifact resolution forbidden!\033[0m\n%s: %s", throwable.getClass()
									.getCanonicalName(), throwable.getMessage()));
				} else if (throwable instanceof UnauthorizedArtifactException)
					sendError(ctx, request, UNAUTHORIZED);
				else {
					sendError(ctx, request, INTERNAL_SERVER_ERROR);
					logger.log(Level.WARNING, "artifact resolution failed!", throwable);
					throwable.printStackTrace(System.err);
				}
				return;
			}
			if (data == null) {
				sendError(ctx, request, NOT_FOUND);
				return;
			}

			if (fileType == extension) {
				// artifact
				sendFileData(ctx, request, data.getArtifactPath(), fileName, fileType, data.getChecksums());
			} else if (contentType == FileContentType.SIGNATURE) {
				// asc = pgp-signature
				sendFileData(ctx, request, data.getSignaturePath(), fileName, fileType, data.getChecksums());
			} else {
				// checksums
				final ChecksumType checksumType = ChecksumType.findByExtension(fileType);
				final String textData = checksumType == null ? null
						: data.getChecksums()
								.get(checksumType.algorithm());
				if (textData == null) {
					sendError(ctx, request, NOT_FOUND);
					return;
				}
				sendTextData(ctx, request, textData, fileName, fileType);
			}
		}, ctx.executor());
	}

	protected void sendFileData(final ChannelHandlerContext ctx, final FullHttpRequest request, final Path dataPath,
			final String fileName, final String fileType, final Map<String, String> checksums) {
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
		setContentHeaders(ctx, response, fileLength, fileName, fileType);
		// setChecksumHeaders(response, checksums);
		setCommonHeaders(request, response);

		// Write the initial line and the header
		ctx.write(response);

		// Write the content
		if (GET.equals(request.method())) {
			// if HEAD -> omit content
			final ChannelFuture sendFileFuture = ctx.write(new DefaultFileRegion(fileChannel, 0, fileLength));
			sendFileFuture.addListener(f -> {
				try {
					fileChannel.close();
				} catch (IOException ignored) {
				}
			});
		}
		sendEnd(ctx, request);
	}

	protected void sendTextData(final ChannelHandlerContext ctx, final FullHttpRequest request, final String textData,
			final String fileName, final String fileType) {

		final byte[] utf8Data = textData.getBytes(StandardCharsets.UTF_8);

		final HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		setContentHeaders(ctx, response, utf8Data.length, fileName, fileType);
		setCommonHeaders(request, response);

		// Write the initial line and the header
		ctx.write(response);

		// Write the content
		if (GET.equals(request.method())) {
			// if HEAD -> omit content
			ctx.write(Unpooled.wrappedBuffer(utf8Data));
		}
		sendEnd(ctx, request);
	}

	protected void setContentHeaders(final ChannelHandlerContext ctx, final HttpResponse response, final long length,
			final String fileName, final String fileType) {
		final HttpHeaders headers = response.headers();

		headers.set(HttpHeaderNames.CONTENT_LENGTH, length);

		headers.set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

		headers.set(HttpHeaderNames.CONTENT_TYPE, getTypeIndex(ctx).getByExtension(fileType)
				.getMetadata(String.class, FileType.META_HTTP_CONTENT_TYPE_HEADER, "application/octet-stream"));
		headers.set("X-Content-Type-Options", "nosniff");
	}

	/**
	 * Set Checksum headers: x-checksum-*
	 *
	 * @param headers
	 * @param checksums
	 * @deprecated Netty currently has a bug that sends the internal map of
	 *             HttpHeaders into an endless spin-loop!
	 */
	protected void setChecksumHeaders(final HttpResponse response, final Map<String, String> checksums) {
		final HttpHeaders headers = response.headers();

		for (Entry<String, String> entry : checksums.entrySet()) {
			headers.set("x-checksum-" + entry.getKey(), entry.getValue());
		}
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
		HttpServerUtils.sendError(ctx, request, status);
		ctx.channel()
				.config()
				.setAutoRead(true);
	}

	protected String toXml(final ArtifactMetadata metadata) {
		final boolean versionIndex = !metadata.getVersions()
				.isEmpty();
		final boolean pluginIndex = !metadata.getPlugins()
				.isEmpty();

		final StringBuilder text = new StringBuilder("<metadata>\n");

		if (versionIndex) {
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
			text.append("  </versioning>\n");
		}

		if (pluginIndex) {
			text.append("  <plugins>\n");
			for (PluginEntry entry : metadata.getPlugins()) {
				text.append("    <plugin>\n")
						.append("      <name>")
						.append(entry.getName())
						.append("</name>\n")
						.append("      <prefix>")
						.append(entry.getPrefix())
						.append("</prefix>\n")
						.append("      <artifactId>")
						.append(entry.getArtifactId())
						.append("</artifactId>\n")
						.append("    </plugin>\n");
			}
			text.append("  </plugins>\n");
		}

		return text.append("</metadata>\n")
				.toString();
	}
}

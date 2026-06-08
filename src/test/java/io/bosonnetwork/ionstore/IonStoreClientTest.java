/*
 * Copyright (c) 2023 -      bosonnetwork.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.bosonnetwork.ionstore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.bosonnetwork.Id;
import io.bosonnetwork.crypto.Hash;
import io.bosonnetwork.crypto.Signature;

/**
 * Unit tests for the {@link IonStore} client that exercise the download integrity check against a
 * minimal in-process HTTP server (no DHT or full service required), plus {@link IonObject} JSON
 * parsing.
 */
class IonStoreClientTest {
	private static final byte[] PAYLOAD = "Hello, Ion Store!".getBytes(StandardCharsets.UTF_8);

	// Path-segment ids that select the server's behavior.
	private final Id goodId = Id.random();
	private final Id badId = Id.random();
	private final Id missingHeaderId = Id.random();

	private Vertx vertx;
	private HttpServer server;
	private IonStore client;

	@BeforeEach
	void setUp() throws Exception {
		vertx = Vertx.vertx();

		Id correctContentId = Id.of(Hash.sha256(PAYLOAD));
		Id wrongContentId = Id.random();

		server = vertx.createHttpServer().requestHandler(req -> {
			String path = req.path();
			String id = path.substring(path.lastIndexOf('/') + 1);
			HttpServerResponse resp = req.response();

			if (id.equals(goodId.toString())) {
				resp.putHeader("Content-Type", "application/octet-stream");
				resp.putHeader("Ion-Content-Id", correctContentId.toString());
				resp.putHeader("Ion-Expire-At", "0");
				resp.putHeader("Ion-Filename", "greeting.txt"); // custom Ion-* metadata
				resp.end(Buffer.buffer(PAYLOAD));
			} else if (id.equals(badId.toString())) {
				resp.putHeader("Ion-Content-Id", wrongContentId.toString());
				resp.end(Buffer.buffer(PAYLOAD));
			} else if (id.equals(missingHeaderId.toString())) {
				resp.end(Buffer.buffer(PAYLOAD)); // no Ion-Content-Id
			} else {
				resp.setStatusCode(404).end();
			}
		});

		int port = server.listen(0).toCompletionStage().toCompletableFuture()
				.get(5, TimeUnit.SECONDS).actualPort();

		client = IonStore.builder()
				.vertx(vertx)
				.userKey(Signature.KeyPair.random())
				.servicePeerId(Id.random())
				.serviceUrl("http://localhost:" + port)
				.build();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (client != null && !client.isClosed())
			client.close().get(5, TimeUnit.SECONDS);
		if (server != null)
			server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
		if (vertx != null)
			vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
	}

	@Test
	void downloadVerifiesIntegrityAndReturnsContent() throws Exception {
		BytesIonObject result = client.get(goodId)
				.toCompletableFuture().get(5, TimeUnit.SECONDS);

		assertEquals(PAYLOAD.length, result.getContent().length());
		assertEquals(Buffer.buffer(PAYLOAD), result.getContent());
		assertArrayEquals(PAYLOAD, result.getBytes());
		assertEquals(Id.of(Hash.sha256(PAYLOAD)), result.getContentId());
		assertEquals(goodId, result.getId());
		assertEquals(PAYLOAD.length, result.getSize());
		// custom Ion-* header surfaced as metadata
		assertEquals("greeting.txt", result.getMetadata().get("Ion-Filename"));
	}

	@Test
	void downloadRejectsContentIdMismatch() {
		ExecutionException e = assertThrows(ExecutionException.class,
				() -> client.get(badId).toCompletableFuture().get(5, TimeUnit.SECONDS));
		assertInstanceOf(ObjectIntegrityException.class, e.getCause());
	}

	@Test
	void downloadRejectsMissingContentIdHeader() {
		ExecutionException e = assertThrows(ExecutionException.class,
				() -> client.get(missingHeaderId).toCompletableFuture().get(5, TimeUnit.SECONDS));
		assertInstanceOf(ObjectIntegrityException.class, e.getCause());
	}

	@Test
	void downloadReturnsNullWhenNotFound() throws Exception {
		BytesIonObject result = client.get(Id.random())
				.toCompletableFuture().get(5, TimeUnit.SECONDS);
		assertNull(result);
	}

	@Test
	void objectMetadataParsesFromJson() {
		Id id = Id.random();
		Id contentId = Id.random();
		JsonObject json = new JsonObject()
				.put("id", id.toString())
				.put("contentId", contentId.toString())
				.put("name", "file.bin")
				.put("size", 1234L)
				.put("contentType", "application/octet-stream")
				.put("encrypted", true)
				.put("expireAt", 1700000000L)
				.put("metadata", new JsonObject().put("Ion-Tag", "v1"))
				.put("uri", "ions://" + id + "/" + id);

		IonObject meta = IonObject.fromJson(json);
		assertEquals(id, meta.getId());
		assertEquals(contentId, meta.getContentId());
		assertEquals("file.bin", meta.getName());
		assertEquals(1234L, meta.getSize());
		assertEquals("application/octet-stream", meta.getContentType());
		assertTrue(meta.isEncrypted());
		assertEquals(1700000000L, meta.getExpireAt());
		assertEquals(Map.of("Ion-Tag", "v1"), meta.getMetadata());
	}
}
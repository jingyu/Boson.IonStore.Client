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

/**
 * Thrown when a downloaded object fails its integrity check: the SHA-256 of the received bytes does
 * not match the content id the service advertised in the {@code Ion-Content-Id} header (or that
 * header is missing or malformed).
 * <p>
 * This is a client-side verification failure, so it carries {@link #NO_HTTP_STATUS} even though it is
 * raised while handling a {@code 200} response.
 */
public class ObjectIntegrityException extends IonStoreException {
	private static final long serialVersionUID = 5894994639001863871L;

	/**
	 * Creates an integrity exception with the given detail message.
	 *
	 * @param message the detail message
	 */
	public ObjectIntegrityException(String message) {
		super(message);
	}
}

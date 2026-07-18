package io.github.gjuton.internal.parser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a JSON Schema document over HTTP(S).
 *
 * <p>This is a thin wrapper around {@link HttpClient} that performs a
 * single GET request and returns the response body. Non-2xx responses
 * and connection failures are surfaced as {@link IOException}.
 */
final class SchemaFetcher {

    private SchemaFetcher() {
    }

    /**
     * Fetches the document at the given HTTP(S) URL and returns its body
     * as a string.
     *
     * @param url an absolute HTTP or HTTPS URL
     * @throws IOException if the request fails or the server returns a
     *     non-2xx status code
     */
    static String fetch(String url) throws IOException {
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            var request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(
                        "Failed to fetch " + url + ": HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }
}

package ch.varani.jass.update;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLException;

/**
 * A client of the network replaced for the tests: it answers the requests from the answers registered by the test,
 * by address, and never opens a connection; the body of an answer is handed to the body handler of the request, as
 * the real client does. An address without answer gets the status 404.
 *
 * <p>Safe for concurrent use.</p>
 */
final class FakeHttpClient extends HttpClient {

  /** The HTTP status of a missing file. */
  static final int NOT_FOUND = 404;

  /**
   * An answer of the fake network.
   *
   * @param status the HTTP status.
   * @param body the body.
   * @param length the length announced in the header {@code Content-Length}, or {@code null} for none.
   * @param broken {@code true} if the connection breaks after the body.
   */
  record Answer(int status, byte[] body, Long length, boolean broken) {

    /**
     * Returns a successful answer, announcing its length.
     *
     * @param body the body.
     *
     * @return the answer.
     */
    static Answer ok(final byte[] body) {
      return new Answer(OK, body, (long) body.length, false);
    }

  }

  /** The HTTP status of a success. */
  static final int OK = 200;

  /** The answers, by address. */
  private final Map<URI, Answer> answers = new ConcurrentHashMap<>();
  /** The addresses whose request fails before any answer. */
  private final List<URI> unreachable = new CopyOnWriteArrayList<>();
  /** The requests sent, in order. */
  private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();

  /**
   * Registers the answer to an address.
   *
   * @param uri the address.
   * @param answer the answer.
   *
   * @return this client.
   */
  FakeHttpClient answer(final URI uri, final Answer answer) {
    answers.put(uri, answer);
    return this;
  }

  /**
   * Makes the requests to an address fail, as without network.
   *
   * @param uri the address.
   *
   * @return this client.
   */
  FakeHttpClient unreachable(final URI uri) {
    unreachable.add(uri);
    return this;
  }

  /**
   * Returns the requests sent.
   *
   * @return the requests, in order.
   */
  List<HttpRequest> requests() {
    return requests;
  }

  /**
   * Answers a request from the registered answers.
   *
   * @param request the request.
   * @param handler the handler of the body.
   * @param <T> the type of the body.
   *
   * @return the answer.
   *
   * @throws IOException if the address is unreachable.
   */
  @Override
  public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> handler)
      throws IOException {
    requests.add(request);
    if (unreachable.contains(request.uri())) {
      throw new SSLException("unreachable");
    }
    final Answer answer = answers.getOrDefault(request.uri(), new Answer(NOT_FOUND, new byte[0], null, false));
    final HttpHeaders headers = HttpHeaders.of(answer.length() == null ? Map.of()
                                               : Map.of("Content-Length", List.of(answer.length().toString())),
                                               (name, value) -> true);
    final HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {

      @Override
      public int statusCode() {
        return answer.status();
      }

      @Override
      public HttpHeaders headers() {
        return headers;
      }

      @Override
      public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
      }

    });
    subscriber.onSubscribe(new Flow.Subscription() {

      @Override
      public void request(final long n) {
        // The body is delivered at once.
      }

      @Override
      public void cancel() {
        // Nothing to release.
      }

    });
    if (answer.body().length > 0) {
      subscriber.onNext(List.of(ByteBuffer.wrap(answer.body())));
    }
    if (answer.broken()) {
      subscriber.onError(new IOException("broken"));
    } else {
      subscriber.onComplete();
    }
    final T body;
    try {
      body = subscriber.getBody().toCompletableFuture().get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new IOException(e);
    }
    return new Response<>(request, answer.status(), headers, body);
  }

  /**
   * Not used by the tests.
   *
   * @param request the request.
   * @param handler the handler of the body.
   * @param <T> the type of the body.
   *
   * @return never.
   */
  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                          final HttpResponse.BodyHandler<T> handler) {
    throw new UnsupportedOperationException();
  }

  /**
   * Not used by the tests.
   *
   * @param request the request.
   * @param handler the handler of the body.
   * @param push the handler of the pushed answers.
   * @param <T> the type of the body.
   *
   * @return never.
   */
  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                          final HttpResponse.BodyHandler<T> handler,
                                                          final HttpResponse.PushPromiseHandler<T> push) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns no cookie handler.
   *
   * @return nothing.
   */
  @Override
  public Optional<CookieHandler> cookieHandler() {
    return Optional.empty();
  }

  /**
   * Returns no time-out.
   *
   * @return nothing.
   */
  @Override
  public Optional<Duration> connectTimeout() {
    return Optional.empty();
  }

  /**
   * Returns the redirection policy of the application.
   *
   * @return {@link HttpClient.Redirect#NORMAL}.
   */
  @Override
  public Redirect followRedirects() {
    return Redirect.NORMAL;
  }

  /**
   * Returns no proxy.
   *
   * @return nothing.
   */
  @Override
  public Optional<ProxySelector> proxy() {
    return Optional.empty();
  }

  /**
   * Not used by the tests.
   *
   * @return never.
   */
  @Override
  public SSLContext sslContext() {
    throw new UnsupportedOperationException();
  }

  /**
   * Not used by the tests.
   *
   * @return never.
   */
  @Override
  public SSLParameters sslParameters() {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns no authenticator.
   *
   * @return nothing.
   */
  @Override
  public Optional<Authenticator> authenticator() {
    return Optional.empty();
  }

  /**
   * Returns the version of HTTP.
   *
   * @return HTTP/1.1.
   */
  @Override
  public HttpClient.Version version() {
    return HttpClient.Version.HTTP_1_1;
  }

  /**
   * Returns no executor.
   *
   * @return nothing.
   */
  @Override
  public Optional<Executor> executor() {
    return Optional.empty();
  }

  /**
   * An answer of the fake network.
   *
   * @param request the request.
   * @param statusCode the HTTP status.
   * @param headers the headers.
   * @param body the body.
   * @param <T> the type of the body.
   */
  private record Response<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body)
      implements HttpResponse<T> {

    /**
     * Returns no previous answer.
     *
     * @return nothing.
     */
    @Override
    public Optional<HttpResponse<T>> previousResponse() {
      return Optional.empty();
    }

    /**
     * Returns no TLS session.
     *
     * @return nothing.
     */
    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    /**
     * Returns the address of the request.
     *
     * @return the address.
     */
    @Override
    public URI uri() {
      return request.uri();
    }

    /**
     * Returns the version of HTTP.
     *
     * @return HTTP/1.1.
     */
    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }

  }

}

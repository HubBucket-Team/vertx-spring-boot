package me.snowdrop.vertx.http;

import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLSession;

import io.netty.buffer.ByteBufAllocator;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.impl.headers.VertxHttpHeaders;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpCookie;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@RunWith(MockitoJUnitRunner.class)
public class VertxServerHttpRequestTest {

    @Mock
    private RoutingContext mockRoutingContext;

    @Mock
    private HttpServerRequest mockHttpServerRequest;

    @Mock
    private SSLSession mockSslSession;

    private NettyDataBufferFactory nettyDataBufferFactory;

    private VertxServerHttpRequest vertxServerHttpRequest;

    @Before
    public void setUp() {
        given(mockRoutingContext.request()).willReturn(mockHttpServerRequest);
        given(mockHttpServerRequest.absoluteURI()).willReturn("http://localhost:8080");
        given(mockHttpServerRequest.headers()).willReturn(new VertxHttpHeaders());

        nettyDataBufferFactory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);
        vertxServerHttpRequest = new VertxServerHttpRequest(mockRoutingContext, nettyDataBufferFactory);
    }

    @Test
    public void shouldGetNativeRequest() {
        assertThat((HttpServerRequest) vertxServerHttpRequest.getNativeRequest()).isEqualTo(mockHttpServerRequest);
    }

    @Test
    public void shouldGetMethodValue() {
        given(mockHttpServerRequest.method()).willReturn(HttpMethod.GET);

        assertThat(vertxServerHttpRequest.getMethodValue()).isEqualTo("GET");
    }

    @Test
    public void shouldGetBody() {
        given(mockHttpServerRequest.pause()).willReturn(mockHttpServerRequest);
        given(mockHttpServerRequest.exceptionHandler(any())).willReturn(mockHttpServerRequest);
        given(mockHttpServerRequest.handler(any())).will(invocation -> {
            Handler<Buffer> handler = invocation.getArgument(0);
            handler.handle(Buffer.buffer("chunk 1"));
            handler.handle(Buffer.buffer("chunk 2"));
            return mockHttpServerRequest;
        });
        given(mockHttpServerRequest.endHandler(any())).will(invocation -> {
            Handler<Void> handler = invocation.getArgument(0);
            handler.handle(null);
            return mockHttpServerRequest;
        });

        StepVerifier.create(vertxServerHttpRequest.getBody())
            .expectNext(nettyDataBufferFactory.wrap("chunk 1".getBytes()))
            .expectNext(nettyDataBufferFactory.wrap("chunk 2".getBytes()))
            .verifyComplete();
    }

    @Test
    public void shouldGetRemoteAddress() {
        SocketAddress original = SocketAddress.inetSocketAddress(8080, "localhost");
        given(mockHttpServerRequest.remoteAddress()).willReturn(original);

        InetSocketAddress expected = InetSocketAddress.createUnresolved("localhost", 8080);
        assertThat(vertxServerHttpRequest.getRemoteAddress()).isEqualTo(expected);
    }

    @Test
    public void shouldInitCookies() {
        Set<Cookie> originalCookies = new HashSet<>(2);
        originalCookies.add(Cookie.cookie("cookie1", "value1"));
        originalCookies.add(Cookie.cookie("cookie2", "value2"));

        given(mockRoutingContext.cookies()).willReturn(originalCookies);

        assertThat(vertxServerHttpRequest.initCookies()).containsOnly(
            new AbstractMap.SimpleEntry<>("cookie1", Collections.singletonList(new HttpCookie("cookie1", "value1"))),
            new AbstractMap.SimpleEntry<>("cookie2", Collections.singletonList(new HttpCookie("cookie2", "value2")))
        );
    }

    @Test
    public void shouldInitSslInfo() {
        given(mockHttpServerRequest.sslSession()).willReturn(mockSslSession);

        assertThat(vertxServerHttpRequest.initSslInfo()).isInstanceOf(SslInfoImpl.class);
    }

    @Test
    public void shouldIgnoreNullSslSession() {
        assertThat(vertxServerHttpRequest.initSslInfo()).isNull();
    }
}

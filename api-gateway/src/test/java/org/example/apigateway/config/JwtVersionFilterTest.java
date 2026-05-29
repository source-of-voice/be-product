package org.example.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

class JwtVersionFilterTest {

    @Mock
    private TokenVersionService tokenVersionService;

    private JwtVersionFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new JwtVersionFilter(tokenVersionService);
    }

    @Test
    void filter_shouldAllowRequestWhenTokenVersionMatchesCurrentVersion() {
        JwtAuthenticationToken principal = jwtPrincipal("1", 2L);
        ServerWebExchange exchange = exchangeWithPrincipal(principal);

        AtomicBoolean chainCalled = new AtomicBoolean(false);

        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        when(tokenVersionService.resolveCurrentVersion("1"))
                .thenReturn(Mono.just(2L));

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assert chainCalled.get();
        verify(tokenVersionService).resolveCurrentVersion("1");
    }

    @Test
    void filter_shouldRejectRequestWhenTokenVersionDoesNotMatchCurrentVersion() {
        JwtAuthenticationToken principal = jwtPrincipal("1", 1L);
        ServerWebExchange exchange = exchangeWithPrincipal(principal);

        WebFilterChain chain = webExchange -> Mono.empty();

        when(tokenVersionService.resolveCurrentVersion("1"))
                .thenReturn(Mono.just(2L));

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                )
                .verify();
    }

    @Test
    void filter_shouldRejectJwtWithoutVersionClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(claims -> claims.putAll(Map.of("roles", "USER")))
                .build();

        JwtAuthenticationToken principal = new JwtAuthenticationToken(jwt);
        ServerWebExchange exchange = exchangeWithPrincipal(principal);

        WebFilterChain chain = webExchange -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorMatches(error ->
                        error instanceof ResponseStatusException exception
                                && exception.getStatusCode() == HttpStatus.UNAUTHORIZED
                )
                .verify();

        verifyNoInteractions(tokenVersionService);
    }

    @Test
    void filter_shouldContinueWithoutPrincipal() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);

        WebFilterChain chain = webExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assert chainCalled.get();
        verifyNoInteractions(tokenVersionService);
    }

    private JwtAuthenticationToken jwtPrincipal(String subject, Long version) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("ver", version)
                .claim("roles", java.util.List.of("USER"))
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private ServerWebExchange exchangeWithPrincipal(Principal principal) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").build()
        );

        return new ServerWebExchangeDecorator(exchange) {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Principal> Mono<T> getPrincipal() {
                return Mono.just((T) principal);
            }
        };
    }
}

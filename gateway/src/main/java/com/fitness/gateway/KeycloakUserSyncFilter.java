package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        RegisterRequest registerRequest = null;

        // FIXED: Only attempt to parse token claims if the Authorization header is actually present
        if (token != null) {
            registerRequest = getUserDetails(token);

            // FIXED: Added safety null-check to guarantee no NullPointerExceptions can fire
            if (userId == null && registerRequest != null) {
                userId = registerRequest.getKeyColckId();
                log.debug("Extracted falling back user ID from Keycloak sub claim: {}", userId);
            }
        }

        // Rule: If both pieces of identity exist, run the synchronization flow
        if (userId != null && token != null) {
            final String finalUserId = userId; // Required for lambda closure safety scoping
            final RegisterRequest finalRegisterRequest = registerRequest;

            return userService.validateUser(finalUserId)
                    .flatMap(exists -> {
                        if (!exists) {
                            log.info("User {} not found in local system. Starting sync from token claims.", finalUserId);
                            if (finalRegisterRequest != null) {
                                return userService.registerUser(finalRegisterRequest).then();
                            }
                        } else {
                            log.info("User {} already exists down stream. Skipping synchronization.", finalUserId);
                        }
                        return Mono.empty();
                    })
                    // Progress downstream with the mutated request tracking parameters
                    .then(Mono.defer(() -> proceedWithRequest(exchange, chain, finalUserId)));
        }

        // Secure fallback: If unauthenticated or missing identity data, pass along cleanly
        return chain.filter(exchange);
    }

    private Mono<Void> proceedWithRequest(ServerWebExchange exchange, WebFilterChain chain, String userId) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-ID", userId)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private RegisterRequest getUserDetails(String token) {
        try {
            String tokenWithoutBearer = token.replace("Bearer", "").trim();
            SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setEmail(claims.getStringClaim("email"));
            registerRequest.setKeyColckId(claims.getStringClaim("sub"));
            registerRequest.setPassword("dummy@123");
            registerRequest.setFirstName(claims.getStringClaim("given_name"));
            registerRequest.setLastName(claims.getStringClaim("family_name"));

            return registerRequest;
        } catch (Exception e) {
            log.error("Failed to parse and extract claims from Keycloak JWT token: {}", e.getMessage());
            return null;
        }
    }
}
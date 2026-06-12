package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Component
public class KeycloakUserSyncFilter implements WebFilter {

    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");

        String keyClockIdFromToken = null;
        RegisterRequest registerRequest = null;

        if (token != null) {
            try {
                String tokenWithoutBearer = token.replace("Bearer", "").trim();
                SignedJWT signedJWT = SignedJWT.parse(tokenWithoutBearer);
                JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

                keyClockIdFromToken = claims.getStringClaim("sub");
                log.info("Successfully extracted Keycloak sub ID directly from token: {}", keyClockIdFromToken);

                // 🪵 ADDED LOG: Print raw claims to verify they match given_name, family_name, and email keys
                log.info(">>>> [Gateway Filter] Raw JWT claims parsed: email={}, given_name={}, family_name={} <<<<",
                        claims.getStringClaim("email"),
                        claims.getStringClaim("given_name"),
                        claims.getStringClaim("family_name"));

                // Build your registration payload object
                registerRequest = new RegisterRequest();
                registerRequest.setEmail(claims.getStringClaim("email"));
                registerRequest.setPassword("dummy@123");
                registerRequest.setFirstName(claims.getStringClaim("given_name"));
                registerRequest.setLastName(claims.getStringClaim("family_name"));

                registerRequest.setKeyClockId(keyClockIdFromToken);

                // 🪵 ADDED LOG: Verify the instantiated Gateway RegisterRequest DTO state right here
                log.info(">>>> [Gateway Filter] Local RegisterRequest instance built successfully: {} <<<<", registerRequest);

            } catch (Exception e) {
                log.error("Failed to parse Keycloak JWT token parameters: {}", e.getMessage());
            }
        }

        // Execution loop verification
        if (keyClockIdFromToken != null && token != null) {
            final String finalUserId = keyClockIdFromToken;
            final RegisterRequest finalRegisterRequest = registerRequest;

            log.info("Forwarding validation call downstream for user: {}", finalUserId);

            return userService.validateUser(finalUserId)
                    .flatMap(exists -> {
                        if (!exists) {
                            log.info("Keycloak User {} not found downstream. Auto-registering.", finalUserId);
                            if (finalRegisterRequest != null) {
                                return userService.registerUser(finalRegisterRequest).then();
                            }
                        } else {
                            log.info("Keycloak User {} verified. Skipping sync step.", finalUserId);
                        }
                        return Mono.empty();
                    })
                    .then(Mono.defer(() -> proceedWithRequest(exchange, chain, finalUserId)));
        }

        log.warn("Filter bypass: Missing token or tracking ID. Header was not attached.");
        return chain.filter(exchange);
    }

    private Mono<Void> proceedWithRequest(ServerWebExchange exchange, WebFilterChain chain, String userId) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-User-ID", userId)
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
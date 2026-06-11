package com.fitness.gateway.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
// Removed @Data because a Service bean should not have getters/setters generated for state properties
public class UserService {

    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId) {
        // FIXED: Stripped away Boolean.TRUE.equals and returned the Mono container straight back
        return userServiceWebClient.get()
                .uri("api/users/{userId}/validate", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    // FIXED: Replaced standard throw blocks with clean Mono.error pipelines
                    if (e.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.NOT_FOUND)) {
                        return Mono.error(new RuntimeException("User Not Found: " + userId));
                    } else if (e.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.BAD_REQUEST)) {
                        return Mono.error(new RuntimeException("Invalid Request: " + userId));
                    }
                    return Mono.error(new RuntimeException("Unexpected verification failure: " + e.getMessage(), e));
                });
    }

    public Mono<UserResponse> registerUser(RegisterRequest registerRequest) {
        log.info("Calling User Registration API for email: {}", registerRequest.getEmail());

        return userServiceWebClient.post()
                .uri("api/users/register")
                .bodyValue(registerRequest)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    HttpStatusCode status = e.getStatusCode();

                    // FIXED: Replaced non-existent 'userId' reference with 'registerRequest.getEmail()'
                    if (status.isSameCodeAs(org.springframework.http.HttpStatus.BAD_REQUEST)) {
                        return Mono.error(new RuntimeException("Bad Registration Request for: " + registerRequest.getEmail()));
                    } else if (status.isSameCodeAs(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)) {
                        return Mono.error(new RuntimeException("Downstream User Service Internal Failure: " + e.getMessage()));
                    }
                    return Mono.error(new RuntimeException("Unexpected registration failure", e));
                });
    }
}
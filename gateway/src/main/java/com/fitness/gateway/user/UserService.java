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
public class UserService {

    private final WebClient userServiceWebClient;

    public Mono<Boolean> validateUser(String userId) {
        // 🪵 ADDED LOG: Tracking validation requests leaving the Gateway
        log.info(">>>> [Gateway Service] Sending validation request downstream for userId: {} <<<<", userId);

        return userServiceWebClient.get()
                .uri("api/users/validate")
                .header("X-User-ID", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.NOT_FOUND)) {
                        log.info("Downstream validation returned 404 for user {}. Mapping to false.", userId);
                        return Mono.just(false);
                    } else if (e.getStatusCode().isSameCodeAs(org.springframework.http.HttpStatus.BAD_REQUEST)) {
                        return Mono.error(new RuntimeException("Invalid Request parameter passed: " + userId));
                    }
                    return Mono.error(new RuntimeException("Unexpected verification failure: " + e.getMessage(), e));
                });
    }

    public Mono<UserResponse> registerUser(RegisterRequest registerRequest) {
        // 🪵 ADDED LOG: Printing out the exact DTO content before serializing to JSON over the network wire
        log.info(">>>> [Gateway Service] Preparing outbound POST payload to /register: {} <<<<", registerRequest);

        return userServiceWebClient.post()
                .uri("api/users/register")
                .bodyValue(registerRequest)
                .retrieve()
                .bodyToMono(UserResponse.class)
                .onErrorResume(WebClientResponseException.class, e -> {
                    HttpStatusCode status = e.getStatusCode();

                    if (status.isSameCodeAs(org.springframework.http.HttpStatus.BAD_REQUEST)) {
                        return Mono.error(new RuntimeException("Bad Registration Request for: " + registerRequest.getEmail()));
                    } else if (status.isSameCodeAs(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)) {
                        return Mono.error(new RuntimeException("Downstream User Service Internal Failure: " + e.getMessage()));
                    }
                    return Mono.error(new RuntimeException("Unexpected registration failure", e));
                });
    }
}
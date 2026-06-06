package com.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public GeminiService(WebClient webClient) {
        this.webClient = webClient;
    }

    public String getAnswer(String question) {
        // Construct the structure using standard Java collections
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", question)
                        ))
                )
        );

        // Build the target URI cleanly
        URI targetUri = UriComponentsBuilder.fromUriString(geminiApiUrl)
                .queryParam("key", geminiApiKey)
                .build()
                .toUri();

        // ================= DIGNOCSTIC LOGS START =================
//        log.info("======= GEMINI API DEBUGGING LOGS =======");
//        log.info("Base URL from properties: {}", geminiApiUrl);
//        log.info("API Key from properties: {}", (geminiApiKey != null && geminiApiKey.length() > 5) ?
//                geminiApiKey.substring(0, 5) + "..." : "NULL/EMPTY");
//        log.info("Final Compiled Target URI: {}", targetUri);
//        log.info("Payload Text Length: {} characters", question.length());
//        log.info("==========================================");
        // ================== DIGNOCSTIC LOGS END ==================

        try {
            return webClient.post()
                    .uri(targetUri)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("Network Error hitting Gemini endpoint: {}", e.getMessage());
            throw e;
        }
    }
}
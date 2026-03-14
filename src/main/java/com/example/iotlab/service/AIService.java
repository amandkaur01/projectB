package com.example.iotlab.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AIService {

    @Value("${huggingface.api.key:}")
    private String apiKey;

    @Value("${huggingface.api.url:https://api-inference.huggingface.co/models/google/flan-t5-large}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calls HuggingFace API and returns clean generated text. Throws
     * RuntimeException on any failure so AIController can fall back using REAL
     * database data instead of hardcoded values.
     */
    public String askAI(String prompt) {

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("hf_REPLACE")) {
            throw new RuntimeException("NO_API_KEY");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestBody = "{"
                    + "\"inputs\": \"" + escapeJson(prompt) + "\","
                    + "\"options\": {\"wait_for_model\": true}"
                    + "}";

            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response
                    = restTemplate.postForEntity(apiUrl, request, String.class);

            String rawBody = response.getBody();
            if (rawBody == null || rawBody.isBlank()) {
                throw new RuntimeException("EMPTY_RESPONSE");
            }

            return parseResponse(rawBody, prompt);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.GONE) {
                throw new RuntimeException("MODEL_RETIRED");
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new RuntimeException("INVALID_KEY");
            }
            if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new RuntimeException("RATE_LIMIT");
            }
            throw new RuntimeException("API_ERROR_" + e.getStatusCode());
        }
        // Network/timeout exceptions propagate naturally
    }

    private String parseResponse(String rawBody, String originalPrompt) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);

            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                if (first.has("generated_text")) {
                    String text = first.get("generated_text").asText().trim();
                    if (text.startsWith(originalPrompt)) {
                        text = text.substring(originalPrompt.length()).trim();
                    }
                    if (!text.isEmpty()) {
                        return text;
                    }
                    throw new RuntimeException("EMPTY_GENERATED_TEXT");
                }
                if (first.has("summary_text")) {
                    return first.get("summary_text").asText().trim();
                }
                if (first.has("translation_text")) {
                    return first.get("translation_text").asText().trim();
                }
            }

            if (root.isObject() && root.has("generated_text")) {
                return root.get("generated_text").asText().trim();
            }

            if (root.isObject() && root.has("error")) {
                String err = root.get("error").asText();
                if (err.contains("loading")) {
                    return "⏳ AI model is loading. Please wait 20 seconds and try again.";
                }
                throw new RuntimeException("HF_ERROR: " + err);
            }

            return rawBody.trim();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            return rawBody.trim();
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

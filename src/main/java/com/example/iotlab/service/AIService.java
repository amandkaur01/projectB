package com.example.iotlab.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.iotlab.model.Equipment;
import com.example.iotlab.repository.EquipmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AIService {

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Value("${huggingface.api.key:}")
    private String apiKey;

    @Value("${huggingface.api.url:https://api-inference.huggingface.co/models/google/flan-t5-large}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String askAI(String userQuestion) {

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("hf_REPLACE")) {
            throw new RuntimeException("NO_API_KEY");
        }

        try {

            // 1️⃣ Fetch equipment data from database
            List<Equipment> equipmentList = equipmentRepository.findAll();

            // 2️⃣ Convert database data into AI readable text
            String context = equipmentList.stream()
                    .map(e -> e.getName()
                    + " total:" + e.getTotalQuantity()
                    + " available:" + e.getAvailableQuantity())
                    .collect(Collectors.joining("\n"));

            // 3️⃣ Create prompt for AI
            String prompt
                    = "You are an AI assistant for a smart lab inventory system.\n\n"
                    + "Inventory Data:\n"
                    + context
                    + "\n\nUser Question:\n"
                    + userQuestion
                    + "\n\nAnswer based only on the inventory data.";

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
                }
            }

            return rawBody.trim();

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

package com.konkuk.memory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.konkuk.memory.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GeminiPhotoAnalysisClient {

    private static final String PROVIDER = "google-gemini";
    private static final Set<String> CONFIDENCE_VALUES =
            Set.of("HIGH", "MEDIUM", "LOW");

    private static final String PROMPT = """
            이 사진을 회상 질문 생성용으로 분석하세요.
            사진에서 직접 보이는 내용만 사용하고, 보이지 않는 사실은 추측하지 마세요.
            음식이 보이면 가능한 음식명을 confidence(HIGH, MEDIUM, LOW)와 함께 적으세요.
            사람의 신원, 관계, 민족, 건강 상태, 정확한 주소 같은 민감하거나 확인 불가능한 정보는 추론하지 마세요.
            questionClues에는 사용자의 기억을 유도할 수 있는 짧고 중립적인 사실 단서를 한국어로 적으세요.
            불확실한 내용은 단정하지 말고 uncertainties에 적으세요.
            모든 문자열은 한국어로 작성하세요.
            """;

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "summary", Map.of("type", "string"),
                    "foods", Map.of(
                            "type", "array",
                            "items", Map.of(
                                    "type", "object",
                                    "properties", Map.of(
                                            "name", Map.of("type", "string"),
                                            "confidence", Map.of(
                                                    "type", "string",
                                                    "enum", List.of("HIGH", "MEDIUM", "LOW")
                                            )
                                    ),
                                    "required", List.of("name", "confidence")
                            )
                    ),
                    "objects", stringArraySchema(),
                    "scene", Map.of("type", "string"),
                    "activities", stringArraySchema(),
                    "peopleCount", Map.of("type", "integer"),
                    "visibleText", stringArraySchema(),
                    "questionClues", stringArraySchema(),
                    "uncertainties", stringArraySchema()
            ),
            "required", List.of(
                    "summary",
                    "foods",
                    "objects",
                    "scene",
                    "activities",
                    "peopleCount",
                    "visibleText",
                    "questionClues",
                    "uncertainties"
            )
    );

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiPhotoAnalysisClient(
            @Value("${app.ai.gemini.api-key:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-3.5-flash-lite}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey.trim();
        this.model = validateModel(model);
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .requestFactory(requestFactory)
                .build();
    }

    public String analyze(byte[] imageBytes, String contentType) {
        if (apiKey.isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_ANALYSIS_NOT_CONFIGURED",
                    "Gemini API 키가 설정되지 않았습니다."
            );
        }

        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("text", PROMPT),
                                Map.of("inlineData", Map.of(
                                        "mimeType", contentType,
                                        "data", Base64.getEncoder().encodeToString(imageBytes)
                                ))
                        )
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseJsonSchema", RESPONSE_SCHEMA,
                        "temperature", 0.2
                )
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String output = extractOutput(response);

            if (output == null || output.isBlank()) {
                throw invalidResponse();
            }

            return normalize(output);
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw upstreamFailure(ex.getStatusCode().value());
        } catch (JsonProcessingException ex) {
            throw invalidResponse();
        } catch (RestClientException ex) {
            throw connectionFailed();
        }
    }

    private static String extractOutput(JsonNode response) {
        if (response == null) {
            return null;
        }

        JsonNode parts = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts");
        String output = null;

        if (parts.isArray()) {
            for (JsonNode part : parts) {
                String text = part.path("text").textValue();
                if (text != null && !text.isBlank()) {
                    output = text;
                }
            }
        }

        return output;
    }

    String normalize(String output) throws JsonProcessingException {
        JsonNode source = objectMapper.readTree(output);

        if (!source.isObject()) {
            throw analysisFailed();
        }

        String summary = cleanText(source.path("summary").asText(""));
        if (summary.isBlank()) {
            throw analysisFailed();
        }

        ObjectNode stored = objectMapper.createObjectNode();
        stored.put("summary", summary);

        ArrayNode foods = stored.putArray("foods");
        JsonNode sourceFoods = source.path("foods");
        if (sourceFoods.isArray()) {
            sourceFoods.forEach(food -> {
                String name = cleanText(food.path("name").asText(""));
                if (!name.isBlank()) {
                    String confidence = food.path("confidence")
                            .asText("LOW")
                            .toUpperCase();
                    if (!CONFIDENCE_VALUES.contains(confidence)) {
                        confidence = "LOW";
                    }
                    ObjectNode normalizedFood = foods.addObject();
                    normalizedFood.put("name", name);
                    normalizedFood.put("confidence", confidence);
                }
            });
        }

        copyStringArray(source, stored, "objects");
        stored.put("scene", cleanText(source.path("scene").asText("")));
        copyStringArray(source, stored, "activities");
        stored.put("peopleCount", Math.max(0, source.path("peopleCount").asInt(0)));
        copyStringArray(source, stored, "visibleText");
        copyStringArray(source, stored, "questionClues");
        copyStringArray(source, stored, "uncertainties");
        stored.put("provider", PROVIDER);
        stored.put("model", model);
        stored.put("analyzedAt", Instant.now().toString());

        return objectMapper.writeValueAsString(stored);
    }

    private void copyStringArray(
            JsonNode source,
            ObjectNode target,
            String fieldName
    ) {
        ArrayNode output = target.putArray(fieldName);
        JsonNode input = source.path(fieldName);

        if (!input.isArray()) {
            return;
        }

        input.forEach(item -> {
            String value = cleanText(item.asText(""));
            if (!value.isBlank()) {
                output.add(value);
            }
        });
    }

    private static String cleanText(String value) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.length() <= 500
                ? cleaned
                : cleaned.substring(0, 500);
    }

    private static Map<String, Object> stringArraySchema() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        );
    }

    private static String validateModel(String model) {
        String value = model == null ? "" : model.trim();
        if (!value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Gemini model name is invalid.");
        }
        if (value.equals("gemini-2.5-flash-lite")) {
            return "gemini-3.5-flash-lite";
        }
        return value;
    }

    private static ApiException invalidResponse() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_RESPONSE_INVALID",
                "Gemini 응답을 사진 분석 결과로 처리하지 못했습니다."
        );
    }

    private static ApiException connectionFailed() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_CONNECTION_FAILED",
                "Gemini 서버에 연결하지 못했습니다."
        );
    }

    private static ApiException upstreamFailure(int status) {
        return switch (status) {
            case 400 -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_REQUEST_REJECTED",
                    "Gemini가 분석 요청 형식을 거절했습니다."
            );
            case 401, 403 -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_API_KEY_REJECTED",
                    "Gemini API 키 또는 프로젝트 권한을 확인해주세요."
            );
            case 404 -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_MODEL_UNAVAILABLE",
                    "현재 프로젝트에서 사용할 수 없는 Gemini 모델입니다."
            );
            case 429 -> new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_QUOTA_EXCEEDED",
                    "Gemini 무료 사용량을 초과했습니다. 잠시 후 다시 시도해주세요."
            );
            default -> analysisFailed();
        };
    }

    private static ApiException analysisFailed() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_ANALYSIS_FAILED",
                "사진 AI 분석에 실패했습니다. 잠시 후 다시 시도해주세요."
        );
    }
}
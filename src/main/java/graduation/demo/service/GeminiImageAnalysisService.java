package graduation.demo.service;

import graduation.demo.dto.ImageAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiImageAnalysisService {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImageAnalysisResponse analyzeImage(MultipartFile image) {
        try {
            if (image.isEmpty()) {
                throw new IllegalArgumentException("이미지 파일이 비어 있습니다.");
            }

            String contentType = image.getContentType();

            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
            }

            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

            Map<String, Object> requestBody = createGeminiRequestBody(base64Image, contentType);

            RestClient restClient = RestClient.builder()
                    .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                    .defaultHeader("x-goog-api-key", apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            String responseBody = restClient.post()
                    .uri("/models/" + model + ":generateContent")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String jsonText = extractTextFromGeminiResponse(responseBody);

            jsonText = cleanJsonText(jsonText);

            return objectMapper.readValue(jsonText, ImageAnalysisResponse.class);

        } catch (Exception e) {
            throw new RuntimeException("Gemini 이미지 분석 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createGeminiRequestBody(String base64Image, String contentType) {
        Map<String, Object> requestBody = new LinkedHashMap<>();

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", """
                이 이미지를 분석해서 반드시 JSON 형식으로만 응답해줘.
                설명 문장, markdown, ```json 같은 코드블록 없이 순수 JSON만 반환해.

                JSON 구조는 반드시 아래 형식을 따라야 해.

                {
                  "title": "이미지의 짧은 제목",
                  "scene": "장소나 상황 설명",
                  "mood": "이미지에서 느껴지는 분위기",
                  "description": "이미지 전체 설명",
                  "objects": ["주요 사물1", "주요 사물2"],
                  "tags": ["태그1", "태그2"],
                  "memoryQuestions": ["기억 회상 질문1", "기억 회상 질문2"]
                }

                사용자의 기억 회상을 돕는 서비스에 사용할 것이다.
                이미지에서 확실히 보이는 정보 위주로만 분석해.
                모르는 내용은 추측하지 마.
                """);

        Map<String, Object> inlineData = new LinkedHashMap<>();
        inlineData.put("mime_type", contentType);
        inlineData.put("data", base64Image);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("inline_data", inlineData);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", List.of(textPart, imagePart));

        requestBody.put("contents", List.of(content));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("response_mime_type", "application/json");

        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private String extractTextFromGeminiResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode textNode = root
                .path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text");

        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            throw new RuntimeException("Gemini 응답에서 text를 찾을 수 없습니다.");
        }

        return textNode.asText();
    }

    private String cleanJsonText(String text) {
        return text
                .replace("```json", "")
                .replace("```", "")
                .trim();
    }
}
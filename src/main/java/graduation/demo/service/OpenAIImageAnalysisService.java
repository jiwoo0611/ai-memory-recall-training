package graduation.demo.service;

import graduation.demo.dto.ImageAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

@Service
public class OpenAIImageAnalysisService {
    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ImageAnalysisResponse analyzeImage(MultipartFile image){
        try {
            if (image.isEmpty()){
                throw new IllegalArgumentException("이미지 파일이 비었습니다.");
            }

            String contentType = image.getContentType();
            if (contentType == null || !contentType.startsWith("image/")){
                throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
            }
            String base64Image = Base64.getEncoder().encodeToString(image.getBytes());
            String imageDataUrl = "data:" + contentType + ";base64," + base64Image;

            Map<String, Object> requestBody = createOpenAIRequestBody(imageDataUrl);

            RestClient restClient = RestClient.builder()
                    .baseUrl("https://api.openai.com/v1")
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            String responseBody = restClient.post()
                    .uri("/responses")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            String jsonText = extractJsonTextFromOpenAIResponse(responseBody);

            return objectMapper.readValue(jsonText, ImageAnalysisResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("이미지 분석 중 오류가 발생했습니다.: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> createOpenAIRequestBody(String imageDataUrl){
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);

        List<Map<String, Object>> input = new ArrayList<>();

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");

        List<Map<String, Object>> content = new ArrayList<>();

        Map<String,Object> textContent = new LinkedHashMap<>();
        textContent.put("type", "input_text");
        textContent.put("text", """
                이 이미지를 분석해서 반드시 JSON 형식으로만 응답해줘.
                사용자의 기억 회상을 돕는 서비스에 사용할거야.
                너무 과장하지 말고 이미지에서 확실히 보이는 정보 위주로 분석해줘.
                """);

        Map<String, Object> imageContent = new LinkedHashMap<>();
        imageContent.put("type", "input_image");
        imageContent.put("image_url", imageDataUrl);

        content.add(textContent);
        content.add(imageContent);

        userMessage.put("content", content);
        input.add(userMessage);

        requestBody.put("input", input);

        Map<String, Object> text = new LinkedHashMap<>();
        Map<String, Object> format = new LinkedHashMap<>();

        format.put("type", "json_schema");
        format.put("name", "image_analysis");

        Map<String, Object> schema = createJsonSchema();
        format.put("schema", schema);
        format.put("strict", true);

        text.put("format", format);
        requestBody.put("text", text);

        return requestBody;
    }

    private Map<String, Object> createJsonSchema(){
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("title", Map.of(
                "type", "string",
                "description","이미지의 짧은 제목"
        ));
        properties.put("scene", Map.of(
                "type", "string",
                "description","장소나 상황 설명"
        ));
        properties.put("mood", Map.of(
                "type", "string",
                "description","이미지에서 느껴지는 분위기"
        ));
        properties.put("description", Map.of(
                "type", "string",
                "description","이미지 전체 설명"
        ));
        properties.put("objects", Map.of(
                "type", "array",
                "items", Map.of("type","string"),
                "description","이미지 안의 주요 사물 목록"
        ));
        properties.put("tags", Map.of(
                "type", "array",
                "items",Map.of("type","string"),
                "description","검색이나 분류에 사용할 태그"
        ));
        properties.put("memoryQuestions", Map.of(
                "type", "array",
                "items",Map.of("type","string"),
                "description","사용자 기억 회상을 돕는 질문 목록"
        ));

        schema.put("properties",properties);

        schema.put("required", List.of(
                "title",
                "scene",
                "mood",
                "description",
                "objects",
                "tags",
                "memoryQuestions"
        ));

        return schema;
    }

    private String extractJsonTextFromOpenAIResponse(String responseBody) throws Exception{
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode output = root.path("output");

        for (JsonNode outputItem : output){
            JsonNode content = outputItem.path("content");

            for (JsonNode contentItem : content){
                if (contentItem.has("text")){
                    return contentItem.path("text").asString();
                }
            }
        }
        throw new RuntimeException("OpenAI 응답에서 JSON 텍스트를 찾을 수 없습니다.");
    }
}

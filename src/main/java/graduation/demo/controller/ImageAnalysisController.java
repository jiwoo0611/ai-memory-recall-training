package graduation.demo.controller;

import graduation.demo.dto.ImageAnalysisResponse;
import graduation.demo.dto.QuizResponse;
import graduation.demo.service.GeminiImageAnalysisService;
import graduation.demo.service.PhotoLocationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ImageAnalysisController {

    private final GeminiImageAnalysisService imageAnalysisService;
    private final PhotoLocationService photoLocationService;

    public ImageAnalysisController(
            GeminiImageAnalysisService imageAnalysisService,
            PhotoLocationService photoLocationService
    ) {
        this.imageAnalysisService = imageAnalysisService;
        this.photoLocationService = photoLocationService;
    }

    @PostMapping(
            value = "/images/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ImageAnalysisResponse> analyzeImage(
            @RequestParam("image") MultipartFile image
    ) {
        ImageAnalysisResponse result = imageAnalysisService.analyzeImage(image);
        return ResponseEntity.ok(result);
    }

    @PostMapping(
            value = "/generate-quiz",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<QuizResponse> generateQuiz(
            @RequestParam("image") MultipartFile image
    ) {
        String province = photoLocationService.extractProvince(image);

        if (province == null) {
            QuizResponse noLocationQuiz = new QuizResponse(
                    "이 사진은 어느 지역에서 찍힌 사진일까요?",
                    List.of("서울특별시", "경기도", "강원도", "부산광역시"),
                    "위치 정보 없음"
            );

            return ResponseEntity.ok(noLocationQuiz);
        }

        QuizResponse locationQuiz = new QuizResponse(
                "이 사진은 어느 지역에서 찍힌 사진일까요?",
                photoLocationService.createProvinceOptions(province),
                province
        );

        return ResponseEntity.ok(locationQuiz);
    }
}
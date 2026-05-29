package graduation.demo.controller;

import graduation.demo.dto.ImageAnalysisResponse;
import graduation.demo.service.GeminiImageAnalysisService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
public class ImageAnalysisController {
    private final GeminiImageAnalysisService imageAnalysisService;

    public ImageAnalysisController(GeminiImageAnalysisService imageAnalysisService) {
        this.imageAnalysisService = imageAnalysisService;
    }

    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ImageAnalysisResponse> analyzeImage(
            @RequestParam("image")MultipartFile image
            ){
        ImageAnalysisResponse result = imageAnalysisService.analyzeImage(image);
        return ResponseEntity.ok(result);
    }
}

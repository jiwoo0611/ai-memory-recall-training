package graduation.demo.dto;

import java.util.List;

public class ImageAnalysisResponse {

    private String title;
    private String scene;
    private String mood;
    private String description;
    private List<String> objects;
    private List<String> tags;
    private List<String> memoryQuestions;

    public ImageAnalysisResponse() {
    }

    public ImageAnalysisResponse(String title, String scene, String mood, String description,
                                 List<String> objects, List<String> tags, List<String> memoryQuestions) {
        this.title = title;
        this.scene = scene;
        this.mood = mood;
        this.description = description;
        this.objects = objects;
        this.tags = tags;
        this.memoryQuestions = memoryQuestions;
    }

    public String getTitle() {
        return title;
    }

    public String getScene() {
        return scene;
    }

    public String getMood() {
        return mood;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getObjects() {
        return objects;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getMemoryQuestions() {
        return memoryQuestions;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setObjects(List<String> objects) {
        this.objects = objects;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setMemoryQuestions(List<String> memoryQuestions) {
        this.memoryQuestions = memoryQuestions;
    }
}
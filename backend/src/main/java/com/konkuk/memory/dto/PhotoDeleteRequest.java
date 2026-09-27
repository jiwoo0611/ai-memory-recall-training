package com.konkuk.memory.dto;

import java.util.List;

public class PhotoDeleteRequest {

    private List<Long> photoIds;

    public List<Long> getPhotoIds() {
        return photoIds;
    }

    public void setPhotoIds(List<Long> photoIds) {
        this.photoIds = photoIds;
    }
}

package com.konkuk.memory.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "photos", indexes = @Index(name = "idx_photos_owner_created", columnList = "user_id,created_at"),
       uniqueConstraints = @UniqueConstraint(name = "uk_photos_storage_key", columnNames = "storage_key"))
public class Photo extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_photos_user"))
    private User user;
    @Column(nullable = false, length = 500)
    private String storageKey;
    @Column(nullable = false, length = 255)
    private String originalFilename;
    @Column(nullable = false, length = 100)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    private LocalDateTime metadataTakenAt;
    @Column(length = 500)
    private String metadataLocation;
    private LocalDate confirmedTakenOn;
    @Column(length = 500)
    private String confirmedLocation;
    @Column(columnDefinition = "TEXT")
    private String aiClues;

    protected Photo() {}
    public Photo(User user,
                 Album album,
                 String storageKey,
                 String originalFilename,
                 String contentType,
                 long sizeBytes) {
        this.user = user;
        this.album = album;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }
    public User getUser() { return user; }
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "album_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_photos_album")
    )
    private Album album;
    public Album getAlbum(){ return album; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public LocalDateTime getMetadataTakenAt() { return metadataTakenAt; }
    public String getMetadataLocation() { return metadataLocation; }
    public LocalDate getConfirmedTakenOn() { return confirmedTakenOn; }
    public String getConfirmedLocation() { return confirmedLocation; }
    public String getAiClues() { return aiClues; }
    public void updateMetadate(LocalDateTime metadataTakenAt, String metadataLocation){
        this.metadataLocation = metadataLocation;
        this.metadataTakenAt = metadataTakenAt;
    }
    public void confirmMetadata(LocalDate confirmedTakenOn, String confirmedLocation){this.confirmedTakenOn = confirmedTakenOn; this.confirmedLocation = confirmedLocation;}
}

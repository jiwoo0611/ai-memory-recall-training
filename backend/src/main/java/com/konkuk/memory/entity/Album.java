package com.konkuk.memory.entity;

import jakarta.persistence.*;

@Entity
@Table(
        name = "albums",
        indexes = @Index(
                name = "idx_albums_user_created",
                columnList = "user_id, created_at"
        )
)

public class Album extends BaseEntity{
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_albums_user")
    )
    private User user;

    @Column(nullable = false, length = 100)
    private String title;

    protected Album(){
    }

    public Album(User user, String title){
        this.user = user;
        this.title = title;
    }

    public User getUser(){
        return user;
    }

    public String getTitle(){
        return title;
    }

    public void changeTitle(String title){
        this.title = title;
    }
}

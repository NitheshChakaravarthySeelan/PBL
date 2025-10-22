package com.blog.postservice.domain.model;

import jakarta.persistence.*;
import com.blog.common.User;

@Entity(name = "AppContent")
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue
    private Long id;

    private String title;
    private String content;

    private Long userId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

package com.blog.postservice.application.mapper;

import com.blog.postservice.application.dto.PostDto;
import com.blog.postservice.domain.model.Post;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {

    public PostDto toDto(Post post) {
        if (post == null) {
            return null;
        }
        return PostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .userId(post.getUserId())
                .build();
    }

    public Post toEntity(PostDto postDto) {
        if (postDto == null) {
            return null;
        }
        Post post = new Post();
        post.setId(postDto.getId());
        post.setTitle(postDto.getTitle());
        post.setContent(postDto.getContent());
        post.setUserId(postDto.getUserId());
        return post;
    }
}
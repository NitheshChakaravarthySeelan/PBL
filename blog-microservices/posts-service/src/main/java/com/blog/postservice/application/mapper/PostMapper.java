package com.blog.postservice.application.mapper;

import com.blog.postservice.application.dto.PostDto;
import com.blog.postservice.domain.model.Post;
import com.blog.common.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {

    @Autowired
    private UserMapper userMapper;

    public PostDto toDto(Post post) {
        if (post == null) {
            return null;
        }
        return PostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .user(userMapper.toDto(post.getUser()))
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
        post.setUser(userMapper.toEntity(postDto.getUser()));
        return post;
    }
}
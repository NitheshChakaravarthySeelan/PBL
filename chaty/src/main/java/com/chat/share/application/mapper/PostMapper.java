package com.chat.share.application.mapper;

import com.chat.share.application.dto.PostDto;
import com.chat.share.domain.model.Post;
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
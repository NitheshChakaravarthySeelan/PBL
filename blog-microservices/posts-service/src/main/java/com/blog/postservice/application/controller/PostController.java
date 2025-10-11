
package com.blog.postservice.application.controller;

import com.blog.postservice.application.dto.PostDto;
import com.blog.postservice.application.mapper.PostMapper;
import com.blog.postservice.domain.model.Post;
import com.blog.postservice.domain.services.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private PostMapper postMapper;

    @GetMapping
    public List<PostDto> getAll() {
        return postService.findAll().stream().map(postMapper::toDto).collect(Collectors.toList());
    }

    @PostMapping("/{userId}")
    public PostDto create(@PathVariable Long userId, @RequestBody PostDto postDto) {
        Post post = postMapper.toEntity(postDto);
        return postMapper.toDto(postService.create(post, userId));
    }
}
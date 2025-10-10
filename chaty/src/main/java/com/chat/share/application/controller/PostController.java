package com.chat.share.application.controller;

import com.chat.share.domain.model.Post;
import com.chat.share.domain.services.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @GetMapping
    public List<Post> getAll() {
        return postService.findAll();
    }

    @PostMapping("/{userId}")
    public Post create(@PathVariable Long userId, @RequestBody Post post) {
        return postService.create(post, userId);
    }
}
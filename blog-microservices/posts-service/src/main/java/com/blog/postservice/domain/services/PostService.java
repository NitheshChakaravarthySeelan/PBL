package com.blog.postservice.domain.services;

import com.blog.postservice.domain.model.Post;
import com.blog.postservice.domain.repository.PostRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    private final Counter postCreatedCounter;

    public PostService(MeterRegistry registry) {
        this.postCreatedCounter = Counter.builder("posts_created_total")
            .description("Number of posts created")
            .register(registry);
    }

    public Post create(Post post, Long userId) {
        post.setUserId(userId);
	    Post savedPost = postRepository.save(post);
	    postCreatedCounter.increment();
	    return savedPost;
    }

    public List<Post> findAll() {
        return postRepository.findAll();
    }

    public List<Post> findByUser(Long userId) {
        return postRepository.findByUserId(userId);
    }

    public void delete(Long id) {
        postRepository.deleteById(id);
    }
}

package com.blog.postservice.domain.services;

import com.blog.common.User;
import com.blog.postservice.domain.model.Post;
import com.blog.postservice.domain.repository.PostRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // Inject Kafka template 	
    private final Counter postCreatedCounter;

    private static final String POST_TOPIC = "post-events";

    public PostService(MeterRegistry registry) {
        this.postCreatedCounter = Counter.builder("posts_created_total")
            .description("Number of posts created")
            .register(registry);
	
	Post savedPost = postRepository.save(post);
	// After saving send an event to kafka
	kafkaTemplate.send(POST_TOPIC, "post-created", savedPost);
    }

    public Post create(Post post, Long userId) {
	    Post savedPost = postRepository.save(post);
	    // After saving send an event to kafka
	    kafkaTemplate.sent(POST_TOPIC, "post-created", savedPost);
	    postCreatedCounter.increment();
	    return savedPost;

    public List<Post> findAll() {
        return postRepository.findAll();
    }

    /**
    public Post create(Post post, Long userId) {
        // TODO: This is a temporary fix
        // In a real microservices architecture, this should be a call to the users-service to get the user
        User user = new User();
        user.setId(userId);
        post.setUser(user);
        postCreatedCounter.increment();
        return postRepository.save(post);
    }
    */

    public List<Post> findByUser(Long userId) {
        return postRepository.findByUserId(userId);
    }

    public void delete(Long id) {
        postRepository.deleteById(id);
    }
}

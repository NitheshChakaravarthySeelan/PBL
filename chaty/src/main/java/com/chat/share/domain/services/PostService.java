package com.chat.share.domain.services;

import com.chat.share.domain.model.Post;
import com.chat.share.domain.model.User;
import com.chat.share.domain.repository.PostRepository;
import com.chat.share.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    public List<Post> findAll() {
        return postRepository.findAll();
    }

    public Post create(Post post, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not in the db"));
        post.setUser(user);
        return postRepository.save(post);
    }

    public List<Post> findByUser(Long userId) {
        return postRepository.findByUserId(userId);
    }

    public void delete(Long id) {
        postRepository.deleteById(id);
    }
}
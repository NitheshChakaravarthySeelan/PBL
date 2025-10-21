package com.blog.userservice.domain.services;

import com.blog.common.User;
import com.blog.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate; // Import KafkaTemplate

import java.util.List;

@Service
public class UserService {

    private final UserRepository repo;
    private final KafkaTemplate<String, Object> kafkaTemplate; // Declare KafkaTemplate

    private static final String USER_TOPIC = "user-events"; // Define Kafka topic

    @Autowired
    public UserService(UserRepository repo, KafkaTemplate<String, Object> kafkaTemplate) { // Inject KafkaTemplate
        this.repo = repo;
        this.kafkaTemplate = kafkaTemplate;
    }

    public List<User> findAll() {
        return repo.findAll();
    }

    public Page<User> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public User create(User u) {
        User savedUser = repo.save(u); // Save the user
        kafkaTemplate.send(USER_TOPIC, "user-created", savedUser); // Send Kafka message
        System.out.println("Sent user created event to Kafka: " + savedUser.getId());
        return savedUser;
    }
}
package com.blog.userservice.kafka;

import com.blog.common.User; // Assuming your User model is here (from common module)
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserKafkaConsumer {

    @KafkaListener(topics = "user-events", groupId = "${spring.kafka.consumer.group-id}")
    public void listenUserEvents(User user) {
        System.out.println("Received User Event: " + user.getId() + " - " + user.getUsername());
        // Here you can add logic to process the received User object
        // For example, update a cache, trigger another service, etc.
    }

    // You can add more listener methods for different topics or message types
}

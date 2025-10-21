package com.blog.postservice.kafka;

import com.blog.postservice.domain.model.Post;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public clas PostKafkaConsumer {
	@KafkaListener(topics = "post-events", groupId = "${spring.kafka.consumer.group-id}")
	public void listenPostEvents(Post post) {
		System.out.println("Received Post Event: " + post.getId() + " - " + post.getTitle();
	}
}

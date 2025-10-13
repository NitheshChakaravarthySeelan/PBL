package com.blog.userservice.domain.services;

import com.blog.common.User;
import com.blog.userservice.domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository repo;

    public List<User> findAll() {
        return repo.findAll();
    }

    public Page<User> findAll(Pageable pageable) {
        return repo.findAll(pageable);
    }

    public User create(User u) {
        return repo.save(u);
    }
}
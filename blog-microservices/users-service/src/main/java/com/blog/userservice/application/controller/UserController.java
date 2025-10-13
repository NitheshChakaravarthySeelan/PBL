
package com.blog.userservice.application.controller;

import com.blog.common.UserMapper;
import com.blog.common.UserDto;
import com.blog.common.User;
import com.blog.userservice.domain.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("api/users")
public class UserController {

    @Autowired
    private UserService service;

    @Autowired
    private UserMapper userMapper;

    @GetMapping
    public Page<UserDto> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return service.findAll(pageable).map(userMapper::toDto);
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody UserDto u) {
        User user = userMapper.toEntity(u);
        return userMapper.toDto(service.create(user));
    }
}
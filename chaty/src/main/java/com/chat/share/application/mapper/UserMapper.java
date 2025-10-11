
package com.chat.share.application.mapper;

import com.chat.share.application.dto.UserDto;
import com.chat.share.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        if (user == null) {
            return null;
        }
        return UserDto.builder()
                .id(user.getId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    public User toEntity(UserDto userDto) {
        if (userDto == null) {
            return null;
        }
        return User.builder()
                .id(userDto.getId())
                .userName(userDto.getUserName())
                .email(userDto.getEmail())
                .role(userDto.getRole())
                .build();
    }
}

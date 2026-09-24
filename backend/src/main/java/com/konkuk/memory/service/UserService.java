package com.konkuk.memory.service;

import com.konkuk.memory.dto.UserProfileResponse;
import com.konkuk.memory.entity.User;
import com.konkuk.memory.exception.ApiException;
import com.konkuk.memory.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    public UserService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUser(Long id) {
        return UserProfileResponse.from(requireCurrentUser(id));
    }

    @Transactional(readOnly = true)
    public User requireCurrentUser(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "유효한 사용자가 아닙니다."));
    }
}

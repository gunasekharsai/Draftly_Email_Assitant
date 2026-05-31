package com.draftly.ai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.draftly.ai.models.User;

public interface UserRepository extends  JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    
}

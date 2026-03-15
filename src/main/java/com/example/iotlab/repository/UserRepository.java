package com.example.iotlab.repository;

import com.example.iotlab.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // Used by OverdueEmailService to look up a student's email by name
    Optional<User> findByNameIgnoreCase(String name);
}

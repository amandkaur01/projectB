package com.example.iotlab.controller;

import com.example.iotlab.model.LoginRequest;
import com.example.iotlab.model.User;
import com.example.iotlab.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public User register(@RequestBody User user) {

        return userRepository.save(user);

    }

    @PostMapping("/login")
    public User login(@RequestBody LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        if (user != null && user.getPassword().equals(request.getPassword())) {
            return user;
        }

        return null;
    }
}

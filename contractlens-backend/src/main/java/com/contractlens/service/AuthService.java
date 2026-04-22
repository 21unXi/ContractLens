package com.contractlens.service;

import com.contractlens.dto.LoginRequest;
import com.contractlens.dto.RegisterRequest;
import com.contractlens.entity.User;
import com.contractlens.repository.UserRepository;
import com.contractlens.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    public User register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already in use!");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        return userRepository.save(user);
    }

    public String login(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());

        return jwtUtil.generateToken(userDetails);
    }
}

package com.example.prac.service.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.prac.dto.auth.AuthenticationRequest;
import com.example.prac.dto.auth.AuthenticationResponse;
import com.example.prac.dto.auth.RegisterRequest;
import com.example.prac.exceptions.UserAlreadyExistsException;
import com.example.prac.model.auth.Role;
import com.example.prac.model.auth.User;
import com.example.prac.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;

        public AuthenticationResponse register(RegisterRequest request) {
                boolean userExists = userRepository.existsById(request.getUsername());

                if (userExists) {
                        throw new UserAlreadyExistsException("A user with the same username already exists");
                }

                var user = User.builder()
                                .username(request.getUsername())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .role(Role.USER)
                                .build();

                userRepository.save(user);
                var token = jwtService.generateToken(user);
                Role role = user.getRole();
                return AuthenticationResponse.builder()
                                .token(token)
                                .role(role)
                                .build();
        }

        public AuthenticationResponse authenticate(AuthenticationRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                request.getUsername(),
                                                request.getPassword()));
                var user = userRepository.findById(request.getUsername())
                                .orElseThrow(() -> new UsernameNotFoundException("user not found"));
                var token = jwtService.generateToken(user);
                Role role = user.getRole();
                return AuthenticationResponse.builder()
                                .token(token)
                                .role(role)
                                .build();
        }
}
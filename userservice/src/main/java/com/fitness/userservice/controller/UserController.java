package com.fitness.userservice.controller;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
@Slf4j
public class UserController {
//    @Autowired
    private UserService userService;
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse>getUserProfile(@PathVariable String userId){
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }
    @PostMapping("/register") // Ensure this matches your route pattern
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        // 🪵 ADDED LOG: Prints the entire incoming request body parameters
        log.info(">>>> [User Service] Received registration request data payload: {} <<<<", request);

        return ResponseEntity.ok(userService.register(request));
    }
    @GetMapping("/validate") // No path variable!
    public ResponseEntity<Boolean> validateUserProfile(@RequestHeader("X-User-ID") String userId) {
        // Validates using the secure ID forwarded in the header by the Gateway
        return ResponseEntity.ok(userService.existByUserId(userId));
    }

}

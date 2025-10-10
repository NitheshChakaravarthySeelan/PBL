package com.chat.share.infrastructure.security.auth;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.facotry.annotation.Autowired;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	@Autowired
	private AuthService authService;

	// Register endpoint
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
		return ResponseEntity.ok(authService.register(req));
	}

	// Login endpoint
	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest req) {
		return ResponseEntity.ok(authService.login(req));
	}

	// Me endpoint
	@GetMapping("/me")
	public ResponseEntity<?> me(@AuthenticationPrincipal Object principal) {
		// Returns the authenticated principal User entity in our setup
		return ResponseEntity.ok(principal);
	}

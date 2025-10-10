package com.chat.share.infrastructure.security.auth;


import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.chat.share.repo.UserRepository;
import com.chat.share.model.User;
import com.chat.share.model.Role;
import com.chat.share.security.JwtService;

/**
 * Registering Users
 * 	Validating the uniqueness of email
 * 	Hash password 
 * 	Assign default role
 * 	Save user to database
 * 	Return JWT for immediate authentication
 * Logging in users
 * 	Validate cred
 * 	if valid generate new JWT
 * 	Return it to client
 */
@Service
public class AuthService {
	@Autowired
	private UserRepository userRepo;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtService jwtService;

	public AuthResponse register(RegisterRequest req) {
		// Check for duplicate email
		if (userRepo.findByEmail(req.email()).isPresent()) {
			throw new IllegalArgumentException("Email already taken");
		}

		// Create a new user object
		User user = new User();
		user.setName(req.name());
		user.setEmail(req.email());

		// Hash the password
		user.setPassword(passwordEncoder.encode(req.password()));
		
		// Assign default role
		user.setRole(Role.USER);

		// Save to db
		userRepo.save(user);

		// Generate the JST token
		String token = jwtService.generateToken(user.getEmail());

		return new AuthReponse(token);
	}

	public AuthReponse login(LoginRequest req) {
		// Find the user by email
		User user = userRepo.findByEmail(req.email())
						.orElseThrow(() -> new RuntimeException("Invalid cred"));
		// Check both the password match
		if (!passwordEncoder.matches(req.password(), user.getPassword())) {
			throw new RuntimeException("Invalid credentials");
		}

		// Generate a new JWT token
		String token = jwtService.generateToken(user.getEmail());
		return new AuthResponse(token);
	}
}

package com.chat.share.infrastructure.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import com.chat.share.repo.UserRepository;
// import com.chat.share.model.User;
import java.io.IOException;
import java.util.List;

/**
 * Intercept every HTTP request
 * Extract and validate the JWT Token
 * Set up the authenticated user in Spring Security's context
 * Basically checks every request carries proof that the user is who they claim to be.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {
	@Autowired
	private JwtService jwtService;
	@Autowired
	private UserRepository userRepo;

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
		// Extracting the JWT from the Authorization header
		final String authHeader = req.getHeader("Authorization");

		// If the header doesn't exist or isn't prefixed with Bearer we skip
		if (authHeader == null || !authHeader.startswith("Bearer")) {
			chain.doFilter(req, res);
			return;
		}

		// Extract the token: 7 cause "Bearer " is 7 char long
		final String token = authHeader.substring(7);

		// Parse the subject
		String email;
		try {
			String email = jwtService.extractSubject(token);
		} catch (Exception e) {
			chain.doFilter(req, res);
			return;
		}

		// Avoid Re authenticating a valid context - ensures we don't re parse or overwrite an existing authentication if another filter is already set
		if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
		User user = userRepo.findByEmail(email).orElse(null);

		// Validate token against user idendity
		if (user != null && jwtService.isTokenValid(token, email)) {
			// If both pass user is legidimate
			// Create the authentication object
			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
			var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
			// Set the auth so anytime we can access the Object
			SecurityContextHolder.getContext().setAuthentication(auth);
		}
	}
	
	chain.doFilter(req, res);
}

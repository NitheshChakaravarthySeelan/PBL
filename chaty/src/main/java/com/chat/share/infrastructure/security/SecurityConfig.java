package com.chat.share.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.srpingframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframwork.beans.facotry.annotation.Autowired;
import org.springframwork.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * It declares a stateless filter chain that disables CSRF, enforces role rules, and inserts a JWT filter before String's username/password filter.
 */
@Configuration
public class SecurityConfig {
	@Autowired
	private JwtAuthFilter jwtAuthFilter;
	
	/**
	 * Spring security process req through a chain of filter
	 * This bean defines the order and behavior of those filter
	 * By returning http.build(), we create the chain for spring to use.
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// Disable the CSRF cause we are stateless and use JWT
		http.csrf().disable();
		// Stateless session management
		http.sessionManagement()
			sessionCreationPolicy(SessionCreationPolicy.STATELESS);

		// Define Endpoint Access Rules
		http.authorizeHttpRequests(auth -> auth
    			.requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
    			.requestMatchers("/api/admin/**").hasRole("ADMIN")
    			.anyRequest().authenticated());

		// Adding JWT to the filter chain
		http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		 return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

package com.chat.share.infrastructure.security;

import com.chat.share.domain.repository.UserRepository;
import com.chat.share.infrastructure.security.filter.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * It declares a stateless filter chain that disables CSRF, enforces role rules, and inserts a JWT filter before String's username/password filter.
 */
@Configuration
public class SecurityConfig {
	@Autowired
	private JwtAuthFilter jwtAuthFilter;

	@Autowired
	private UserRepository userRepo;
	
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
		http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

		// Define Endpoint Access Rules
		http.authorizeHttpRequests(auth -> auth
    			.antMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
    			.antMatchers("/api/admin/**").hasRole("ADMIN")
    			.anyRequest().authenticated());

		// Adding JWT to the filter chain
		http.authenticationProvider(authenticationProvider()).addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
		 return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService());
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	public UserDetailsService userDetailsService() {
		return username -> userRepo.findByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}
}
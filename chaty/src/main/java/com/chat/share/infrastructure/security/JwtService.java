package com.chat.share.infrastructure.security;

// Core JWT builder APIs
import io.jsonwebtoken.*;
// Utility to generate secure jwt key using HMAC
import io.jsonwebtoken.security.Keys;
// Injects value from application.properties
import org.springframework.beans.factory.annotation.Value;
// Marks it as spring managed bean used for dependency injection
import org.springframework.stereotype.Component;
// Interface for the crypt key
import java.security.Key;
import java.util.Date;

@Component
public class JwtService {
	private final Key key;
	private final long expiration;
	
	// Constructor
	public JwtService(@Value("${jwt.secret}") String secret,
			@Value("${jwt.expiration}") long expiration) {
			this.key = Keys.hmacShaKeyFor(secret.getBytes());
			this.expiration = expiration;
	}
	
	// Subject tells who the token belongs to 
	public String generateToken(String subject) {
		Date issuedAt = new Date();
		Date expirationDate = new Date(issuedAt.getTime() + expiration);
		return Jwts.builder()
				.setSubject(subject)
				.setIssuedAt(issuedAt)
				.setExpiration(expirationDate)
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	public boolean isTokenValid(String token, String username) {
		try {
			String sub = extractSubject(token);
			return (sub != null && sub.equals(username) && !isTokenExpired(token));
		} catch (JwtException e) {
			return false;
		}
	}

	public String extractSubject(String token) {
		return Jwts.parser().setSigningKey(key).build().parseClaimsJws(token)
			.getBody()
			.getSubject();
	}

	public boolean isTokenExpired(String token) {
		Date exp = Jwts.parser().setSigningKey(key).build().parseClaimsJws(token)
				.getBody()
				.getExpiration();
		Date currDate = new Date();
		return exp.before(currDate);
	}
}
package com.blog.apigateway;

import com.blog.apigateway.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@EnableDiscoveryClient
@SpringBootApplication
public class ApiGatewayApplication {

    @Autowired
    private JwtAuthenticationFilter authenticationFilter;

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    @Bean
    public RestTemplate template() {
        return new RestTemplate();
    }

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("users-service", r -> r.path("/users/**")
                        .filters(f -> f.filter(authenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("lb://users-service"))
                .route("posts-service", r -> r.path("/posts/**")
                        .filters(f -> f.filter(authenticationFilter.apply(new JwtAuthenticationFilter.Config())))
                        .uri("lb://posts-service"))
                .route("auth-service", r -> r.path("/api/auth/**")
                        .uri("lb://users-service")) // Assuming users-service handles auth
                .build();
    }
}
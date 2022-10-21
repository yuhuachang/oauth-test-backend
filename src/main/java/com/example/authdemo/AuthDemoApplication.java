package com.example.authdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.beans.factory.annotation.Value;

@EnableScheduling
@SpringBootApplication
public class AuthDemoApplication {

  @Value("${frontend_server_uri}")
  private String frontendServerUri;

	public static void main(String[] args) {
		SpringApplication.run(AuthDemoApplication.class, args);
	}

  // Allowing CORS.  This is used only for development or
  // front-end and back-end are different servers.
	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/**")
						.allowedOrigins(frontendServerUri);
			}
		};
	}
}

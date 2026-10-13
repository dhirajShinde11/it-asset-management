package com.company.itam.config;

import com.company.itam.model.AppUser;
import com.company.itam.model.UserRole;
import com.company.itam.repository.UserRepository;
import com.company.itam.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(
            UserRepository userRepository,
            AuthService authService) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            // ==========================================
            // SYSTEM ADMINISTRATOR ACCOUNTS ONLY
            // All test/dummy data removed.
            // Admin will manually add organization users & assets.
            // ==========================================
            AppUser admin = new AppUser(
                    "Dhiraj (IT Administrator)",
                    "dhiraj@bmconsulting.co",
                    authService.hashPassword("12345"),
                    "Information Technology",
                    "Principal IT Infrastructure Architect",
                    UserRole.ADMIN
            );
            admin.setPhoneNumber("+91 98765 43210");
            userRepository.save(admin);

            // Typo alias variant
            AppUser adminAlt = new AppUser(
                    "Dhiraj (IT Administrator)",
                    "dhiraj@bmcosulting.co",
                    authService.hashPassword("12345"),
                    "Information Technology",
                    "Principal IT Infrastructure Architect",
                    UserRole.ADMIN
            );
            adminAlt.setPhoneNumber("+91 98765 43210");
            userRepository.save(adminAlt);
        };
    }
}

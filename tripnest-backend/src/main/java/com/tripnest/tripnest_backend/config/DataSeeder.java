package com.tripnest.tripnest_backend.config;
 
import com.tripnest.tripnest_backend.entity.Role;
import com.tripnest.tripnest_backend.entity.User;
import com.tripnest.tripnest_backend.entity.Destination;
import com.tripnest.tripnest_backend.repository.DestinationRepository;
import com.tripnest.tripnest_backend.repository.RoleRepository;
import com.tripnest.tripnest_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
 
import java.util.List;
 
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
 
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final DestinationRepository destinationRepository;
    private final PasswordEncoder passwordEncoder;
 
    private static final List<String> DEFAULT_ROLES = List.of("TRAVELER", "GROUP_ADMIN", "ADMINISTRATOR");
    @Value("${ADMIN_EMAIL:}")
    private String adminEmail;
    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;
 
    @Override
    public void run(String... args) {
        DEFAULT_ROLES.forEach(roleName -> {
            if (roleRepository.findByName(roleName).isEmpty()) {
                Role role = new Role();
                role.setName(roleName);
                roleRepository.save(role);
            }
        });

        if (destinationRepository.count() == 0) {
            destinationRepository.saveAll(List.of(
                destination("Paris", "France", "Art, cafes, and historic neighbourhoods."),
                destination("Bali", "Indonesia", "Beaches, temples, and tropical landscapes."),
                destination("Manali", "India", "Mountain trails and Himalayan views."),
                destination("Tokyo", "Japan", "Food, culture, and modern city energy.")
            ));
        }
 
        // Guard: if the seeded admin already exists, stop here.
        // This is the ONLY place in the entire codebase that creates an
        // ADMINISTRATOR account directly — and it only ever runs once.
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        if (adminPassword.length() < 12) {
            throw new IllegalStateException("ADMIN_PASSWORD must contain at least 12 characters");
        }
        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }
 
        Role adminRole = roleRepository.findByName("ADMINISTRATOR")
                .orElseThrow(() -> new RuntimeException("ADMINISTRATOR role missing after seeding"));
 
        User admin = new User();
        admin.setName("Default Administrator");
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(adminRole);
        admin.setOauthGoogle(false);
        userRepository.save(admin);
    }

    private Destination destination(String name, String country, String description) {
        Destination destination = new Destination();
        destination.setName(name);
        destination.setCountry(country);
        destination.setDescription(description);
        destination.setWeatherInfo("Check forecast before departure");
        destination.setIsPopular(true);
        return destination;
    }
}

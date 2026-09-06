package br.com.promova.config;

import br.com.promova.profile.ProfileService;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile({"dev", "test"})
public class DataSeeder {
  @Bean
  CommandLineRunner seedUsers(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      ProfileService profileService) {
    return args -> {
      if (userRepository.count() > 0) {
        userRepository.findAll().forEach(profileService::ensureProfile);
        return;
      }

      userRepository.save(
          new User(
              "Gestor",
              "manager@promova.com",
              passwordEncoder.encode("manager123"),
              UserRole.MANAGER));

      userRepository.save(
          new User(
              "João Silva",
              "joao.silva@empresa.com",
              passwordEncoder.encode("senha123"),
              UserRole.EMPLOYEE));

      userRepository.save(
          new User(
              "Maria Santos",
              "maria.santos@empresa.com",
              passwordEncoder.encode("senha123"),
              UserRole.EMPLOYEE));

      userRepository.save(
          new User(
              "Pedro Costa",
              "pedro.costa@empresa.com",
              passwordEncoder.encode("senha123"),
              UserRole.EMPLOYEE));

      userRepository.findAll().forEach(profileService::ensureProfile);
    };
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}

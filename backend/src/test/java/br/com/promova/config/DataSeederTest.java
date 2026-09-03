package br.com.promova.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.promova.analysis.service.SavedAnalysisService;
import br.com.promova.profile.ProfileService;
import br.com.promova.user.User;
import br.com.promova.user.UserRepository;
import br.com.promova.user.UserRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataSeederTest {
  @Test
  void backfillsProfilesForUsersAlreadyPresentOnRestart() throws Exception {
    User existingUser = new User("Employee", "employee@example.com", "hash", UserRole.EMPLOYEE);
    UserRepository userRepository = mock(UserRepository.class);
    SavedAnalysisService savedAnalysisService = mock(SavedAnalysisService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    ProfileService profileService = mock(ProfileService.class);
    when(userRepository.count()).thenReturn(1L);
    when(userRepository.findAll()).thenReturn(List.of(existingUser));

    CommandLineRunner runner =
        new DataSeeder().seedUsers(userRepository, savedAnalysisService, passwordEncoder, profileService);

    runner.run();

    verify(profileService).ensureProfile(existingUser);
  }

  @Test
  void createsProfilesForAllDemoSeedUsers() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    SavedAnalysisService savedAnalysisService = mock(SavedAnalysisService.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    ProfileService profileService = mock(ProfileService.class);
    List<User> savedUsers = new ArrayList<>();
    when(userRepository.count()).thenReturn(0L);
    when(passwordEncoder.encode(any())).thenReturn("hash");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              savedUsers.add(user);
              return user;
            });
    when(userRepository.findAll()).thenAnswer(invocation -> savedUsers);

    CommandLineRunner runner =
        new DataSeeder().seedUsers(userRepository, savedAnalysisService, passwordEncoder, profileService);

    runner.run();

    verify(profileService, atLeastOnce()).ensureProfile(any(User.class));
    assertThat(savedUsers)
        .anySatisfy(
            user -> {
              assertThat(user.getEmail()).isEqualTo("manager@promova.com");
              assertThat(user.getRole()).isEqualTo(UserRole.MANAGER);
            });
  }
}

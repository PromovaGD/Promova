package br.com.promova.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmailIgnoreCase(String email);

  List<User> findByRoleOrderByNameAsc(UserRole role);

  @Query("SELECT u FROM User u WHERE u.id != :excludeId ORDER BY u.name ASC")
  List<User> findAllExcept(@Param("excludeId") Long excludeId);
}

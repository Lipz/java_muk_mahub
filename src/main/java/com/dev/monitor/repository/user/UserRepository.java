package com.dev.monitor.repository.user;

import com.dev.monitor.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    User findByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
}

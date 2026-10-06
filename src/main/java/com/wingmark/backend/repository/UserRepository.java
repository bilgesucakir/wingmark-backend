package com.wingmark.backend.repository;

import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** User accounts. */
public interface UserRepository extends MongoRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRole(Role role);

    List<User> findByEmailVerifiedFalseAndCreatedAtBefore(java.time.Instant createdBefore);

    boolean existsByProfilePictureEndingWith(String suffix);
}

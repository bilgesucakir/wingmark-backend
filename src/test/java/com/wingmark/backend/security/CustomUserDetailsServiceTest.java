package com.wingmark.backend.security;

import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Role;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void loadsAUserByEmailCaseInsensitively() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("Someone@Example.com")
                .passwordHash("hashed")
                .username("someone")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmailIgnoreCase("Someone@Example.com")).thenReturn(Optional.of(user));

        var details = service.loadUserByUsername("Someone@Example.com");

        assertThat(details.getUsername()).isEqualTo("Someone@Example.com");
        assertThat(details.getPassword()).isEqualTo("hashed");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(((UserPrincipal) details).getId()).isEqualTo(user.getId());
    }

    @Test
    void throwsWhenNoUserHasThatEmail() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}

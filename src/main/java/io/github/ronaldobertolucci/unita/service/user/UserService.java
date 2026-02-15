package io.github.ronaldobertolucci.unita.service.user;

import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.dto.user.UserRegistrationDto;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.RoleRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<UserDto> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(UserDto::new);
    }

    public List<UserDto> findAll() {
        return userRepository.findAll().stream()
                .map(UserDto::new)
                .toList();
    }

    public UserDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        return new UserDto(user);
    }

    @Transactional
    public UserDto register(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("USER role not found"));

        User user = User.builder()
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .email(dto.email())
                .dateOfBirth(dto.dateOfBirth())
                .password(passwordEncoder.encode(dto.password()))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        return new UserDto(userRepository.save(user));
    }

    @Transactional
    public UserDto disable(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setEnabled(false);
        return new UserDto(user);
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public UserDto enable(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
        user.setEnabled(true);
        return new UserDto(user);
    }
}
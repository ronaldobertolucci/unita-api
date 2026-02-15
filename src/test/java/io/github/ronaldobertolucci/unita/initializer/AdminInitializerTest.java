package io.github.ronaldobertolucci.unita.initializer;

import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.repository.RoleRepository;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments args;

    @InjectMocks
    private AdminInitializer adminInitializer;

    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role(1L, "ADMIN");

        // Configura @Value fields
        ReflectionTestUtils.setField(adminInitializer, "adminInitEnabled", true);
        ReflectionTestUtils.setField(adminInitializer, "adminEmail", "admin@example.com");
        ReflectionTestUtils.setField(adminInitializer, "adminPassword", "admin123");
        ReflectionTestUtils.setField(adminInitializer, "adminFirstName", "Admin");
        ReflectionTestUtils.setField(adminInitializer, "adminLastName", "System");
    }

    @Test
    void run_WhenInitializationIsDisabled_ShouldNotCreateAdmin() throws Exception {
        // Arrange
        ReflectionTestUtils.setField(adminInitializer, "adminInitEnabled", false);

        // Act
        adminInitializer.run(args);

        // Assert
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_WhenAdminAlreadyExists_ShouldNotCreateAdmin() throws Exception {
        // Arrange
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        // Act
        adminInitializer.run(args);

        // Assert
        verify(userRepository, times(1)).existsByEmail("admin@example.com");
        verify(roleRepository, never()).findByName(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_WhenAdminDoesNotExist_ShouldCreateAdmin() throws Exception {
        // Arrange
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        adminInitializer.run(args);

        // Assert
        verify(userRepository, times(1)).existsByEmail("admin@example.com");
        verify(roleRepository, times(1)).findByName("ADMIN");
        verify(passwordEncoder, times(1)).encode("admin123");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("Admin", savedUser.getFirstName());
        assertEquals("System", savedUser.getLastName());
        assertEquals("admin@example.com", savedUser.getEmail());
        assertEquals("encodedPassword", savedUser.getPassword());
        assertTrue(savedUser.getEnabled());
        assertTrue(savedUser.getRoles().contains(adminRole));
    }

    @Test
    void run_WhenAdminRoleNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> adminInitializer.run(args));

        assertTrue(exception.getMessage().contains("ADMIN role not found"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void run_WhenAdminIsCreated_ShouldEncodePassword() throws Exception {
        // Arrange
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("admin123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        adminInitializer.run(args);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("encodedPassword", savedUser.getPassword());
        assertNotEquals("admin123", savedUser.getPassword());
    }

    @Test
    void run_WhenAdminIsCreated_ShouldHaveAdminRole() throws Exception {
        // Arrange
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        adminInitializer.run(args);

        // Assert
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertFalse(savedUser.getRoles().isEmpty());
        assertTrue(savedUser.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ADMIN")));
    }
}
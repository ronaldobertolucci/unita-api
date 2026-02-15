package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.dto.user.LoginDto;
import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.dto.user.UserRegistrationDto;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.*;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import io.github.ronaldobertolucci.unita.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthenticationController.class)
@Import(TestConfig.class)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    private User testUser;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        Role userRole = new Role(1L, "USER");

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("encodedPassword")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        userDto = new UserDto(testUser);
    }

    @Test
    void login_WhenCredentialsAreValid_ShouldReturnToken() throws Exception {
        // Arrange
        LoginDto loginDto = new LoginDto("john@example.com", "password123");

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(testUser);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenService.generateToken(any(User.class))).thenReturn("mock-jwt-token");

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("john@example.com"))
                .andExpect(jsonPath("$.user.firstName").value("John"));

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(tokenService, times(1)).generateToken(testUser);
    }

    @Test
    void login_WhenCredentialsAreInvalid_ShouldReturnUnauthorized() throws Exception {
        // Arrange
        LoginDto loginDto = new LoginDto("john@example.com", "wrongpassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isUnauthorized());

        verify(tokenService, never()).generateToken(any(User.class));
    }

    @Test
    void login_WhenEmailIsBlank_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LoginDto invalidDto = new LoginDto("", "password123");

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void login_WhenEmailIsInvalid_ShouldReturnBadRequest() throws Exception {
        // Arrange
        LoginDto invalidDto = new LoginDto("not-an-email", "password123");

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void register_WhenDataIsValid_ShouldCreateUser() throws Exception {
        // Arrange
        UserRegistrationDto registrationDto = new UserRegistrationDto(
                "John",
                "Doe",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "password123"
        );

        when(userService.register(any(UserRegistrationDto.class))).thenReturn(userDto);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(userService, times(1)).register(any(UserRegistrationDto.class));
    }

    @Test
    void register_WhenEmailAlreadyExists_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UserRegistrationDto registrationDto = new UserRegistrationDto(
                "John",
                "Doe",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "password123"
        );

        when(userService.register(any(UserRegistrationDto.class)))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already registered"));

        verify(userService, times(1)).register(any(UserRegistrationDto.class));
    }

    @Test
    void register_WhenPasswordIsTooShort_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UserRegistrationDto invalidDto = new UserRegistrationDto(
                "John",
                "Doe",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "short"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).register(any());
    }

    @Test
    void register_WhenEmailIsInvalid_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UserRegistrationDto invalidDto = new UserRegistrationDto(
                "John",
                "Doe",
                "invalid-email",
                LocalDate.of(1990, 1, 1),
                "password123"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).register(any());
    }

    @Test
    void register_WhenFirstNameIsBlank_ShouldReturnBadRequest() throws Exception {
        // Arrange
        UserRegistrationDto invalidDto = new UserRegistrationDto(
                "",
                "Doe",
                "john@example.com",
                LocalDate.of(1990, 1, 1),
                "password123"
        );

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());

        verify(userService, never()).register(any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void getCurrentUser_WhenAuthenticated_ShouldReturnUserData() throws Exception {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(testUser);

        // Act & Assert
        mockMvc.perform(get("/auth/me")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"));
    }
}
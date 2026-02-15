package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationResponseDto;
import io.github.ronaldobertolucci.unita.model.group.*;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.group.GroupInvitationService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GroupInvitationController.class)
@Import(TestConfig.class)
class GroupInvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private GroupInvitationService invitationService;

    private User invitingUser;
    private User invitedUser;
    private Group testGroup;
    private GroupInvitation testInvitation;
    private GroupInvitationDto invitationDto;

    @BeforeEach
    void setUp() {
        Role userRole = new Role(1L, "USER");

        invitingUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        invitedUser = User.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1995, 5, 5))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        testGroup = Group.builder()
                .id(1L)
                .name("Family")
                .responsibleUser(invitingUser)
                .build();

        testInvitation = GroupInvitation.builder()
                .id(1L)
                .group(testGroup)
                .invitedUser(invitedUser)
                .invitingUser(invitingUser)
                .status(InvitationStatus.PENDING)
                .build();

        invitationDto = new GroupInvitationDto(testInvitation);
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void createInvitation_WhenDataIsValid_ShouldCreateInvitation() throws Exception {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(invitationService.createInvitation(any(GroupInvitationCreateDto.class), any()))
                .thenReturn(invitationDto);

        // Act & Assert
        mockMvc.perform(post("/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(invitationService, times(1)).createInvitation(any(GroupInvitationCreateDto.class), any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void createInvitation_WhenGroupIdIsNull_ShouldReturnBadRequest() throws Exception {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(null, 2L);

        // Act & Assert
        mockMvc.perform(post("/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(invitationService, never()).createInvitation(any(), any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void createInvitation_WhenInvitedUserIdIsNull_ShouldReturnBadRequest() throws Exception {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, null);

        // Act & Assert
        mockMvc.perform(post("/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());

        verify(invitationService, never()).createInvitation(any(), any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void getMyPendingInvitations_ShouldReturnList() throws Exception {
        // Arrange
        when(invitationService.getMyPendingInvitations(any()))
                .thenReturn(List.of(invitationDto));

        // Act & Assert
        mockMvc.perform(get("/invitations/my/pending"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(invitationService, times(1)).getMyPendingInvitations(any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void getMyPendingInvitationsCount_ShouldReturnCount() throws Exception {
        // Arrange
        when(invitationService.getMyPendingInvitationsCount(any()))
                .thenReturn(3L);

        // Act & Assert
        mockMvc.perform(get("/invitations/my/pending/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));

        verify(invitationService, times(1)).getMyPendingInvitationsCount(any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void getGroupInvitations_ShouldReturnList() throws Exception {
        // Arrange
        when(invitationService.getGroupInvitations(eq(1L), any()))
                .thenReturn(List.of(invitationDto));

        // Act & Assert
        mockMvc.perform(get("/invitations/group/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(invitationService, times(1)).getGroupInvitations(eq(1L), any());
    }

    @Test
    @WithMockUser(username = "jane@example.com", roles = "USER")
    void respondToInvitation_WhenAccepted_ShouldAcceptInvitation() throws Exception {
        // Arrange
        GroupInvitationResponseDto dto = new GroupInvitationResponseDto(true);
        testInvitation.setStatus(InvitationStatus.ACCEPTED);
        GroupInvitationDto acceptedDto = new GroupInvitationDto(testInvitation);

        when(invitationService.respondToInvitation(eq(1L), any(GroupInvitationResponseDto.class), any()))
                .thenReturn(acceptedDto);

        // Act & Assert
        mockMvc.perform(put("/invitations/1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(invitationService, times(1)).respondToInvitation(eq(1L), any(GroupInvitationResponseDto.class), any());
    }

    @Test
    @WithMockUser(username = "jane@example.com", roles = "USER")
    void respondToInvitation_WhenRejected_ShouldRejectInvitation() throws Exception {
        // Arrange
        GroupInvitationResponseDto dto = new GroupInvitationResponseDto(false);
        testInvitation.setStatus(InvitationStatus.REJECTED);
        GroupInvitationDto rejectedDto = new GroupInvitationDto(testInvitation);

        when(invitationService.respondToInvitation(eq(1L), any(GroupInvitationResponseDto.class), any()))
                .thenReturn(rejectedDto);

        // Act & Assert
        mockMvc.perform(put("/invitations/1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(invitationService, times(1)).respondToInvitation(eq(1L), any(GroupInvitationResponseDto.class), any());
    }

    @Test
    @WithMockUser(username = "jane@example.com", roles = "USER")
    void respondToInvitation_WhenAcceptIsNull_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String invalidJson = "{\"accept\": null}";

        // Act & Assert
        mockMvc.perform(put("/invitations/1/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(invitationService, never()).respondToInvitation(any(), any(), any());
    }

    @Test
    @WithMockUser(username = "john@example.com", roles = "USER")
    void cancelInvitation_ShouldCancelInvitation() throws Exception {
        // Arrange
        doNothing().when(invitationService).cancelInvitation(eq(1L), any());

        // Act & Assert
        mockMvc.perform(delete("/invitations/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Invitation cancelled successfully"));

        verify(invitationService, times(1)).cancelInvitation(eq(1L), any());
    }
}
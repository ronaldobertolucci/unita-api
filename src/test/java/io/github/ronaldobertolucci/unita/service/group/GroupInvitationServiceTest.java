package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupInvitationResponseDto;
import io.github.ronaldobertolucci.unita.model.group.*;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.repository.GroupInvitationRepository;
import io.github.ronaldobertolucci.unita.repository.GroupMembershipRepository;
import io.github.ronaldobertolucci.unita.repository.GroupRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupInvitationServiceTest {

    @Mock
    private GroupInvitationRepository invitationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupMembershipRepository membershipRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private GroupInvitationService invitationService;

    private User invitedUser;
    private Group testGroup;
    private GroupInvitation testInvitation;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = new Role(1L, "USER");

        User invitingUser = User.builder()
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

        when(authentication.getPrincipal()).thenReturn(invitingUser);
    }

    @Test
    void createInvitation_WhenAllConditionsMet_ShouldCreateInvitation() {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitedUser));
        when(membershipRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(false);
        when(invitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(1L, 2L, InvitationStatus.PENDING))
                .thenReturn(false);
        when(invitationRepository.save(any(GroupInvitation.class))).thenReturn(testInvitation);

        // Act
        GroupInvitationDto result = invitationService.createInvitation(dto, authentication);

        // Assert
        assertNotNull(result);
        assertEquals(InvitationStatus.PENDING, result.status());
        verify(invitationRepository, times(1)).save(any(GroupInvitation.class));
    }

    @Test
    void createInvitation_WhenGroupDoesNotExist_ShouldThrowException() {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class,
                () -> invitationService.createInvitation(dto, authentication));

        verify(invitationRepository, never()).save(any(GroupInvitation.class));
    }

    @Test
    void createInvitation_WhenInviterIsNotMember_ShouldThrowException() {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.createInvitation(dto, authentication));

        verify(invitationRepository, never()).save(any(GroupInvitation.class));
    }

    @Test
    void createInvitation_WhenInvitedUserDoesNotExist_ShouldThrowException() {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class,
                () -> invitationService.createInvitation(dto, authentication));

        verify(invitationRepository, never()).save(any(GroupInvitation.class));
    }

    @Test
    void createInvitation_WhenInvitedUserAlreadyMember_ShouldThrowException() {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitedUser));
        when(membershipRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.createInvitation(dto, authentication));

        verify(invitationRepository, never()).save(any(GroupInvitation.class));
    }

    @Test
    void createInvitation_WhenPendingInvitationExists_ShouldThrowException() {
        // Arrange
        GroupInvitationCreateDto dto = new GroupInvitationCreateDto(1L, 2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(invitedUser));
        when(membershipRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(false);
        when(invitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(1L, 2L, InvitationStatus.PENDING))
                .thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.createInvitation(dto, authentication));

        verify(invitationRepository, never()).save(any(GroupInvitation.class));
    }

    @Test
    void getMyPendingInvitations_WhenUserHasPendingInvitations_ShouldReturnList() {
        // Arrange
        when(invitationRepository.findPendingInvitationsByInvitedUserId(1L))
                .thenReturn(List.of(testInvitation));

        // Act
        List<GroupInvitationDto> result = invitationService.getMyPendingInvitations(authentication);

        // Assert
        assertEquals(1, result.size());
        assertEquals(InvitationStatus.PENDING, result.getFirst().status());
    }

    @Test
    void getMyPendingInvitationsCount_WhenUserHasPendingInvitations_ShouldReturnCount() {
        // Arrange
        when(invitationRepository.countByInvitedUserIdAndStatus(1L, InvitationStatus.PENDING))
                .thenReturn(3L);

        // Act
        Long result = invitationService.getMyPendingInvitationsCount(authentication);

        // Assert
        assertEquals(3L, result);
    }

    @Test
    void getGroupInvitations_WhenUserIsResponsible_ShouldReturnInvitations() {
        // Arrange
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(invitationRepository.findByGroupIdWithUsers(1L)).thenReturn(List.of(testInvitation));

        // Act
        List<GroupInvitationDto> result = invitationService.getGroupInvitations(1L, authentication);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void getGroupInvitations_WhenUserIsNotResponsible_ShouldThrowException() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(invitedUser);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.getGroupInvitations(1L, authentication));
    }

    @Test
    void respondToInvitation_WhenAccepted_ShouldAcceptAndCreateMembership() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(invitedUser);
        GroupInvitationResponseDto dto = new GroupInvitationResponseDto(true);
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));
        when(invitationRepository.save(any(GroupInvitation.class))).thenReturn(testInvitation);

        // Act
        GroupInvitationDto result = invitationService.respondToInvitation(1L, dto, authentication);

        // Assert
        assertNotNull(result);
        verify(invitationRepository, times(1)).save(testInvitation);
        verify(membershipRepository, times(1)).save(any(GroupMembership.class));
    }

    @Test
    void respondToInvitation_WhenRejected_ShouldRejectWithoutCreatingMembership() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(invitedUser);
        GroupInvitationResponseDto dto = new GroupInvitationResponseDto(false);
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));
        when(invitationRepository.save(any(GroupInvitation.class))).thenReturn(testInvitation);

        // Act
        GroupInvitationDto result = invitationService.respondToInvitation(1L, dto, authentication);

        // Assert
        assertNotNull(result);
        verify(invitationRepository, times(1)).save(testInvitation);
        verify(membershipRepository, never()).save(any(GroupMembership.class));
    }

    @Test
    void respondToInvitation_WhenInvitationNotForUser_ShouldThrowException() {
        // Arrange
        GroupInvitationResponseDto dto = new GroupInvitationResponseDto(true);
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.respondToInvitation(1L, dto, authentication));
    }

    @Test
    void respondToInvitation_WhenInvitationAlreadyResponded_ShouldThrowException() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(invitedUser);
        testInvitation.setStatus(InvitationStatus.ACCEPTED);
        GroupInvitationResponseDto dto = new GroupInvitationResponseDto(true);
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.respondToInvitation(1L, dto, authentication));
    }

    @Test
    void cancelInvitation_WhenUserIsInviter_ShouldCancelInvitation() {
        // Arrange
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));

        // Act
        invitationService.cancelInvitation(1L, authentication);

        // Assert
        verify(invitationRepository, times(1)).delete(testInvitation);
    }

    @Test
    void cancelInvitation_WhenUserIsGroupOwner_ShouldCancelInvitation() {
        // Arrange
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));

        // Act
        invitationService.cancelInvitation(1L, authentication);

        // Assert
        verify(invitationRepository, times(1)).delete(testInvitation);
    }

    @Test
    void cancelInvitation_WhenUserIsNeitherInviterNorOwner_ShouldThrowException() {
        // Arrange
        User otherUser = User.builder()
                .id(3L)
                .firstName("Other")
                .lastName("User")
                .email("other@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1985, 3, 15))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        when(authentication.getPrincipal()).thenReturn(otherUser);
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.cancelInvitation(1L, authentication));

        verify(invitationRepository, never()).delete(any(GroupInvitation.class));
    }

    @Test
    void cancelInvitation_WhenInvitationNotPending_ShouldThrowException() {
        // Arrange
        testInvitation.setStatus(InvitationStatus.ACCEPTED);
        when(invitationRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(testInvitation));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> invitationService.cancelInvitation(1L, authentication));

        verify(invitationRepository, never()).delete(any(GroupInvitation.class));
    }
}
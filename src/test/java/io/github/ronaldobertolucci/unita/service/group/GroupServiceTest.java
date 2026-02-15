package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.GroupCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupMembershipDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupUpdateResponsibleDto;
import io.github.ronaldobertolucci.unita.model.group.*;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private GroupService groupService;

    private User testUser;
    private User otherUser;
    private Group testGroup;

    @BeforeEach
    void setUp() {
        Role userRole = new Role(1L, "USER");

        testUser = User.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        otherUser = User.builder()
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
                .responsibleUser(testUser)
                .build();

        when(authentication.getPrincipal()).thenReturn(testUser);
    }

    @Test
    void createGroup_WhenNameIsUnique_ShouldCreateGroup() {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("Family");
        when(groupRepository.existsByNameAndResponsibleUserId("Family", 1L)).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

        // Act
        GroupDto result = groupService.createGroup(dto, authentication);

        // Assert
        assertNotNull(result);
        assertEquals("Family", result.name());

        verify(groupRepository, times(1)).save(any(Group.class));
        verify(membershipRepository, times(1)).save(any(GroupMembership.class));
    }

    @Test
    void createGroup_WhenNameAlreadyExists_ShouldThrowException() {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("Family");
        when(groupRepository.existsByNameAndResponsibleUserId("Family", 1L)).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.createGroup(dto, authentication));

        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void getMyGroups_WhenUserHasGroups_ShouldReturnList() {
        // Arrange
        when(groupRepository.findGroupsByMemberUserId(1L)).thenReturn(List.of(testGroup));

        // Act
        List<GroupDto> result = groupService.getMyGroups(authentication);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Family", result.getFirst().name());
    }

    @Test
    void getGroupsWhereIAmResponsible_WhenUserIsResponsible_ShouldReturnList() {
        // Arrange
        when(groupRepository.findByResponsibleUserId(1L)).thenReturn(List.of(testGroup));

        // Act
        List<GroupDto> result = groupService.getGroupsWhereIAmResponsible(authentication);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Family", result.getFirst().name());
    }

    @Test
    void getGroupById_WhenUserIsMember_ShouldReturnGroup() {
        // Arrange
        when(groupRepository.findByIdWithResponsible(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);

        // Act
        GroupDto result = groupService.getGroupById(1L, authentication);

        // Assert
        assertNotNull(result);
        assertEquals("Family", result.name());
    }

    @Test
    void getGroupById_WhenUserIsNotMember_ShouldThrowException() {
        // Arrange
        when(groupRepository.findByIdWithResponsible(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.getGroupById(1L, authentication));
    }

    @Test
    void getGroupById_WhenGroupDoesNotExist_ShouldThrowException() {
        // Arrange
        when(groupRepository.findByIdWithResponsible(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class,
                () -> groupService.getGroupById(1L, authentication));
    }

    @Test
    void updateGroupName_WhenUserIsResponsible_ShouldUpdateName() {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("New Family");
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(groupRepository.existsByNameAndResponsibleUserId("New Family", 1L)).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

        // Act
        GroupDto result = groupService.updateGroupName(1L, dto, authentication);

        // Assert
        assertNotNull(result);
        verify(groupRepository, times(1)).save(testGroup);
    }

    @Test
    void updateGroupName_WhenUserIsNotResponsible_ShouldThrowException() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(otherUser);
        GroupCreateDto dto = new GroupCreateDto("New Family");
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.updateGroupName(1L, dto, authentication));
    }

    @Test
    void transferResponsibility_WhenUserIsResponsibleAndNewUserIsMember_ShouldTransfer() {
        // Arrange
        GroupUpdateResponsibleDto dto = new GroupUpdateResponsibleDto(2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(groupRepository.save(any(Group.class))).thenReturn(testGroup);

        // Act
        GroupDto result = groupService.transferResponsibility(1L, dto, authentication);

        // Assert
        assertNotNull(result);
        verify(groupRepository, times(1)).save(testGroup);
    }

    @Test
    void transferResponsibility_WhenUserIsNotResponsible_ShouldThrowException() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(otherUser);
        GroupUpdateResponsibleDto dto = new GroupUpdateResponsibleDto(1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.transferResponsibility(1L, dto, authentication));
    }

    @Test
    void transferResponsibility_WhenNewUserIsNotMember_ShouldThrowException() {
        // Arrange
        GroupUpdateResponsibleDto dto = new GroupUpdateResponsibleDto(2L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.transferResponsibility(1L, dto, authentication));
    }

    @Test
    void deleteGroup_WhenUserIsResponsible_ShouldDeleteGroup() {
        // Arrange
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // Act
        groupService.deleteGroup(1L, authentication);

        // Assert
        verify(groupRepository, times(1)).delete(testGroup);
    }

    @Test
    void deleteGroup_WhenUserIsNotResponsible_ShouldThrowException() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(otherUser);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.deleteGroup(1L, authentication));

        verify(groupRepository, never()).delete(any(Group.class));
    }

    @Test
    void leaveGroup_WhenUserIsMemberButNotResponsible_ShouldLeaveGroup() {
        // Arrange
        when(authentication.getPrincipal()).thenReturn(otherUser);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(2L, 1L)).thenReturn(true);

        // Act
        groupService.leaveGroup(1L, authentication);

        // Assert
        verify(membershipRepository, times(1)).deleteByUserIdAndGroupId(2L, 1L);
    }

    @Test
    void leaveGroup_WhenUserIsResponsible_ShouldThrowException() {
        // Arrange
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.leaveGroup(1L, authentication));

        verify(membershipRepository, never()).deleteByUserIdAndGroupId(anyLong(), anyLong());
    }

    @Test
    void leaveGroup_WhenUserIsNotMember_ShouldThrowException() {
        // Arrange
        when(groupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.leaveGroup(1L, authentication));
    }

    @Test
    void getGroupMembers_WhenUserIsMember_ShouldReturnMembers() {
        // Arrange
        GroupMembership membership = GroupMembership.builder()
                .id(1L)
                .user(testUser)
                .group(testGroup)
                .build();

        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(membershipRepository.findByGroupIdWithUsers(1L)).thenReturn(List.of(membership));

        // Act
        List<GroupMembershipDto> result = groupService.getGroupMembers(1L, authentication);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void getGroupMembers_WhenUserIsNotMember_ShouldThrowException() {
        // Arrange
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.getGroupMembers(1L, authentication));
    }

    @Test
    void getGroupMemberCount_WhenUserIsMember_ShouldReturnCount() {
        // Arrange
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(membershipRepository.countByGroupId(1L)).thenReturn(5L);

        // Act
        Long result = groupService.getGroupMemberCount(1L, authentication);

        // Assert
        assertEquals(5L, result);
    }

    @Test
    void getGroupMemberCount_WhenUserIsNotMember_ShouldThrowException() {
        // Arrange
        when(membershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class,
                () -> groupService.getGroupMemberCount(1L, authentication));
    }
}
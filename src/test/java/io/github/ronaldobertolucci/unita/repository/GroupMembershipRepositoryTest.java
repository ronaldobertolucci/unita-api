package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupMembershipRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GroupMembershipRepository membershipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User user1;
    private User user2;
    private Group group1;
    private Group group2;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("USER role not found"));

        user1 = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        user1 = userRepository.save(user1);

        user2 = User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1995, 5, 5))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        user2 = userRepository.save(user2);

        group1 = Group.builder()
                .name("Group 1")
                .responsibleUser(user1)
                .build();
        group1 = groupRepository.save(group1);

        group2 = Group.builder()
                .name("Group 2")
                .responsibleUser(user1)
                .build();
        group2 = groupRepository.save(group2);
    }

    @Test
    void existsByUserIdAndGroupId_WhenMembershipExists_ShouldReturnTrue() {
        // Arrange
        GroupMembership membership = GroupMembership.builder()
                .user(user1)
                .group(group1)
                .build();
        membershipRepository.save(membership);

        // Act
        boolean exists = membershipRepository.existsByUserIdAndGroupId(
                user1.getId(),
                group1.getId()
        );

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsByUserIdAndGroupId_WhenMembershipDoesNotExist_ShouldReturnFalse() {
        // Act
        boolean exists = membershipRepository.existsByUserIdAndGroupId(
                user1.getId(),
                group1.getId()
        );

        // Assert
        assertFalse(exists);
    }

    @Test
    void findByUserIdAndGroupId_WhenMembershipExists_ShouldReturnMembership() {
        // Arrange
        GroupMembership membership = GroupMembership.builder()
                .user(user1)
                .group(group1)
                .build();
        membershipRepository.save(membership);

        // Act
        Optional<GroupMembership> result = membershipRepository.findByUserIdAndGroupId(
                user1.getId(),
                group1.getId()
        );

        // Assert
        assertTrue(result.isPresent());
        assertEquals(user1.getId(), result.get().getUser().getId());
        assertEquals(group1.getId(), result.get().getGroup().getId());
    }

    @Test
    void findByUserIdAndGroupId_WhenMembershipDoesNotExist_ShouldReturnEmpty() {
        // Act
        Optional<GroupMembership> result = membershipRepository.findByUserIdAndGroupId(
                user1.getId(),
                group1.getId()
        );

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findByGroupIdWithUsers_WhenGroupHasMembers_ShouldReturnListWithUsers() {
        // Arrange
        membershipRepository.save(GroupMembership.builder()
                .user(user1)
                .group(group1)
                .build());

        membershipRepository.save(GroupMembership.builder()
                .user(user2)
                .group(group1)
                .build());

        entityManager.flush();
        entityManager.clear();

        // Act
        List<GroupMembership> memberships = membershipRepository.findByGroupIdWithUsers(group1.getId());

        // Assert
        assertEquals(2, memberships.size());
        assertNotNull(memberships.get(0).getUser());
        assertNotNull(memberships.get(1).getUser());
    }

    @Test
    void findByGroupIdWithUsers_WhenGroupHasNoMembers_ShouldReturnEmptyList() {
        // Act
        List<GroupMembership> memberships = membershipRepository.findByGroupIdWithUsers(group1.getId());

        // Assert
        assertTrue(memberships.isEmpty());
    }

    @Test
    void findByUserIdWithGroups_WhenUserIsMemberOfGroups_ShouldReturnListWithGroups() {
        // Arrange
        membershipRepository.save(GroupMembership.builder()
                .user(user1)
                .group(group1)
                .build());

        membershipRepository.save(GroupMembership.builder()
                .user(user1)
                .group(group2)
                .build());

        entityManager.flush();
        entityManager.clear();

        // Act
        List<GroupMembership> memberships = membershipRepository.findByUserIdWithGroups(user1.getId());

        // Assert
        assertEquals(2, memberships.size());
        assertNotNull(memberships.get(0).getGroup());
        assertNotNull(memberships.get(0).getGroup().getResponsibleUser());
    }

    @Test
    void findByUserIdWithGroups_WhenUserIsNotMemberOfAnyGroup_ShouldReturnEmptyList() {
        // Act
        List<GroupMembership> memberships = membershipRepository.findByUserIdWithGroups(user1.getId());

        // Assert
        assertTrue(memberships.isEmpty());
    }

    @Test
    void countByGroupId_WhenGroupHasMembers_ShouldReturnCorrectCount() {
        // Arrange
        membershipRepository.save(GroupMembership.builder()
                .user(user1)
                .group(group1)
                .build());

        membershipRepository.save(GroupMembership.builder()
                .user(user2)
                .group(group1)
                .build());

        // Act
        long count = membershipRepository.countByGroupId(group1.getId());

        // Assert
        assertEquals(2, count);
    }

    @Test
    void countByGroupId_WhenGroupHasNoMembers_ShouldReturnZero() {
        // Act
        long count = membershipRepository.countByGroupId(group1.getId());

        // Assert
        assertEquals(0, count);
    }

    @Test
    void deleteByUserIdAndGroupId_WhenMembershipExists_ShouldDeleteIt() {
        // Arrange
        GroupMembership membership = membershipRepository.save(GroupMembership.builder()
                .user(user1)
                .group(group1)
                .build());
        entityManager.flush();

        // Act
        membershipRepository.deleteByUserIdAndGroupId(user1.getId(), group1.getId());
        entityManager.flush();

        // Assert
        Optional<GroupMembership> result = membershipRepository.findByUserIdAndGroupId(
                user1.getId(),
                group1.getId()
        );
        assertTrue(result.isEmpty());
    }
}
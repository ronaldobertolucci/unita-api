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

class GroupRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupMembershipRepository membershipRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User responsibleUser;
    private User memberUser;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("USER role not found"));

        responsibleUser = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        responsibleUser = userRepository.save(responsibleUser);

        memberUser = User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1995, 5, 5))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        memberUser = userRepository.save(memberUser);
    }

    @Test
    void existsByNameAndResponsibleUserId_WhenGroupExists_ShouldReturnTrue() {
        // Arrange
        Group group = Group.builder()
                .name("Family")
                .responsibleUser(responsibleUser)
                .build();
        groupRepository.save(group);

        // Act
        boolean exists = groupRepository.existsByNameAndResponsibleUserId(
                "Family",
                responsibleUser.getId()
        );

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsByNameAndResponsibleUserId_WhenGroupDoesNotExist_ShouldReturnFalse() {
        // Act
        boolean exists = groupRepository.existsByNameAndResponsibleUserId(
                "NonExistent",
                responsibleUser.getId()
        );

        // Assert
        assertFalse(exists);
    }

    @Test
    void existsByNameAndResponsibleUserId_WhenSameNameDifferentResponsible_ShouldReturnFalse() {
        // Arrange
        Group group = Group.builder()
                .name("Family")
                .responsibleUser(responsibleUser)
                .build();
        groupRepository.save(group);

        // Act
        boolean exists = groupRepository.existsByNameAndResponsibleUserId(
                "Family",
                memberUser.getId()
        );

        // Assert
        assertFalse(exists);
    }

    @Test
    void findByResponsibleUserId_WhenUserHasGroups_ShouldReturnList() {
        // Arrange
        Group group1 = groupRepository.save(Group.builder()
                .name("Group 1")
                .responsibleUser(responsibleUser)
                .build());

        Group group2 = groupRepository.save(Group.builder()
                .name("Group 2")
                .responsibleUser(responsibleUser)
                .build());

        // Act
        List<Group> groups = groupRepository.findByResponsibleUserId(responsibleUser.getId());

        // Assert
        assertEquals(2, groups.size());
    }

    @Test
    void findByResponsibleUserId_WhenUserHasNoGroups_ShouldReturnEmptyList() {
        // Act
        List<Group> groups = groupRepository.findByResponsibleUserId(memberUser.getId());

        // Assert
        assertTrue(groups.isEmpty());
    }

    @Test
    void findByIdWithResponsible_WhenGroupExists_ShouldReturnGroupWithResponsible() {
        // Arrange
        Group group = Group.builder()
                .name("Test Group")
                .responsibleUser(responsibleUser)
                .build();
        group = groupRepository.save(group);
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<Group> result = groupRepository.findByIdWithResponsible(group.getId());

        // Assert
        assertTrue(result.isPresent());
        assertNotNull(result.get().getResponsibleUser());
        assertEquals("john@example.com", result.get().getResponsibleUser().getEmail());
    }

    @Test
    void findByIdWithResponsible_WhenGroupDoesNotExist_ShouldReturnEmpty() {
        // Act
        Optional<Group> result = groupRepository.findByIdWithResponsible(999L);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    void findGroupsByMemberUserId_WhenUserIsMemberOfGroups_ShouldReturnList() {
        // Arrange
        Group group1 = groupRepository.save(Group.builder()
                .name("Group 1")
                .responsibleUser(responsibleUser)
                .build());

        Group group2 = groupRepository.save(Group.builder()
                .name("Group 2")
                .responsibleUser(responsibleUser)
                .build());

        membershipRepository.save(GroupMembership.builder()
                .user(memberUser)
                .group(group1)
                .build());

        membershipRepository.save(GroupMembership.builder()
                .user(memberUser)
                .group(group2)
                .build());

        entityManager.flush();
        entityManager.clear();

        // Act
        List<Group> groups = groupRepository.findGroupsByMemberUserId(memberUser.getId());

        // Assert
        assertEquals(2, groups.size());
    }

    @Test
    void findGroupsByMemberUserId_WhenUserIsNotMemberOfAnyGroup_ShouldReturnEmptyList() {
        // Arrange
        groupRepository.save(Group.builder()
                .name("Group 1")
                .responsibleUser(responsibleUser)
                .build());

        // Act
        List<Group> groups = groupRepository.findGroupsByMemberUserId(memberUser.getId());

        // Assert
        assertTrue(groups.isEmpty());
    }
}
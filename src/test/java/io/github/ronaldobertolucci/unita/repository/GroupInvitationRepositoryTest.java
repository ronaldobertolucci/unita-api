package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupInvitation;
import io.github.ronaldobertolucci.unita.model.group.InvitationStatus;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GroupInvitationRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GroupInvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User invitingUser;
    private User invitedUser;
    private Group testGroup;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("USER role not found"));

        invitingUser = User.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        invitingUser = userRepository.save(invitingUser);

        invitedUser = User.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("jane@example.com")
                .password("password")
                .dateOfBirth(LocalDate.of(1995, 5, 5))
                .enabled(true)
                .roles(new HashSet<>(Set.of(userRole)))
                .build();
        invitedUser = userRepository.save(invitedUser);

        testGroup = Group.builder()
                .name("Test Group")
                .responsibleUser(invitingUser)
                .build();
        testGroup = groupRepository.save(testGroup);
    }

    @Test
    void existsByGroupIdAndInvitedUserIdAndStatus_WhenPendingInvitationExists_ShouldReturnTrue() {
        // Arrange
        GroupInvitation invitation = GroupInvitation.builder()
                .group(testGroup)
                .invitedUser(invitedUser)
                .invitingUser(invitingUser)
                .status(InvitationStatus.PENDING)
                .build();
        invitationRepository.save(invitation);
        entityManager.flush();

        // Act
        boolean exists = invitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(
                testGroup.getId(),
                invitedUser.getId(),
                InvitationStatus.PENDING
        );

        // Assert
        assertTrue(exists);
    }

    @Test
    void existsByGroupIdAndInvitedUserIdAndStatus_WhenNoPendingInvitation_ShouldReturnFalse() {
        // Act
        boolean exists = invitationRepository.existsByGroupIdAndInvitedUserIdAndStatus(
                testGroup.getId(),
                invitedUser.getId(),
                InvitationStatus.PENDING
        );

        // Assert
        assertFalse(exists);
    }

    @Test
    void findPendingInvitationsByInvitedUserId_WhenPendingInvitationsExist_ShouldReturnList() {
        // Arrange
        GroupInvitation invitation = GroupInvitation.builder()
                .group(testGroup)
                .invitedUser(invitedUser)
                .invitingUser(invitingUser)
                .status(InvitationStatus.PENDING)
                .build();
        invitationRepository.save(invitation);
        entityManager.flush();
        entityManager.clear();

        // Act
        List<GroupInvitation> invitations = invitationRepository
                .findPendingInvitationsByInvitedUserId(invitedUser.getId());

        // Assert
        assertEquals(1, invitations.size());
        assertEquals(InvitationStatus.PENDING, invitations.get(0).getStatus());
        assertNotNull(invitations.get(0).getGroup());
        assertNotNull(invitations.get(0).getInvitingUser());
    }

    @Test
    void countByInvitedUserIdAndStatus_WhenMultiplePendingInvitations_ShouldReturnCorrectCount() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            Group group = groupRepository.save(Group.builder()
                    .name("Group " + i)
                    .responsibleUser(invitingUser)
                    .build());

            invitationRepository.save(GroupInvitation.builder()
                    .group(group)
                    .invitedUser(invitedUser)
                    .invitingUser(invitingUser)
                    .status(InvitationStatus.PENDING)
                    .build());
        }
        entityManager.flush();

        // Act
        long count = invitationRepository.countByInvitedUserIdAndStatus(
                invitedUser.getId(),
                InvitationStatus.PENDING
        );

        // Assert
        assertEquals(3, count);
    }
}
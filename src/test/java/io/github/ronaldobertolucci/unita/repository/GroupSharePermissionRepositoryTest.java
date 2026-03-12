package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class GroupSharePermissionRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private GroupSharePermissionRepository groupSharePermissionRepository;
    @Autowired
    private GroupRepository groupRepository;

    private User user;
    private User otherUser;
    private Group group;

    @BeforeEach
    void setUp() {
        user = saveUser("user@test.com");
        otherUser = saveUser("other@test.com");
        group = saveGroup(user);
    }

    // -------------------------------------------------------------------------
    // findAllByGroupIdAndUserId
    // -------------------------------------------------------------------------

    @Test
    void findAllByGroupIdAndUserId_WhenPermissionsExist_ShouldReturnAll() {
        savePermission(group, user, ShareType.BALANCE, true);
        savePermission(group, user, ShareType.INVESTMENTS, false);

        List<GroupSharePermission> result =
                groupSharePermissionRepository.findAllByGroupIdAndUserId(group.getId(), user.getId());

        assertEquals(2, result.size());
    }

    @Test
    void findAllByGroupIdAndUserId_WhenNoPermissions_ShouldReturnEmpty() {
        List<GroupSharePermission> result =
                groupSharePermissionRepository.findAllByGroupIdAndUserId(group.getId(), user.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllByGroupIdAndUserId_ShouldNotReturnOtherUsersPermissions() {
        savePermission(group, user, ShareType.BALANCE, true);
        savePermission(group, otherUser, ShareType.BALANCE, true);

        List<GroupSharePermission> result =
                groupSharePermissionRepository.findAllByGroupIdAndUserId(group.getId(), user.getId());

        assertEquals(1, result.size());
        assertEquals(user.getId(), result.get(0).getUser().getId());
    }

    // -------------------------------------------------------------------------
    // findByGroupIdAndUserIdAndShareType
    // -------------------------------------------------------------------------

    @Test
    void findByGroupIdAndUserIdAndShareType_WhenExists_ShouldReturnPermission() {
        savePermission(group, user, ShareType.BALANCE, true);

        Optional<GroupSharePermission> result = groupSharePermissionRepository
                .findByGroupIdAndUserIdAndShareType(group.getId(), user.getId(), ShareType.BALANCE);

        assertTrue(result.isPresent());
        assertEquals(ShareType.BALANCE, result.get().getShareType());
        assertTrue(result.get().isEnabled());
    }

    @Test
    void findByGroupIdAndUserIdAndShareType_WhenNotExists_ShouldReturnEmpty() {
        Optional<GroupSharePermission> result = groupSharePermissionRepository
                .findByGroupIdAndUserIdAndShareType(group.getId(), user.getId(), ShareType.BALANCE);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByGroupIdAndUserIdAndShareType_WhenDifferentType_ShouldReturnEmpty() {
        savePermission(group, user, ShareType.BALANCE, true);

        Optional<GroupSharePermission> result = groupSharePermissionRepository
                .findByGroupIdAndUserIdAndShareType(group.getId(), user.getId(), ShareType.INVESTMENTS);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // findEnabledUserIdsByGroupIdAndShareType
    // -------------------------------------------------------------------------

    @Test
    void findEnabledUserIdsByGroupIdAndShareType_WhenEnabled_ShouldReturnUserIds() {
        savePermission(group, user, ShareType.BALANCE, true);
        savePermission(group, otherUser, ShareType.BALANCE, true);

        List<Long> result = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(group.getId(), ShareType.BALANCE);

        assertEquals(2, result.size());
        assertTrue(result.contains(user.getId()));
        assertTrue(result.contains(otherUser.getId()));
    }

    @Test
    void findEnabledUserIdsByGroupIdAndShareType_WhenDisabled_ShouldNotReturn() {
        savePermission(group, user, ShareType.BALANCE, false);

        List<Long> result = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(group.getId(), ShareType.BALANCE);

        assertTrue(result.isEmpty());
    }

    @Test
    void findEnabledUserIdsByGroupIdAndShareType_WhenMixedEnabledAndDisabled_ShouldReturnOnlyEnabled() {
        savePermission(group, user, ShareType.BALANCE, true);
        savePermission(group, otherUser, ShareType.BALANCE, false);

        List<Long> result = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(group.getId(), ShareType.BALANCE);

        assertEquals(1, result.size());
        assertTrue(result.contains(user.getId()));
    }

    @Test
    void findEnabledUserIdsByGroupIdAndShareType_WhenDifferentShareType_ShouldNotReturn() {
        savePermission(group, user, ShareType.INVESTMENTS, true);

        List<Long> result = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(group.getId(), ShareType.BALANCE);

        assertTrue(result.isEmpty());
    }

    @Test
    void findEnabledUserIdsByGroupIdAndShareType_WhenNoPermissions_ShouldReturnEmpty() {
        List<Long> result = groupSharePermissionRepository
                .findEnabledUserIdsByGroupIdAndShareType(group.getId(), ShareType.BALANCE);

        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Group saveGroup(User responsible) {
        Group g = Group.builder()
                .name("Família Teste")
                .responsibleUser(responsible)
                .build();
        return groupRepository.save(g);
    }

    private GroupSharePermission savePermission(Group g, User u, ShareType type, boolean enabled) {
        GroupSharePermission permission = GroupSharePermission.builder()
                .group(g)
                .user(u)
                .shareType(type)
                .enabled(enabled)
                .build();
        return groupSharePermissionRepository.save(permission);
    }
}
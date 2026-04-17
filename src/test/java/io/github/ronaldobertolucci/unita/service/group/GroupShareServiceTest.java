package io.github.ronaldobertolucci.unita.service.group;

import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.model.card.CreditCard;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.group.GroupSharePermission;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupShareServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMembershipRepository groupMembershipRepository;
    @Mock
    private GroupSharePermissionRepository groupSharePermissionRepository;
    @Mock
    private PocketRepository pocketRepository;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private GroupShareService groupShareService;

    private User currentUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setFirstName("Maria");
        currentUser.setLastName("Souza");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setFirstName("João");
        otherUser.setLastName("Silva");

        when(authentication.getPrincipal()).thenReturn(currentUser);
    }

    // -------------------------------------------------------------------------
    // updatePermissions
    // -------------------------------------------------------------------------

    @Test
    void updatePermissions_WhenNewPermission_ShouldCreateAndReturn() {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findAllByGroupIdAndUserId(1L, 1L)).thenReturn(List.of());
        when(groupRepository.getReferenceById(1L)).thenReturn(buildGroup(1L));

        GroupSharePermission saved = buildPermission(ShareType.BALANCE, true);
        when(groupSharePermissionRepository.saveAll(any())).thenReturn(List.of(saved));

        List<GroupSharePermissionDto> result = groupShareService.updatePermissions(1L, dto, authentication);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ShareType.BALANCE, result.get(0).shareType());
        assertTrue(result.get(0).enabled());
        verify(groupSharePermissionRepository).saveAll(any());
    }

    @Test
    void updatePermissions_WhenExistingPermission_ShouldUpdateAndReturn() {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, false)));

        GroupSharePermission existing = buildPermission(ShareType.BALANCE, true);
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupSharePermissionRepository.findAllByGroupIdAndUserId(1L, 1L)).thenReturn(List.of(existing));
        when(groupSharePermissionRepository.saveAll(any())).thenReturn(List.of(buildPermission(ShareType.BALANCE, false)));

        List<GroupSharePermissionDto> result = groupShareService.updatePermissions(1L, dto, authentication);

        assertFalse(result.get(0).enabled());
    }

    @Test
    void updatePermissions_WhenGroupNotFound_ShouldThrow() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        assertThrows(EntityNotFoundException.class,
                () -> groupShareService.updatePermissions(99L, dto, authentication));
        verify(groupSharePermissionRepository, never()).saveAll(any());
    }

    @Test
    void updatePermissions_WhenNotMember_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.updatePermissions(1L, dto, authentication));
        verify(groupSharePermissionRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // getPockets
    // -------------------------------------------------------------------------

    @Test
    void getPockets_WhenMembersHavePockets_ShouldReturnAll() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupMembershipRepository.findByGroupIdWithUsers(1L))
                .thenReturn(List.of(buildMembership(currentUser), buildMembership(otherUser)));

        Cash cash1 = buildCash(10L, currentUser);
        Cash cash2 = buildCash(20L, otherUser);

        when(pocketRepository.findAllByUserId(1L)).thenReturn(List.of(cash1));
        when(pocketRepository.findAllByUserId(2L)).thenReturn(List.of(cash2));

        List<GroupMemberPocketDto> result = groupShareService.getPockets(1L, authentication);

        assertEquals(2, result.size());

        GroupMemberPocketDto first = result.get(0);
        assertEquals(10L, first.id());
        assertEquals("Cash", first.type());
        assertEquals("Dinheiro em espécie", first.label());
        assertEquals(1L, first.user().id());
        assertEquals("Maria", first.user().firstName());
        assertEquals("Souza", first.user().lastName());

        GroupMemberPocketDto second = result.get(1);
        assertEquals(20L, second.id());
        assertEquals("Cash", second.type());
        assertEquals("Dinheiro em espécie", second.label());
        assertEquals(2L, second.user().id());
        assertEquals("João", second.user().firstName());
        assertEquals("Silva", second.user().lastName());
    }

    @Test
    void getPockets_WhenMembersHaveNoPockets_ShouldReturnEmptyList() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(true);
        when(groupMembershipRepository.findByGroupIdWithUsers(1L))
                .thenReturn(List.of(buildMembership(currentUser)));
        when(pocketRepository.findAllByUserId(1L)).thenReturn(List.of());

        List<GroupMemberPocketDto> result = groupShareService.getPockets(1L, authentication);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPockets_WhenGroupNotFound_ShouldThrow() {
        when(groupRepository.existsById(99L)).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> groupShareService.getPockets(99L, authentication));
    }

    @Test
    void getPockets_WhenNotMember_ShouldThrow() {
        when(groupRepository.existsById(1L)).thenReturn(true);
        when(groupMembershipRepository.existsByUserIdAndGroupId(1L, 1L)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> groupShareService.getPockets(1L, authentication));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private Group buildGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private GroupMembership buildMembership(User user) {
        GroupMembership gm = new GroupMembership();
        gm.setUser(user);
        gm.setGroup(buildGroup(1L));
        return gm;
    }

    private GroupSharePermission buildPermission(ShareType type, boolean enabled) {
        return GroupSharePermission.builder()
                .group(buildGroup(1L))
                .user(currentUser)
                .shareType(type)
                .enabled(enabled)
                .build();
    }

    private Cash buildCash(Long id, User user) {
        Cash cash = new Cash();
        cash.setId(id);
        cash.setUser(user);
        return cash;
    }
}
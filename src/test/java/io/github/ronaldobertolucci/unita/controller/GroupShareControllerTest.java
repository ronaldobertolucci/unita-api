package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.group.*;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import io.github.ronaldobertolucci.unita.model.group.ShareType;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.group.GroupShareService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GroupShareController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class GroupShareControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private GroupShareService groupShareService;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private GroupSharePermissionDto permissionDto(ShareType type, boolean enabled) {
        return new GroupSharePermissionDto(type, enabled);
    }

    // -------------------------------------------------------------------------
    // getPermissions
    // -------------------------------------------------------------------------

    @Test
    void getPermissions_WhenDataIsValid_ShouldReturn200() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));
        when(groupShareService.getPermissions(eq(1L), any()))
                .thenReturn(List.of(permissionDto(ShareType.BALANCE, true)));

        mockMvc.perform(get("/groups/1/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shareType").value("BALANCE"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void getPermissions_WhenUnauthenticated_ShouldReturn403() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        mockMvc.perform(get("/groups/1/share/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPermissions_WhenNotMember_ShouldReturn400() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));
        when(groupShareService.getPermissions(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("User is not a member of this group"));

        mockMvc.perform(get("/groups/1/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPermissions_WhenGroupNotFound_ShouldReturn404() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));
        when(groupShareService.getPermissions(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Group not found with id: 99"));

        mockMvc.perform(get("/groups/99/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // updatePermissions
    // -------------------------------------------------------------------------

    @Test
    void updatePermissions_WhenDataIsValid_ShouldReturn200() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));
        when(groupShareService.updatePermissions(eq(1L), any(), any()))
                .thenReturn(List.of(permissionDto(ShareType.BALANCE, true)));

        mockMvc.perform(put("/groups/1/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].shareType").value("BALANCE"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void updatePermissions_WhenUnauthenticated_ShouldReturn403() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));

        mockMvc.perform(put("/groups/1/share/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatePermissions_WhenRequiredFieldsAreMissing_ShouldReturn400() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(null);

        mockMvc.perform(put("/groups/1/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePermissions_WhenNotMember_ShouldReturn400() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));
        when(groupShareService.updatePermissions(eq(1L), any(), any()))
                .thenThrow(new IllegalArgumentException("User is not a member of this group"));

        mockMvc.perform(put("/groups/1/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePermissions_WhenGroupNotFound_ShouldReturn404() throws Exception {
        GroupSharePermissionsUpdateDto dto = new GroupSharePermissionsUpdateDto(
                List.of(new GroupSharePermissionUpdateItemDto(ShareType.BALANCE, true)));
        when(groupShareService.updatePermissions(eq(99L), any(), any()))
                .thenThrow(new EntityNotFoundException("Group not found with id: 99"));

        mockMvc.perform(put("/groups/99/share/permissions")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // getPockets
    // -------------------------------------------------------------------------

    @Test
    void getPockets_ShouldReturn200WithList() throws Exception {
        when(groupShareService.getPockets(eq(1L), any())).thenReturn(List.of(memberPocketDto()));

        mockMvc.perform(get("/groups/1/share/pockets")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].type").value("Cash"))
                .andExpect(jsonPath("$[0].label").value("Carteira"))
                .andExpect(jsonPath("$[0].user.id").value(2))
                .andExpect(jsonPath("$[0].user.firstName").value("João"))
                .andExpect(jsonPath("$[0].user.lastName").value("Silva"))
                .andExpect(jsonPath("$[0].user.email").value("joao@example.com"));
    }

    @Test
    void getPockets_WhenNoneFound_ShouldReturn200WithEmptyList() throws Exception {
        when(groupShareService.getPockets(eq(1L), any())).thenReturn(List.of());

        mockMvc.perform(get("/groups/1/share/pockets")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getPockets_WhenUnauthenticated_ShouldReturn403() throws Exception {
        mockMvc.perform(get("/groups/1/share/pockets"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPockets_WhenNotMember_ShouldReturn400() throws Exception {
        when(groupShareService.getPockets(eq(1L), any()))
                .thenThrow(new IllegalArgumentException("User is not a member of this group"));

        mockMvc.perform(get("/groups/1/share/pockets")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPockets_WhenGroupNotFound_ShouldReturn404() throws Exception {
        when(groupShareService.getPockets(eq(99L), any()))
                .thenThrow(new EntityNotFoundException("Group not found"));

        mockMvc.perform(get("/groups/99/share/pockets")
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isNotFound());
    }

    private GroupMemberPocketDto memberPocketDto() {
        GroupMemberPocketUserDto user = new GroupMemberPocketUserDto(2L, "João", "Silva", "joao@example.com");
        return new GroupMemberPocketDto(10L, "Cash", "Carteira", user);
    }
}
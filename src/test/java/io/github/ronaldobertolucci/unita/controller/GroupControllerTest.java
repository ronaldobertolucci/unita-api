package io.github.ronaldobertolucci.unita.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ronaldobertolucci.unita.config.TestConfig;
import io.github.ronaldobertolucci.unita.config.security.SecurityConfigurations;
import io.github.ronaldobertolucci.unita.dto.group.GroupCreateDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupMembershipDto;
import io.github.ronaldobertolucci.unita.dto.group.GroupUpdateResponsibleDto;
import io.github.ronaldobertolucci.unita.model.group.Group;
import io.github.ronaldobertolucci.unita.model.group.GroupMembership;
import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.group.GroupService;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GroupController.class)
@Import({TestConfig.class, SecurityConfigurations.class})
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private GroupService groupService;

    private User testUser;
    private Group testGroup;
    private GroupDto groupDto;

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

        testGroup = Group.builder()
                .id(1L)
                .name("Family")
                .responsibleUser(testUser)
                .build();

        groupDto = new GroupDto(testGroup);
    }

    @Test
    void createGroup_WhenDataIsValid_ShouldCreateGroup() throws Exception {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("Family");
        when(groupService.createGroup(any(GroupCreateDto.class), any()))
                .thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Family"))
                .andExpect(jsonPath("$.id").value(1));

        verify(groupService, times(1)).createGroup(any(GroupCreateDto.class), any());
    }

    @Test
    void createGroup_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("Family");
        when(groupService.createGroup(any(GroupCreateDto.class), any()))
                .thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void createGroup_WhenNameIsBlank_ShouldReturnBadRequest() throws Exception {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("");

        // Act & Assert
        mockMvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isBadRequest());

        verify(groupService, never()).createGroup(any(), any());
    }

    @Test
    void getMyGroups_ShouldReturnList() throws Exception {
        // Arrange
        when(groupService.getMyGroups(any())).thenReturn(List.of(groupDto));

        // Act & Assert
        mockMvc.perform(get("/groups/my").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Family"));

        verify(groupService, times(1)).getMyGroups(any());
    }

    @Test
    void getMyGroups_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        when(groupService.getMyGroups(any())).thenReturn(List.of(groupDto));

        // Act & Assert
        mockMvc.perform(get("/groups/my"))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupsWhereIAmResponsible_ShouldReturnList() throws Exception {
        // Arrange
        when(groupService.getGroupsWhereIAmResponsible(any())).thenReturn(List.of(groupDto));

        // Act & Assert
        mockMvc.perform(get("/groups/my/responsible").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].name").value("Family"));

        verify(groupService, times(1)).getGroupsWhereIAmResponsible(any());
    }

    @Test
    void getGroupsWhereIAmResponsible_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        when(groupService.getGroupsWhereIAmResponsible(any())).thenReturn(List.of(groupDto));

        // Act & Assert
        mockMvc.perform(get("/groups/my/responsible"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupById_WhenGroupExists_ShouldReturnGroup() throws Exception {
        // Arrange
        when(groupService.getGroupById(eq(1L), any())).thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(get("/groups/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Family"));

        verify(groupService, times(1)).getGroupById(eq(1L), any());
    }

    @Test
    void getGroupById_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        when(groupService.getGroupById(eq(1L), any())).thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(get("/groups/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateGroupName_WhenDataIsValid_ShouldUpdateGroup() throws Exception {
        // Arrange
        GroupCreateDto dto = new GroupCreateDto("New Family");
        when(groupService.updateGroupName(eq(1L), any(GroupCreateDto.class), any()))
                .thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(put("/groups/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(groupService, times(1)).updateGroupName(eq(1L), any(GroupCreateDto.class), any());
    }

    @Test
    void transferResponsibility_WhenDataIsValid_ShouldTransfer() throws Exception {
        // Arrange
        GroupUpdateResponsibleDto dto = new GroupUpdateResponsibleDto(2L);
        when(groupService.transferResponsibility(eq(1L), any(GroupUpdateResponsibleDto.class), any()))
                .thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(put("/groups/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(groupService, times(1)).transferResponsibility(eq(1L), any(GroupUpdateResponsibleDto.class), any());
    }

    @Test
    void transferResponsibility_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        GroupUpdateResponsibleDto dto = new GroupUpdateResponsibleDto(2L);
        when(groupService.transferResponsibility(eq(1L), any(GroupUpdateResponsibleDto.class), any()))
                .thenReturn(groupDto);

        // Act & Assert
        mockMvc.perform(put("/groups/1/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteGroup_ShouldDeleteGroup() throws Exception {
        // Arrange
        doNothing().when(groupService).deleteGroup(eq(1L), any());

        // Act & Assert
        mockMvc.perform(delete("/groups/1").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Group deleted successfully"));

        verify(groupService, times(1)).deleteGroup(eq(1L), any());
    }

    @Test
    void leaveGroup_ShouldLeaveGroup() throws Exception {
        // Arrange
        doNothing().when(groupService).leaveGroup(eq(1L), any());

        // Act & Assert
        mockMvc.perform(delete("/groups/1/leave").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("You have left the group"));

        verify(groupService, times(1)).leaveGroup(eq(1L), any());
    }

    @Test
    void leaveGroup_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        doNothing().when(groupService).leaveGroup(eq(1L), any());

        // Act & Assert
        mockMvc.perform(delete("/groups/1/leave"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupMembers_ShouldReturnMembers() throws Exception {
        // Arrange
        GroupMembership membership = GroupMembership.builder()
                .id(1L)
                .user(testUser)
                .group(testGroup)
                .build();

        GroupMembershipDto membershipDto = new GroupMembershipDto(membership);

        when(groupService.getGroupMembers(eq(1L), any()))
                .thenReturn(List.of(membershipDto));

        // Act & Assert
        mockMvc.perform(get("/groups/1/members").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].user.email").value("john@example.com"));

        verify(groupService, times(1)).getGroupMembers(eq(1L), any());
    }

    @Test
    void getGroupMembers_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        GroupMembership membership = GroupMembership.builder()
                .id(1L)
                .user(testUser)
                .group(testGroup)
                .build();

        GroupMembershipDto membershipDto = new GroupMembershipDto(membership);

        when(groupService.getGroupMembers(eq(1L), any()))
                .thenReturn(List.of(membershipDto));

        // Act & Assert
        mockMvc.perform(get("/groups/1/members"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupMemberCount_ShouldReturnCount() throws Exception {
        // Arrange
        when(groupService.getGroupMemberCount(eq(1L), any())).thenReturn(5L);

        // Act & Assert
        mockMvc.perform(get("/groups/1/members/count").with(user("test").authorities(List.of(new SimpleGrantedAuthority("USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(5));

        verify(groupService, times(1)).getGroupMemberCount(eq(1L), any());
    }

    @Test
    void getGroupMemberCount_WhenUnauthenticated_ShouldReturn403() throws Exception {
        // Arrange
        when(groupService.getGroupMemberCount(eq(1L), any())).thenReturn(5L);

        // Act & Assert
        mockMvc.perform(get("/groups/1/members/count"))
                .andExpect(status().isForbidden());
    }
}
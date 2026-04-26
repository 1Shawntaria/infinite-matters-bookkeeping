package com.infinitematters.bookkeeping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.notifications.Notification;
import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationDeliveryState;
import com.infinitematters.bookkeeping.notifications.NotificationDispatchService;
import com.infinitematters.bookkeeping.notifications.NotificationRepository;
import com.infinitematters.bookkeeping.notifications.NotificationStatus;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.workflows.WorkflowTask;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskPriority;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskRepository;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus;
import com.infinitematters.bookkeeping.workflows.WorkflowTaskType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InfiniteMattersApplicationTests {
    private static final String ORG_HEADER = "X-Organization-Id";
    private static final String OWNER_EMAIL = "owner@acme.test";
    private static final String OWNER_PASSWORD = "password123";
    private static final String MEMBER_EMAIL = "member@acme.test";
    private static final String MEMBER_PASSWORD = "password456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private WorkflowTaskRepository workflowTaskRepository;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditService auditService;

    @Test
    void browserCookieSessionsAuthenticateAndEnforceTenantBoundary() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "cookie-owner-" + suffix + "@example.test";
        String otherEmail = "cookie-other-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Cookie Owner", password);
        String ownerOrganizationId = createOrganization(ownerUserId);
        String otherUserId = createUser(otherEmail, "Cookie Other", password);
        String otherOrganizationId = createOrganization(otherUserId);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(ownerEmail, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        Cookie accessCookie = loginResult.getResponse().getCookie("im_access_token");
        Cookie refreshCookie = loginResult.getResponse().getCookie("im_refresh_token");
        Cookie csrfCookie = loginResult.getResponse().getCookie("im_csrf_token");
        assertThat(accessCookie).isNotNull();
        assertThat(accessCookie.isHttpOnly()).isTrue();
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();

        mockMvc.perform(get("/api/auth/me")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ownerEmail));

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .cookie(accessCookie)
                        .header(ORG_HEADER, otherOrganizationId)
                        .param("organizationId", otherOrganizationId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User does not have access to organization " + otherOrganizationId));

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .cookie(accessCookie)
                        .header(ORG_HEADER, ownerOrganizationId)
                        .param("organizationId", ownerOrganizationId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts")
                        .cookie(accessCookie)
                        .header(ORG_HEADER, ownerOrganizationId)
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "name": "Cookie Missing Csrf Checking",
                                  "accountType": "BANK",
                                  "institutionName": "Infinite Matters Bank",
                                  "currency": "USD"
                                }
                                """.formatted(ownerOrganizationId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));

        AuthTokens ownerBearerTokens = issueToken(ownerEmail, password);
        mockMvc.perform(post("/api/accounts")
                        .cookie(accessCookie, refreshCookie)
                        .header(ORG_HEADER, ownerOrganizationId)
                        .header("Authorization", bearerToken(ownerBearerTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "name": "Bearer With Stale Cookies Checking",
                                  "accountType": "BANK",
                                  "institutionName": "Infinite Matters Bank",
                                  "currency": "USD"
                                }
                                """.formatted(ownerOrganizationId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users")
                        .cookie(accessCookie, refreshCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Public Signup With Stale Cookies",
                                  "password": "%s"
                                }
                                """.formatted("stale-cookie-signup-" + suffix + "@example.test", password)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("CSRF token is missing or invalid"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue())
                        .header("Referer", "https://evil.example.test/accounting"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Request origin is not allowed"));

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(refreshCookie, csrfCookie)
                        .header("X-CSRF-Token", csrfCookie.getValue())
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        Cookie rotatedRefreshCookie = refreshResult.getResponse().getCookie("im_refresh_token");
        Cookie rotatedCsrfCookie = refreshResult.getResponse().getCookie("im_csrf_token");
        assertThat(rotatedRefreshCookie).isNotNull();
        assertThat(rotatedRefreshCookie.isHttpOnly()).isTrue();
        assertThat(rotatedCsrfCookie).isNotNull();
        assertThat(rotatedCsrfCookie.isHttpOnly()).isFalse();

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(rotatedRefreshCookie, rotatedCsrfCookie)
                        .header("X-CSRF-Token", rotatedCsrfCookie.getValue())
                        .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(logoutResult.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(header -> header.contains("im_access_token=") && header.contains("Max-Age=0"))
                .anyMatch(header -> header.contains("im_refresh_token=") && header.contains("Max-Age=0"))
                .anyMatch(header -> header.contains("im_csrf_token=") && header.contains("Max-Age=0"));
    }

    @Test
    void listsOnlyOrganizationsForAuthenticatedUserMemberships() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "workspace-owner-" + suffix + "@example.test";
        String memberEmail = "workspace-member-" + suffix + "@example.test";
        String otherOwnerEmail = "workspace-other-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Workspace Owner", password);
        String memberUserId = createUser(memberEmail, "Workspace Member", password);
        String otherOwnerUserId = createUser(otherOwnerEmail, "Other Workspace Owner", password);

        AuthTokens ownerTokens = issueToken(ownerEmail, password);
        AuthTokens memberTokens = issueToken(memberEmail, password);
        String primaryOrganizationId = createOrganization("Primary Workspace", ownerUserId);
        String secondaryOrganizationId = createOrganization("Secondary Workspace", ownerUserId);
        String otherOrganizationId = createOrganization("Other Workspace", otherOwnerUserId);
        addMembership(secondaryOrganizationId, memberUserId, ownerTokens.accessToken());

        mockMvc.perform(get("/api/users/organizations")
                        .header("Authorization", bearerToken(ownerTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id", hasItems(primaryOrganizationId, secondaryOrganizationId)))
                .andExpect(jsonPath("$[?(@.id=='%s')].invitationTtlDays".formatted(primaryOrganizationId), hasItem(7)))
                .andExpect(jsonPath("$[?(@.id=='%s')].role".formatted(primaryOrganizationId), hasItem("OWNER")))
                .andExpect(jsonPath("$[?(@.id=='%s')].role".formatted(secondaryOrganizationId), hasItem("OWNER")));

        mockMvc.perform(get("/api/users/organizations")
                        .header("Authorization", bearerToken(memberTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(secondaryOrganizationId))
                .andExpect(jsonPath("$[0].role").value("MEMBER"));

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .header(ORG_HEADER, primaryOrganizationId)
                        .header("Authorization", bearerToken(memberTokens.accessToken()))
                        .param("organizationId", primaryOrganizationId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User does not have access to organization " + primaryOrganizationId));

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .header(ORG_HEADER, otherOrganizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", otherOrganizationId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("User does not have access to organization " + otherOrganizationId));
    }

    @Test
    void ownerCanListAndUpdateOrganizationMemberships() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "membership-owner-" + suffix + "@example.test";
        String adminEmail = "membership-admin-" + suffix + "@example.test";
        String memberEmail = "membership-member-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Membership Owner", password);
        String adminUserId = createUser(adminEmail, "Membership Admin", password);
        String memberUserId = createUser(memberEmail, "Membership Member", password);
        String organizationId = createOrganization("Membership Workspace", ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);
        addMembership(organizationId, adminUserId, ownerTokens.accessToken());

        mockMvc.perform(post("/api/users/memberships/by-email")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "email": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(organizationId, memberEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        MvcResult membershipsResult = mockMvc.perform(get("/api/users/memberships")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].user.email", hasItems(ownerEmail, adminEmail, memberEmail)))
                .andReturn();

        JsonNode memberships = objectMapper.readTree(membershipsResult.getResponse().getContentAsString());
        String memberMembershipId = null;
        for (JsonNode membership : memberships) {
            if (memberEmail.equals(membership.path("user").path("email").asText())) {
                memberMembershipId = membership.path("id").asText();
                break;
            }
        }
        assertThat(memberMembershipId).isNotNull();

        mockMvc.perform(patch("/api/users/memberships/{membershipId}", memberMembershipId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.email").value(memberEmail));

        AuthTokens memberTokens = issueToken(memberEmail, password);
        mockMvc.perform(get("/api/users/memberships")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].user.email", hasItems(ownerEmail, adminEmail, memberEmail)));

        String adminMembershipId = null;
        for (JsonNode membership : memberships) {
            if (adminEmail.equals(membership.path("user").path("email").asText())) {
                adminMembershipId = membership.path("id").asText();
                break;
            }
        }
        assertThat(adminMembershipId).isNotNull();

        mockMvc.perform(delete("/api/users/memberships/{membershipId}", adminMembershipId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/memberships")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].user.email", hasItems(ownerEmail, memberEmail)));
    }

    @Test
    void lastOwnerCannotBeRemovedFromOrganization() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "last-owner-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Last Owner", password);
        String organizationId = createOrganization("Last Owner Workspace", ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);

        MvcResult membershipsResult = mockMvc.perform(get("/api/users/memberships")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        String ownerMembershipId = objectMapper.readTree(membershipsResult.getResponse().getContentAsString())
                .get(0)
                .path("id")
                .asText();

        mockMvc.perform(delete("/api/users/memberships/{membershipId}", ownerMembershipId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The last owner cannot be removed from the workspace"));
    }

    @Test
    void ownerCanCreateListAndRevokeInvitations() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "invite-owner-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Invite Owner", password);
        String organizationId = createOrganization("Invitation Workspace", ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);

        MvcResult invitationResult = mockMvc.perform(post("/api/users/invitations")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "email": "invitee-%s@example.test",
                                  "role": "MEMBER"
                                }
                                """.formatted(organizationId, suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inviteUrl").isNotEmpty())
                .andExpect(jsonPath("$.delivery.status").value("PENDING"))
                .andExpect(jsonPath("$.delivery.channel").value("EMAIL"))
                .andReturn();

        String invitationId = objectMapper.readTree(invitationResult.getResponse().getContentAsString())
                .path("id")
                .asText();

        mockMvc.perform(get("/api/users/invitations")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].delivery.status").value("PENDING"));

        MvcResult resentInvitation = mockMvc.perform(post("/api/users/invitations/{invitationId}/resend", invitationId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.inviteUrl").isNotEmpty())
                .andExpect(jsonPath("$.delivery.status").value("PENDING"))
                .andReturn();

        String resentInviteUrl = objectMapper.readTree(resentInvitation.getResponse().getContentAsString())
                .path("inviteUrl")
                .asText();

        org.assertj.core.api.Assertions.assertThat(resentInviteUrl).isNotBlank();

        mockMvc.perform(delete("/api/users/invitations/{invitationId}", invitationId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void invitationCanCreateAccountAndAcceptWorkspace() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "accept-owner-" + suffix + "@example.test";
        String invitedEmail = "accept-invitee-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Accept Owner", password);
        String organizationId = createOrganization("Accepted Workspace", ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);

        MvcResult invitationResult = mockMvc.perform(post("/api/users/invitations")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "email": "%s",
                                  "role": "ADMIN"
                                }
                                """.formatted(organizationId, invitedEmail)))
                .andExpect(status().isOk())
                .andReturn();

        String inviteUrl = objectMapper.readTree(invitationResult.getResponse().getContentAsString())
                .path("inviteUrl")
                .asText();
        String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/auth/invitations/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(invitedEmail))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/auth/invitations/{token}/accept", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Invited Admin",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(invitedEmail));

        AuthTokens invitedTokens = issueToken(invitedEmail, password);
        mockMvc.perform(get("/api/users/organizations")
                        .header("Authorization", bearerToken(invitedTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(organizationId))
                .andExpect(jsonPath("$[0].invitationTtlDays").value(7))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void ownerCanUpdateWorkspaceSettings() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "settings-owner-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Settings Owner", password);
        String organizationId = createOrganization("Settings Workspace", ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);

        mockMvc.perform(get("/api/organizations/settings")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Settings Workspace"))
                .andExpect(jsonPath("$.timezone").value("America/Los_Angeles"))
                .andExpect(jsonPath("$.invitationTtlDays").value(7));

        mockMvc.perform(patch("/api/organizations/settings")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Settings Workspace East",
                                  "timezone": "America/New_York",
                                  "invitationTtlDays": 14
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Settings Workspace East"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.invitationTtlDays").value(14));

        mockMvc.perform(get("/api/organizations/settings")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Settings Workspace East"))
                .andExpect(jsonPath("$.timezone").value("America/New_York"))
                .andExpect(jsonPath("$.invitationTtlDays").value(14));
    }

    @Test
    void workspaceSettingsRejectInvalidTimezone() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "settings-invalid-timezone-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Settings Owner", password);
        String organizationId = createOrganization("Settings Workspace", ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);

        mockMvc.perform(patch("/api/organizations/settings")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "timezone": "Mars/Phobos"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Timezone must be a valid IANA zone ID"));
    }

    @Test
    void invalidOrganizationHeaderReturnsBadRequest() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "invalid-org-header-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Invalid Header Owner", password);
        String organizationId = createOrganization(ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .header(ORG_HEADER, "not-a-uuid")
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid X-Organization-Id header"))
                .andExpect(jsonPath("$.path").value("/api/dashboard/snapshot"));
    }

    @Test
    void returnsRealReconciliationAccountDetail() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "reconciliation-detail-owner-" + suffix + "@example.test";
        String password = "password123";

        String ownerUserId = createUser(ownerEmail, "Reconciliation Detail Owner", password);
        String organizationId = createOrganization(ownerUserId);
        AuthTokens ownerTokens = issueToken(ownerEmail, password);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MvcResult reconciliationStart = mockMvc.perform(post("/api/reconciliations")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month":"2026-03",
                                  "financialAccountId":"%s",
                                  "openingBalance": 1000.00,
                                  "statementEndingBalance": 1200.00
                                }
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andReturn();

        String reconciliationId = objectMapper.readTree(reconciliationStart.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/reconciliations/accounts/{accountId}", accountId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusMonth").value("2026-03"))
                .andExpect(jsonPath("$.financialAccountId").value(accountId))
                .andExpect(jsonPath("$.accountName").value("Operating Checking"))
                .andExpect(jsonPath("$.session.id").value(reconciliationId))
                .andExpect(jsonPath("$.session.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.bookEndingBalance").value(1000.0))
                .andExpect(jsonPath("$.varianceAmount").value(200.0))
                .andExpect(jsonPath("$.canStartReconciliation").value(false))
                .andExpect(jsonPath("$.canCompleteReconciliation").value(true))
                .andExpect(jsonPath("$.transactions").isArray());
    }

    @Test
    void importsTransactionsAndRoutesAmbiguousItemsIntoReview() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.summary")
                        .value("Get the versioned home dashboard contract"))
                .andExpect(jsonPath("$.paths['/api/dashboard/home/versions'].get.summary")
                        .value("Get supported dashboard home contract versions"))
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.description")
                        .value(org.hamcrest.Matchers.containsString("dashboard contract of record")))
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.parameters[*].name", hasItem("version")))
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['200'].headers['X-Dashboard-Home-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['200'].headers['X-Dashboard-Home-Requested-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['200'].headers['X-Dashboard-Home-Version-Source']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['200'].headers['X-Dashboard-Home-Recommended-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['200'].headers['X-Dashboard-Home-Latest-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['200'].headers['Deprecation']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home'].get.responses['400'].description")
                        .value(org.hamcrest.Matchers.containsString("headers are not emitted")))
                .andExpect(jsonPath("$.paths['/api/dashboard/home/versions'].get.responses['200'].headers['X-Dashboard-Home-Default-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home/versions'].get.responses['200'].headers['X-Dashboard-Home-Recommended-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home/versions'].get.responses['200'].headers['X-Dashboard-Home-Latest-Version']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home/versions'].get.responses['200'].headers['X-Dashboard-Home-Supported-Versions']").exists())
                .andExpect(jsonPath("$.paths['/api/dashboard/home/versions'].get.description")
                        .value(org.hamcrest.Matchers.containsString("version lifecycle headers")));

        mockMvc.perform(get("/api/dashboard/home/versions"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Dashboard-Home-Default-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Recommended-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Latest-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Supported-Versions", "v1"))
                .andExpect(jsonPath("$.defaultVersion").value("v1"))
                .andExpect(jsonPath("$.recommendedVersion").value("v1"))
                .andExpect(jsonPath("$.latestVersion").value("v1"))
                .andExpect(jsonPath("$.negotiationPolicy")
                        .value("If the client omits a version, the server returns the default version. If the client requests an unsupported version, the server returns 400 Bad Request."))
                .andExpect(jsonPath("$.headerPolicy")
                        .value("X-Dashboard-Home-Default-Version is the server default, X-Dashboard-Home-Recommended-Version is the preferred client target, X-Dashboard-Home-Latest-Version is the newest available contract, and X-Dashboard-Home-Supported-Versions lists all supported versions."))
                .andExpect(jsonPath("$.supportedVersions[0]").value("v1"))
                .andExpect(jsonPath("$.versions[0].version").value("v1"))
                .andExpect(jsonPath("$.versions[0].defaultVersion").value(true))
                .andExpect(jsonPath("$.versions[0].notes").value("Current stable home dashboard contract."))
                .andExpect(jsonPath("$.versions[0].intendedUse").value("Recommended for all current clients."))
                .andExpect(jsonPath("$.versions[0].deprecated").value(false))
                .andExpect(jsonPath("$.versions[0].deprecationDate").doesNotExist())
                .andExpect(jsonPath("$.versions[0].sunsetDate").doesNotExist());

        MvcResult duplicateSeedUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Duplicate Owner",
                                  "password": "%s"
                                }
                                """.formatted(OWNER_EMAIL, OWNER_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Duplicate Owner",
                                  "password": "%s"
                                }
                                """.formatted(OWNER_EMAIL, OWNER_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("A user with email '" + OWNER_EMAIL + "' already exists"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.requestId").isString());

        String userId = objectMapper.readTree(duplicateSeedUserResult.getResponse().getContentAsString()).get("id").asText();
        String memberUserId = createMemberUser();
        String organizationId = createOrganization(userId);
        AuthTokens ownerTokens = issueToken(OWNER_EMAIL, OWNER_PASSWORD);
        AuthTokens memberTokens = issueToken(MEMBER_EMAIL, MEMBER_PASSWORD);
        String replayedOwnerRefreshToken = ownerTokens.refreshToken();
        String ownerToken = ownerTokens.accessToken();
        String memberToken = memberTokens.accessToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL));

        AuthTokens rotatedOwnerTokens = refreshToken(ownerTokens.refreshToken());
        ownerToken = rotatedOwnerTokens.accessToken();
        ownerTokens = rotatedOwnerTokens;

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL));

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(rotatedOwnerTokens.refreshSessionId().toString()))
                .andExpect(jsonPath("$[0].active").value(true));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(replayedOwnerRefreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected; active session chain revoked"));

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(rotatedOwnerTokens.refreshSessionId().toString()))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].revokedReason").value("REUSE_CHAIN_REVOKED"));

        mockMvc.perform(get("/api/auth/notifications")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("AUTH_SECURITY"))
                .andExpect(jsonPath("$[0].referenceType").value("auth_session"));

        mockMvc.perform(get("/api/auth/activity")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("AUTH_REFRESH_TOKEN_REUSE_DETECTED"));

        ownerTokens = issueToken(OWNER_EMAIL, OWNER_PASSWORD);
        ownerToken = ownerTokens.accessToken();

        addMembership(organizationId, memberUserId, ownerToken);
        String accountId = createAccount(organizationId, ownerToken);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,2026-03-15,STARBUCKS,coffee with client,18.45,5814
                txn-2,2026-03-16,Unknown Vendor,monthly seat fee,49.99,5734
                """.getBytes());

        MvcResult importResult = mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(2))
                .andExpect(jsonPath("$.duplicateCount").value(0))
                .andExpect(jsonPath("$.reviewRequiredCount").value(1))
                .andExpect(jsonPath("$.postedCount").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.transactions[0].status").value("POSTED"))
                .andExpect(jsonPath("$.transactions[1].status").value("REVIEW_REQUIRED"))
                .andReturn();

        JsonNode importedPayload = objectMapper.readTree(importResult.getResponse().getContentAsString());
        String reviewTransactionId = importedPayload.get("transactions").get(1).get("transactionId").asText();

        MvcResult reviewResult = mockMvc.perform(get("/api/reviews/tasks")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(reviewTransactionId))
                .andExpect(jsonPath("$[0].proposedCategory").value("OTHER"))
                .andReturn();

        JsonNode reviewPayload = objectMapper.readTree(reviewResult.getResponse().getContentAsString());
        String taskId = reviewPayload.get(0).get("taskId").asText();

        mockMvc.perform(post("/api/reviews/tasks/{taskId}/resolve", taskId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finalCategory":"SOFTWARE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalCategory").value("SOFTWARE"))
                .andExpect(jsonPath("$.transactionStatus").value("POSTED"))
                .andExpect(jsonPath("$.taskStatus").value("COMPLETED"));

        mockMvc.perform(get("/api/transactions")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("POSTED"))
                .andExpect(jsonPath("$[0].finalCategory").value("SOFTWARE"))
                .andExpect(jsonPath("$[1].status").value("POSTED"));

        mockMvc.perform(get("/api/transactions/import-history")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].financialAccountId").value(accountId))
                .andExpect(jsonPath("$[0].financialAccountName").value("Operating Checking"))
                .andExpect(jsonPath("$[0].importedAt").isString())
                .andExpect(jsonPath("$[0].merchant").value("UNKNOWN VENDOR"))
                .andExpect(jsonPath("$[0].status").value("POSTED"))
                .andExpect(jsonPath("$[1].merchant").value("STARBUCKS"));

        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "transactions-2.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-3,%s,Unknown Vendor,renewal invoice,59.99,5734
                """.formatted(LocalDate.now().minusDays(29)).getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(secondFile)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewRequiredCount").value(0))
                .andExpect(jsonPath("$.postedCount").value(1))
                .andExpect(jsonPath("$.transactions[0].route").value("MEMORY"))
                .andExpect(jsonPath("$.transactions[0].finalCategory").value("SOFTWARE"))
                .andExpect(jsonPath("$.transactions[0].status").value("POSTED"));

        mockMvc.perform(get("/api/ledger/entries")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lines[0].entrySide").value("DEBIT"))
                .andExpect(jsonPath("$[0].lines[1].entrySide").value("CREDIT"))
                .andExpect(jsonPath("$[0].lines[0].accountName").value("Software and Subscriptions"))
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(get("/api/audit/events")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItems(
                        "TRANSACTION_IMPORTED",
                        "LEDGER_POSTED",
                        "REVIEW_RESOLVED")));

        mockMvc.perform(get("/api/audit/events")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.path").value("/api/audit/events"))
                .andExpect(jsonPath("$.requestId").isString());

        mockMvc.perform(get("/api/periods/checklist")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeReady").value(false));

        mockMvc.perform(post("/api/periods/close")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"month":"2026-03"}
                                """))
                .andExpect(status().isBadRequest());

        MvcResult reconciliationStart = mockMvc.perform(post("/api/reconciliations")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month":"2026-03",
                                  "financialAccountId":"%s",
                                  "openingBalance": 1000.00,
                                  "statementEndingBalance": 1200.00
                                }
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn();

        String reconciliationId = objectMapper.readTree(reconciliationStart.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/reconciliations/{sessionId}/complete", reconciliationId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.varianceAmount").value(71.57));

        mockMvc.perform(get("/api/periods/checklist")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeReady").value(false))
                .andExpect(jsonPath("$.items[0].complete").value(false))
                .andExpect(jsonPath("$.items[1].itemType").value("ACCOUNT_RECONCILIATION"));

        mockMvc.perform(post("/api/periods/close")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"month":"2026-03"}
                                """))
                .andExpect(status().isBadRequest());

        MvcResult exceptionTaskResult = mockMvc.perform(get("/api/reviews/tasks")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem(org.hamcrest.Matchers.containsString("Resolve reconciliation variance"))))
                .andReturn();

        String exceptionTaskId = objectMapper.readTree(exceptionTaskResult.getResponse().getContentAsString()).get(0).get("taskId").asText();

        mockMvc.perform(post("/api/reviews/tasks/{taskId}/assign", exceptionTaskId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assignedUserId":"%s"}
                                """.formatted(memberUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToUserId").value(memberUserId))
                .andExpect(jsonPath("$.assignedToUserName").value("Acme Member"));

        mockMvc.perform(get("/api/workflows/inbox")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(1))
                .andExpect(jsonPath("$.highPriorityCount").value(1))
                .andExpect(jsonPath("$.assignedToCurrentUserCount").value(1))
                .andExpect(jsonPath("$.attentionTasks[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.attentionTasks[0].assignedToUserId").value(memberUserId));

        mockMvc.perform(post("/api/workflows/reminders/run")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.notifications[0].workflowTaskId").value(exceptionTaskId))
                .andExpect(jsonPath("$.notifications[0].status").value("SENT"));

        mockMvc.perform(get("/api/workflows/notifications")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workflowTaskId").value(exceptionTaskId))
                .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("Resolve reconciliation variance")));

        mockMvc.perform(get("/api/workflows/notifications/attention")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/workflows/notifications/attention")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/workflows/notifications/requeue-failed")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeuedCount").value(0))
                .andExpect(jsonPath("$.notifications.length()").value(0));

        mockMvc.perform(post("/api/workflows/notifications/requeue-failed")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusMonth").value("2026-03"))
                .andExpect(jsonPath("$.cashBalance").value(128.43))
                .andExpect(jsonPath("$.postedTransactionCount").value(3))
                .andExpect(jsonPath("$.workflowInbox.highPriorityCount").value(1))
                .andExpect(jsonPath("$.workflowInbox.attentionTasks[0].taskType").value("RECONCILIATION_EXCEPTION"))
                .andExpect(jsonPath("$.period.closeReady").value(false))
                .andExpect(jsonPath("$.period.unreconciledAccountCount").value(1))
                .andExpect(jsonPath("$.notificationHealth.pendingCount").value(0))
                .andExpect(jsonPath("$.notificationHealth.failedCount").value(0))
                .andExpect(jsonPath("$.notificationHealth.retryingCount").value(0))
                .andExpect(jsonPath("$.notificationHealth.attentionNotifications.length()").value(0))
                .andExpect(jsonPath("$.expenseCategories[0].category").value("SOFTWARE"))
                .andExpect(jsonPath("$.expenseCategories[0].amount").value(109.98))
                .andExpect(jsonPath("$.expenseCategories[0].deltaFromPreviousMonth").value(109.98))
                .andExpect(jsonPath("$.expenseCategories[1].category").value("MEALS"))
                .andExpect(jsonPath("$.staleAccounts.length()").value(0))
                .andExpect(jsonPath("$.recentNotifications[0].workflowTaskId").value(exceptionTaskId));

        mockMvc.perform(get("/api/dashboard/home")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Dashboard-Home-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Requested-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Version-Source", "default"))
                .andExpect(header().string("X-Dashboard-Home-Default-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Recommended-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Latest-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Supported-Versions", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Deprecated", "false"))
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Sunset"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.contract.version").value("v1"))
                .andExpect(jsonPath("$.contract.requestedVersion").value("v1"))
                .andExpect(jsonPath("$.contract.versionSource").value("default"));

        mockMvc.perform(get("/api/dashboard/home")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .param("version", "v1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Dashboard-Home-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Requested-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Version-Source", "requested"))
                .andExpect(header().string("X-Dashboard-Home-Default-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Recommended-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Latest-Version", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Supported-Versions", "v1"))
                .andExpect(header().string("X-Dashboard-Home-Deprecated", "false"))
                .andExpect(header().doesNotExist("Deprecation"))
                .andExpect(header().doesNotExist("Sunset"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.contract.version").value("v1"))
                .andExpect(jsonPath("$.contract.requestedVersion").value("v1"))
                .andExpect(jsonPath("$.contract.versionSource").value("requested"))
                .andExpect(jsonPath("$.focusMonth").value("2026-03"))
                .andExpect(jsonPath("$.cashBalance").value(128.43))
                .andExpect(jsonPath("$.workflowInbox.cardId").value("workflow-inbox"))
                .andExpect(jsonPath("$.period.cardId").value("period-close"))
                .andExpect(jsonPath("$.supportPerformance.cardId").value("support-performance"))
                .andExpect(jsonPath("$.expenseCategories[0].itemId").value("expense-category-software"))
                .andExpect(jsonPath("$.staleAccounts.length()").value(0))
                .andExpect(jsonPath("$.recentNotifications[0].workflowTaskId").value(exceptionTaskId));

        mockMvc.perform(get("/api/dashboard/home")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .param("version", "v2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Unsupported dashboard home version 'v2'. Supported versions: v1."));

        mockMvc.perform(post("/api/reviews/tasks/{taskId}/resolve-exception", exceptionTaskId)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolutionComment":"Investigated statement timing difference; owner approved override"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionComment").value("Investigated statement timing difference; owner approved override"))
                .andExpect(jsonPath("$.resolvedByUserId").value(memberUserId));

        mockMvc.perform(get("/api/periods/checklist")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closeReady").value(false))
                .andExpect(jsonPath("$.items[0].complete").value(true))
                .andExpect(jsonPath("$.items[1].complete").value(false));

        mockMvc.perform(post("/api/periods/force-close")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month":"2026-03",
                                  "reason":"Materiality threshold not exceeded; variance documented and approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closeMethod").value("OVERRIDE"))
                .andExpect(jsonPath("$.overrideReason").value("Materiality threshold not exceeded; variance documented and approved"))
                .andExpect(jsonPath("$.overrideApprovedByUserId").value(userId));

        mockMvc.perform(get("/api/audit/events")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItems(
                        "RECONCILIATION_EXCEPTION_MANUALLY_RESOLVED",
                        "PERIOD_FORCE_CLOSED")));

        mockMvc.perform(get("/api/dashboard/snapshot")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowInbox.openCount").value(0))
                .andExpect(jsonPath("$.period.latestClosedStatus").value("CLOSED"))
                .andExpect(jsonPath("$.period.latestCloseMethod").value("OVERRIDE"))
                .andExpect(jsonPath("$.period.latestOverrideReason").value("Materiality threshold not exceeded; variance documented and approved"))
                .andExpect(jsonPath("$.notificationHealth.pendingCount").value(0))
                .andExpect(jsonPath("$.expenseCategories[0].category").value("SOFTWARE"))
                .andExpect(jsonPath("$.staleAccounts.length()").value(0));

        MockMultipartFile closedPeriodFile = new MockMultipartFile(
                "file",
                "transactions-closed.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-4,2026-03-22,Late Merchant,late import,25.00,5734
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(closedPeriodFile)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberToken))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/adjustments")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "entryDate": "2026-04-05",
                                  "description": "April software reclass",
                                  "adjustmentReason": "Correct prior month classification via current-period adjustment",
                                  "lines": [
                                    {
                                      "accountCode": "6120",
                                      "accountName": "Software and Subscriptions",
                                      "entrySide": "DEBIT",
                                      "amount": 59.99
                                    },
                                    {
                                      "accountCode": "6999",
                                      "accountName": "Other Business Expenses",
                                      "entrySide": "CREDIT",
                                      "amount": 59.99
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryType").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.adjustmentReason").value("Correct prior month classification via current-period adjustment"));

        logout(memberTokens.refreshToken());

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", bearerToken(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value(memberTokens.refreshSessionId().toString()))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].revokedReason").value("LOGOUT"));

        AuthTokens secondMemberSession = issueToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/revoke", secondMemberSession.refreshSessionId())
                        .header("Authorization", bearerToken(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "User removed trusted device"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(secondMemberSession.refreshSessionId().toString()))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.revokedReason").value("User removed trusted device"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(secondMemberSession.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));

        AuthTokens preResetActiveSession = issueToken(MEMBER_EMAIL, MEMBER_PASSWORD);

        MvcResult forgotPasswordResult = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s"
                                }
                                """.formatted(MEMBER_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET_TOKEN_ISSUED"))
                .andExpect(jsonPath("$.deliveryChannel").value("EMAIL"))
                .andExpect(jsonPath("$.resetToken").isString())
                .andReturn();

        String resetToken = objectMapper.readTree(forgotPasswordResult.getResponse().getContentAsString())
                .get("resetToken").asText();

        mockMvc.perform(get("/api/auth/notifications")
                        .header("Authorization", bearerToken(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$[0].deliveryState").value("PENDING"));

        notificationDispatchService.dispatchPendingNotifications();

        MvcResult authNotificationsResult = mockMvc.perform(get("/api/auth/notifications")
                        .header("Authorization", bearerToken(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$[0].deliveryState").value("ACCEPTED"))
                .andExpect(jsonPath("$[0].providerName").value("logging-email-provider"))
                .andExpect(jsonPath("$[0].providerMessageId").isString())
                .andReturn();

        JsonNode notificationPayload = objectMapper.readTree(authNotificationsResult.getResponse().getContentAsString()).get(0);
        String providerMessageId = notificationPayload.get("providerMessageId").asText();

        mockMvc.perform(post("/api/providers/notifications/events")
                        .header("X-Provider-Webhook-Secret", "local-webhook-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerName": "logging-email-provider",
                                  "providerMessageId": "%s",
                                  "eventType": "DELIVERED",
                                  "externalEventId": "evt-delivered-1",
                                  "payloadSummary": "provider delivered"
                                }
                                """.formatted(providerMessageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryState").value("DELIVERED"));

        mockMvc.perform(get("/api/auth/notifications")
                        .header("Authorization", bearerToken(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deliveryState").value("DELIVERED"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "%s",
                                  "newPassword": "member-reset-789"
                                }
                                """.formatted(resetToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MEMBER_EMAIL));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(preResetActiveSession.refreshToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));

        mockMvc.perform(get("/api/auth/activity")
                        .header("Authorization", bearerToken(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItems(
                        "PASSWORD_RESET_REQUESTED",
                        "PASSWORD_RESET_CONSUMED",
                        "AUTH_ALL_SESSIONS_REVOKED",
                        "AUTH_PASSWORD_CHANGED",
                        "AUTH_SESSION_REVOKED")));

        AuthTokens passwordResetTokens = issueToken(MEMBER_EMAIL, "member-reset-789");
        assertThat(passwordResetTokens.accessToken()).isNotBlank();

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "%s",
                                      "password": "wrong-password"
                                    }
                                    """.formatted(OWNER_EMAIL)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "wrong-password"
                                }
                                """.formatted(OWNER_EMAIL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many authentication attempts. Please wait and try again."));
    }

    @Test
    void rejectsCsvImportsWithNoTransactionRows() throws Exception {
        String emptyCsvOwnerEmail = "empty-csv-owner@acme.test";
        String emptyCsvOwnerPassword = "password789";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Empty Csv Owner",
                                  "password": "%s"
                                }
                                """.formatted(emptyCsvOwnerEmail, emptyCsvOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(emptyCsvOwnerEmail, emptyCsvOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-empty.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file contained no transaction rows. Expected header id,date,merchant,memo,amount,mcc."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void returnsBadRequestForMalformedCsvImportRequests() throws Exception {
        String malformedImportOwnerEmail = "malformed-import-owner@acme.test";
        String malformedImportOwnerPassword = "password889";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Malformed Import Owner",
                                  "password": "%s"
                                }
                                """.formatted(malformedImportOwnerEmail, malformedImportOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(malformedImportOwnerEmail, malformedImportOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,2026-03-15,STARBUCKS,coffee with client,18.45,5814
                """.getBytes());
        MockMultipartFile wrongPartNameFile = new MockMultipartFile(
                "upload",
                "transactions.csv",
                "text/csv",
                validFile.getBytes());
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "transactions.csv",
                "text/csv",
                new byte[0]);

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(wrongPartNameFile)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file is required. Attach a file in multipart field 'file'."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"));

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(emptyFile)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file is required. Attach a file in multipart field 'file'."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"));

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file is required. Attach a file in multipart field 'file'."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"));

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(validFile)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required request parameter: financialAccountId"))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"));

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(validFile)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for request parameter: financialAccountId"))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"));
    }

    @Test
    void rejectsCsvImportsWithMissingRequiredColumns() throws Exception {
        String invalidHeaderOwnerEmail = "invalid-header-owner@acme.test";
        String invalidHeaderOwnerPassword = "password987";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Invalid Header Owner",
                                  "password": "%s"
                                }
                                """.formatted(invalidHeaderOwnerEmail, invalidHeaderOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(invalidHeaderOwnerEmail, invalidHeaderOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-missing-columns.csv",
                "text/csv",
                """
                id,date,merchant,amount
                txn-1,2026-03-15,STARBUCKS,18.45
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file is missing required columns: memo, mcc. Expected header id,date,merchant,memo,amount,mcc."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void rejectsCsvImportsWithInvalidDateValues() throws Exception {
        String invalidDateOwnerEmail = "invalid-date-owner@acme.test";
        String invalidDateOwnerPassword = "password654";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Invalid Date Owner",
                                  "password": "%s"
                                }
                                """.formatted(invalidDateOwnerEmail, invalidDateOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(invalidDateOwnerEmail, invalidDateOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-invalid-date.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,03/15/2026,STARBUCKS,coffee with client,18.45,5814
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV row 1 has invalid value for column 'date': '03/15/2026'. Expected ISO-8601 date like 2026-03-15."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void rejectsCsvImportsWithInvalidAmountValues() throws Exception {
        String invalidAmountOwnerEmail = "invalid-amount-owner@acme.test";
        String invalidAmountOwnerPassword = "password321";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Invalid Amount Owner",
                                  "password": "%s"
                                }
                                """.formatted(invalidAmountOwnerEmail, invalidAmountOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(invalidAmountOwnerEmail, invalidAmountOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-invalid-amount.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,2026-03-15,STARBUCKS,coffee with client,eighteen,5814
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV row 1 has invalid value for column 'amount': 'eighteen'. Expected numeric amount like 18.45."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void rejectsCsvImportsWithBlankRequiredValues() throws Exception {
        String blankValueOwnerEmail = "blank-value-owner@acme.test";
        String blankValueOwnerPassword = "password111";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Blank Value Owner",
                                  "password": "%s"
                                }
                                """.formatted(blankValueOwnerEmail, blankValueOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(blankValueOwnerEmail, blankValueOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-blank-required.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,2026-03-15,,coffee with client,18.45,5814
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV row 1 is missing a value for required column 'merchant'."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void rejectsCsvImportsWithDuplicateIdsInSameFile() throws Exception {
        String duplicateIdOwnerEmail = "duplicate-id-owner@acme.test";
        String duplicateIdOwnerPassword = "password222";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Duplicate Id Owner",
                                  "password": "%s"
                                }
                                """.formatted(duplicateIdOwnerEmail, duplicateIdOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(duplicateIdOwnerEmail, duplicateIdOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-duplicate-ids.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,2026-03-15,STARBUCKS,coffee with client,18.45,5814
                txn-1,2026-03-16,COMCAST,office internet bill,89.99,4814
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV row 2 duplicates transaction id 'txn-1' within the uploaded file."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void returnsAllCsvRowValidationErrorsAtOnce() throws Exception {
        String batchErrorOwnerEmail = "batch-error-owner@acme.test";
        String batchErrorOwnerPassword = "password333";

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Batch Error Owner",
                                  "password": "%s"
                                }
                                """.formatted(batchErrorOwnerEmail, batchErrorOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(createUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(batchErrorOwnerEmail, batchErrorOwnerPassword);
        String organizationId = createOrganization(ownerUserId);
        String accountId = createAccount(organizationId, ownerTokens.accessToken());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "transactions-multi-error.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-1,03/15/2026,STARBUCKS,coffee with client,18.45,5814
                txn-1,2026-03-16,,office internet bill,eighteen,4814
                """.getBytes());

        mockMvc.perform(multipart("/api/transactions/import/csv")
                        .file(file)
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("financialAccountId", accountId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV file contains 4 validation errors."))
                .andExpect(jsonPath("$.details[0]").value("CSV row 1 has invalid value for column 'date': '03/15/2026'. Expected ISO-8601 date like 2026-03-15."))
                .andExpect(jsonPath("$.details[1]").value("CSV row 2 is missing a value for required column 'merchant'."))
                .andExpect(jsonPath("$.details[2]").value("CSV row 2 has invalid value for column 'amount': 'eighteen'. Expected numeric amount like 18.45."))
                .andExpect(jsonPath("$.details[3]").value("CSV row 2 duplicates transaction id 'txn-1' within the uploaded file."))
                .andExpect(jsonPath("$.path").value("/api/transactions/import/csv"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("X-Request-Id"));
    }

    @Test
    void listsOpenDeadLetterSupportPerformanceTasksForAdminsOnly() throws Exception {
        String performanceOwnerEmail = "performance-owner@acme.test";
        String performanceOwnerPassword = "password444";
        String performanceMemberEmail = "performance-member@acme.test";
        String performanceMemberPassword = "password555";

        MvcResult ownerUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Performance Owner",
                                  "password": "%s"
                                }
                                """.formatted(performanceOwnerEmail, performanceOwnerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(ownerUserResult.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Performance Member",
                                  "password": "%s"
                                }
                                """.formatted(performanceMemberEmail, performanceMemberPassword)))
                .andExpect(status().isOk());

        AuthTokens ownerTokens = issueToken(performanceOwnerEmail, performanceOwnerPassword);
        AuthTokens memberTokens = issueToken(performanceMemberEmail, performanceMemberPassword);
        String organizationId = createOrganization(ownerUserId);
        UUID organizationUuid = UUID.fromString(organizationId);
        UUID ownerUserUuid = userService.getByEmail(performanceOwnerEmail).getId();
        UUID memberUserUuid = userService.getByEmail(performanceMemberEmail).getId();
        addMembership(organizationId, memberUserUuid.toString(), ownerTokens.accessToken());

        WorkflowTask task = new WorkflowTask();
        task.setOrganization(organizationService.get(organizationUuid));
        task.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        task.setStatus(WorkflowTaskStatus.OPEN);
        task.setPriority(WorkflowTaskPriority.CRITICAL);
        task.setTitle("Dead-letter support performance at risk");
        task.setDescription("Support performance risk needs attention");
        task.setAssignedToUser(userService.get(ownerUserUuid));
        task.setDueDate(java.time.LocalDate.now());
        workflowTaskRepository.save(task);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(task.getId().toString()))
                .andExpect(jsonPath("$[0].taskType").value("DEAD_LETTER_SUPPORT_PERFORMANCE"))
                .andExpect(jsonPath("$[0].title").value("Dead-letter support performance at risk"))
                .andExpect(jsonPath("$[0].assignedToUserId").value(ownerUserUuid.toString()));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void filtersAndSummarizesDeadLetterSupportPerformanceTasksForAdmins() throws Exception {
        String ownerEmail = "performance-summary-owner@acme.test";
        String ownerPassword = "password666";

        MvcResult ownerUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Performance Summary Owner",
                                  "password": "%s"
                                }
                                """.formatted(ownerEmail, ownerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(ownerUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(ownerEmail, ownerPassword);
        String organizationId = createOrganization(ownerUserId);
        UUID organizationUuid = UUID.fromString(organizationId);
        UUID ownerUserUuid = userService.getByEmail(ownerEmail).getId();

        WorkflowTask acknowledgedTask = new WorkflowTask();
        acknowledgedTask.setOrganization(organizationService.get(organizationUuid));
        acknowledgedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        acknowledgedTask.setStatus(WorkflowTaskStatus.OPEN);
        acknowledgedTask.setPriority(WorkflowTaskPriority.CRITICAL);
        acknowledgedTask.setTitle("Acknowledged performance risk");
        acknowledgedTask.setDescription("Owner is already working this");
        acknowledgedTask.setAssignedToUser(userService.get(ownerUserUuid));
        acknowledgedTask.setAcknowledgedAt(java.time.Instant.now());
        acknowledgedTask.setAcknowledgedByUser(userService.get(ownerUserUuid));
        acknowledgedTask.setDueDate(java.time.LocalDate.now().minusDays(1));
        workflowTaskRepository.save(acknowledgedTask);

        WorkflowTask unassignedTask = new WorkflowTask();
        unassignedTask.setOrganization(organizationService.get(organizationUuid));
        unassignedTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        unassignedTask.setStatus(WorkflowTaskStatus.OPEN);
        unassignedTask.setPriority(WorkflowTaskPriority.CRITICAL);
        unassignedTask.setTitle("Unassigned performance risk");
        unassignedTask.setDescription("Needs ownership");
        unassignedTask.setDueDate(java.time.LocalDate.now().plusDays(1));
        workflowTaskRepository.save(unassignedTask);
        auditService.record(
                organizationUuid,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                "workflow_task",
                unassignedTask.getId().toString(),
                "Reactivated support performance risk after snooze expired on " + java.time.LocalDate.now().minusDays(1));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("filter", "ACKNOWLEDGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].taskId").value(acknowledgedTask.getId().toString()))
                .andExpect(jsonPath("$[0].acknowledgedByUserId").value(ownerUserUuid.toString()));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .param("filter", "REACTIVATED_NEEDS_ATTENTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].taskId").value(unassignedTask.getId().toString()))
                .andExpect(jsonPath("$[0].acknowledgedAt").doesNotExist());

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/summary")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTaskCount").value(2))
                .andExpect(jsonPath("$.assignedTaskCount").value(1))
                .andExpect(jsonPath("$.unassignedTaskCount").value(1))
                .andExpect(jsonPath("$.acknowledgedTaskCount").value(1))
                .andExpect(jsonPath("$.unacknowledgedTaskCount").value(1))
                .andExpect(jsonPath("$.snoozedTaskCount").value(0))
                .andExpect(jsonPath("$.overdueTaskCount").value(1))
                .andExpect(jsonPath("$.reactivatedNeedsAttentionCount").value(1))
                .andExpect(jsonPath("$.reactivatedOverdueCount").value(0));
    }

    @Test
    void snoozesDeadLetterSupportPerformanceTaskForAdmins() throws Exception {
        String ownerEmail = "performance-snooze-owner@acme.test";
        String ownerPassword = "password777";

        MvcResult ownerUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Performance Snooze Owner",
                                  "password": "%s"
                                }
                                """.formatted(ownerEmail, ownerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(ownerUserResult.getResponse().getContentAsString()).get("id").asText();
        AuthTokens ownerTokens = issueToken(ownerEmail, ownerPassword);
        String organizationId = createOrganization(ownerUserId);
        UUID organizationUuid = UUID.fromString(organizationId);

        WorkflowTask task = new WorkflowTask();
        task.setOrganization(organizationService.get(organizationUuid));
        task.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        task.setStatus(WorkflowTaskStatus.OPEN);
        task.setPriority(WorkflowTaskPriority.CRITICAL);
        task.setTitle("Dead-letter support performance at risk");
        task.setDescription("Needs monitoring");
        workflowTaskRepository.save(task);

        String snoozedUntil = java.time.LocalDate.now().plusDays(2).toString();
        mockMvc.perform(post("/api/workflows/notifications/dead-letter/performance/tasks/{taskId}/snooze", task.getId())
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "snoozedUntil": "%s",
                                  "note": "Waiting on next staffing check-in"
                                }
                                """.formatted(snoozedUntil)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskId").value(task.getId().toString()))
                .andExpect(jsonPath("$.snoozedUntil").value(snoozedUntil))
                .andExpect(jsonPath("$.acknowledgedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolutionComment").value("Waiting on next staffing check-in"));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/summary")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snoozedTaskCount").value(1))
                .andExpect(jsonPath("$.acknowledgedTaskCount").value(1))
                .andExpect(jsonPath("$.ignoredTaskCount").value(0));
    }

    @Test
    void listsHighPriorityDeadLetterSupportPerformanceTasksForAdminsOnly() throws Exception {
        String ownerEmail = "performance-priority-owner@acme.test";
        String ownerPassword = "password888";
        String memberEmail = "performance-priority-member@acme.test";
        String memberPassword = "password999";

        MvcResult ownerUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Performance Priority Owner",
                                  "password": "%s"
                                }
                                """.formatted(ownerEmail, ownerPassword)))
                .andExpect(status().isOk())
                .andReturn();

        String ownerUserId = objectMapper.readTree(ownerUserResult.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Performance Priority Member",
                                  "password": "%s"
                                }
                                """.formatted(memberEmail, memberPassword)))
                .andExpect(status().isOk());

        AuthTokens ownerTokens = issueToken(ownerEmail, ownerPassword);
        AuthTokens memberTokens = issueToken(memberEmail, memberPassword);
        String organizationId = createOrganization(ownerUserId);
        UUID organizationUuid = UUID.fromString(organizationId);
        UUID memberUserUuid = userService.getByEmail(memberEmail).getId();
        addMembership(organizationId, memberUserUuid.toString(), ownerTokens.accessToken());

        WorkflowTask ignoredTask = new WorkflowTask();
        ignoredTask.setOrganization(organizationService.get(organizationUuid));
        ignoredTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        ignoredTask.setStatus(WorkflowTaskStatus.OPEN);
        ignoredTask.setPriority(WorkflowTaskPriority.CRITICAL);
        ignoredTask.setTitle("Ignored performance risk");
        ignoredTask.setDescription("Has already been escalated");
        ignoredTask.setDueDate(java.time.LocalDate.now().plusDays(1));
        workflowTaskRepository.save(ignoredTask);

        Notification escalation = new Notification();
        escalation.setOrganization(organizationService.get(organizationUuid));
        escalation.setWorkflowTask(ignoredTask);
        escalation.setCategory(NotificationCategory.WORKFLOW);
        escalation.setChannel(NotificationChannel.IN_APP);
        escalation.setStatus(NotificationStatus.SENT);
        escalation.setDeliveryState(NotificationDeliveryState.DELIVERED);
        escalation.setReferenceType("dead_letter_support_performance_escalation");
        escalation.setReferenceId(ignoredTask.getId().toString());
        escalation.setRecipientEmail(ownerEmail);
        escalation.setScheduledFor(java.time.Instant.now());
        escalation.setSentAt(java.time.Instant.now());
        escalation.setAttemptCount(0);
        escalation.setMessage("Escalated ignored performance risk");
        notificationRepository.save(escalation);

        WorkflowTask reactivatedOverdueTask = new WorkflowTask();
        reactivatedOverdueTask.setOrganization(organizationService.get(organizationUuid));
        reactivatedOverdueTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        reactivatedOverdueTask.setStatus(WorkflowTaskStatus.OPEN);
        reactivatedOverdueTask.setPriority(WorkflowTaskPriority.CRITICAL);
        reactivatedOverdueTask.setTitle("Reactivated overdue performance risk");
        reactivatedOverdueTask.setDescription("Snooze expired and the task is overdue again");
        reactivatedOverdueTask.setDueDate(java.time.LocalDate.now().minusDays(1));
        workflowTaskRepository.save(reactivatedOverdueTask);
        auditService.record(
                organizationUuid,
                "DEAD_LETTER_SUPPORT_PERFORMANCE_TASK_REACTIVATED",
                "workflow_task",
                reactivatedOverdueTask.getId().toString(),
                "Reactivated support performance risk after snooze expired on " + java.time.LocalDate.now().minusDays(1));

        WorkflowTask ordinaryTask = new WorkflowTask();
        ordinaryTask.setOrganization(organizationService.get(organizationUuid));
        ordinaryTask.setTaskType(WorkflowTaskType.DEAD_LETTER_SUPPORT_PERFORMANCE);
        ordinaryTask.setStatus(WorkflowTaskStatus.OPEN);
        ordinaryTask.setPriority(WorkflowTaskPriority.CRITICAL);
        ordinaryTask.setTitle("Ordinary performance risk");
        ordinaryTask.setDescription("Still open, but not urgent enough for the priority queue");
        ordinaryTask.setDueDate(java.time.LocalDate.now().plusDays(2));
        workflowTaskRepository.save(ordinaryTask);

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks/high-priority")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].taskId").value(reactivatedOverdueTask.getId().toString()))
                .andExpect(jsonPath("$[1].taskId").value(ignoredTask.getId().toString()));

        mockMvc.perform(get("/api/workflows/notifications/dead-letter/performance/tasks/high-priority")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(memberTokens.accessToken()))
                        .param("organizationId", organizationId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private String createUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Acme Owner"
                                  ,"password": "%s"
                                }
                                """.formatted(OWNER_EMAIL, OWNER_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createMemberUser() throws Exception {
        return createUser(MEMBER_EMAIL, "Acme Member", MEMBER_PASSWORD);
    }

    private String createUser(String email, String fullName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, fullName, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createOrganization(String ownerUserId) throws Exception {
        return createOrganization("Acme Design LLC", ownerUserId);
    }

    private String createOrganization(String organizationName, String ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "planTier": "GROWTH",
                                  "timezone": "America/Los_Angeles",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(organizationName, ownerUserId)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private void addMembership(String organizationId, String memberUserId, String ownerToken) throws Exception {
        mockMvc.perform(post("/api/users/memberships")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "userId": "%s",
                                  "role": "MEMBER"
                                }
                                """.formatted(organizationId, memberUserId)))
                .andExpect(status().isOk());
    }

    private String createAccount(String organizationId, String ownerToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                        .header(ORG_HEADER, organizationId)
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "%s",
                                  "name": "Operating Checking",
                                  "accountType": "BANK",
                                  "institutionName": "Infinite Matters Bank",
                                  "currency": "USD"
                                }
                                """.formatted(organizationId)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).contains("Operating Checking");
        return objectMapper.readTree(responseBody).get("id").asText();
    }

    private AuthTokens issueToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshSessionId").isString())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthTokens(
                json.get("accessToken").asText(),
                json.get("refreshToken").asText(),
                json.get("refreshSessionId").asText());
    }

    private AuthTokens refreshToken(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshSessionId").isString())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthTokens(
                json.get("accessToken").asText(),
                json.get("refreshToken").asText(),
                json.get("refreshSessionId").asText());
    }

    private void logout(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk());
    }

    private String bearerToken(String token) {
        return "Bearer " + token;
    }

    private record AuthTokens(String accessToken, String refreshToken, String refreshSessionId) {
    }
}

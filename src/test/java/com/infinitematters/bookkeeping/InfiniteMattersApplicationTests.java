package com.infinitematters.bookkeeping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinitematters.bookkeeping.notifications.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

    @Test
    void importsTransactionsAndRoutesAmbiguousItemsIntoReview() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists());

        String userId = createUser();
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

        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "transactions-2.csv",
                "text/csv",
                """
                id,date,merchant,memo,amount,mcc
                txn-3,2026-03-20,Unknown Vendor,renewal invoice,59.99,5734
                """.getBytes());

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
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "fullName": "Acme Member",
                                  "password": "%s"
                                }
                                """.formatted(MEMBER_EMAIL, MEMBER_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createOrganization(String ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme Design LLC",
                                  "planTier": "GROWTH",
                                  "timezone": "America/Los_Angeles",
                                  "ownerUserId": "%s"
                                }
                                """.formatted(ownerUserId)))
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

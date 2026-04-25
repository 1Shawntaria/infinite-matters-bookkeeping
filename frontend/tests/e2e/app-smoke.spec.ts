import { expect, test } from "@playwright/test";

const organizations = [
  {
    id: "org-primary",
    name: "Acme Books Demo",
    planTier: "GROWTH",
    timezone: "America/Los_Angeles",
    role: "OWNER",
  },
  {
    id: "org-secondary",
    name: "Sunrise Client Ops",
    planTier: "STARTER",
    timezone: "America/New_York",
    role: "ADMIN",
  },
  {
    id: "org-empty",
    name: "Northwind New Books",
    planTier: "STARTER",
    timezone: "America/Chicago",
    role: "MEMBER",
  },
];

function dashboardSnapshot(organizationId: string) {
  if (organizationId === "org-empty") {
    return {
      focusMonth: "2026-04",
      cashBalance: 0,
      postedTransactionCount: 0,
      primaryAction: null,
      workflowInbox: {
        openCount: 0,
        overdueCount: 0,
        dueTodayCount: 0,
        highPriorityCount: 0,
        unassignedCount: 0,
        assignedToCurrentUserCount: 0,
        recommendedActionLabel: null,
        recommendedActionKey: null,
        recommendedActionPath: null,
        recommendedActionUrgency: null,
        attentionTasks: [],
      },
      period: {
        closeReady: true,
        unreconciledAccountCount: 0,
        recommendedActionLabel: null,
        recommendedActionKey: null,
        recommendedActionPath: null,
        recommendedActionUrgency: null,
      },
      expenseCategories: [],
      staleAccounts: [],
      recentNotifications: [],
    };
  }

  if (organizationId === "org-secondary") {
    return {
      focusMonth: "2026-04",
      cashBalance: 8450.55,
      postedTransactionCount: 18,
      primaryAction: null,
      workflowInbox: {
        openCount: 1,
        overdueCount: 0,
        dueTodayCount: 0,
        highPriorityCount: 0,
        unassignedCount: 1,
        assignedToCurrentUserCount: 0,
        recommendedActionLabel: null,
        recommendedActionKey: null,
        recommendedActionPath: null,
        recommendedActionUrgency: null,
        attentionTasks: [],
      },
      period: {
        closeReady: true,
        unreconciledAccountCount: 0,
        recommendedActionLabel: null,
        recommendedActionKey: null,
        recommendedActionPath: null,
        recommendedActionUrgency: null,
      },
      expenseCategories: [],
      staleAccounts: [],
      recentNotifications: [],
    };
  }

  return {
    focusMonth: "2026-04",
    cashBalance: 15234.12,
    postedTransactionCount: 27,
    primaryAction: {
      cardId: "period-close",
      label: "Finish account reconciliations",
      actionKey: "FINISH_RECONCILIATIONS",
      actionPath: "/reconciliation",
      itemCount: 1,
      reason: "1 account still needs reconciliation before close.",
      urgency: "HIGH",
      source: "PERIOD_CLOSE",
    },
    workflowInbox: {
      openCount: 3,
      overdueCount: 1,
      dueTodayCount: 1,
      highPriorityCount: 1,
      unassignedCount: 1,
      assignedToCurrentUserCount: 1,
      recommendedActionLabel: "Resolve open review tasks",
      recommendedActionKey: "REVIEW_HIGH_PRIORITY_TASKS",
      recommendedActionPath: "/workflows/inbox",
      recommendedActionUrgency: "HIGH",
      attentionTasks: [],
    },
    period: {
      closeReady: false,
      unreconciledAccountCount: 1,
      recommendedActionLabel: "Finish account reconciliations",
      recommendedActionKey: "FINISH_RECONCILIATIONS",
      recommendedActionPath: "/reconciliation",
      recommendedActionUrgency: "HIGH",
    },
    expenseCategories: [
      {
        itemId: "expense-category-software",
        category: "SOFTWARE",
        amount: 610.0,
        deltaFromPreviousMonth: 25.5,
        actionKey: "REVIEW_EXPENSE_CATEGORY",
        actionPath: "/transactions?category=SOFTWARE",
        actionUrgency: "NORMAL",
        actionReason: "Up 25.50 from last month.",
      },
    ],
    staleAccounts: [],
    recentNotifications: [],
  };
}

function reviewTasks() {
  return [
    {
      taskId: "review-task-1",
      transactionId: "txn-1",
      taskType: "TRANSACTION_REVIEW",
      priority: "HIGH",
      overdue: false,
      title: "Review AMZN MKTP",
      description: "Category needs confirmation.",
      dueDate: "2026-04-30",
      merchant: "AMZN MKTP",
      amount: 142.18,
      transactionDate: "2026-04-03",
      proposedCategory: "OTHER",
      confidenceScore: 0.83,
      route: "PREMIUM",
      resolutionComment: null,
    },
  ];
}

function reconciliationDashboard() {
  return {
    focusMonth: "2026-04",
    period: {
      closeReady: false,
      unreconciledAccountCount: 1,
      recommendedActionLabel: "Finish account reconciliations",
      recommendedActionKey: "FINISH_RECONCILIATIONS",
      recommendedActionPath: "/reconciliation",
      recommendedActionUrgency: "HIGH",
    },
    unreconciledAccounts: [
      {
        itemId: "recon-account-operating",
        accountId: "acct-operating",
        accountName: "Operating Checking",
        accountType: "BANK",
        lastTransactionDate: "2026-04-06",
        daysSinceActivity: 18,
        actionKey: "REVIEW_RECONCILIATION",
        actionPath: "/reconciliation/acct-operating?month=2026-04",
        actionUrgency: "HIGH",
        actionReason: "Account requires reconciliation before period close.",
        sessionStarted: false,
      },
    ],
  };
}

function reconciliationAccountDetail() {
  return {
    focusMonth: "2026-04",
    financialAccountId: "acct-operating",
    accountName: "Operating Checking",
    institutionName: "Infinite Matters Bank",
    accountType: "BANK",
    currency: "USD",
    active: true,
    session: {
      id: "recon-session-1",
      financialAccountId: "acct-operating",
      accountName: "Operating Checking",
      periodStart: "2026-04-01",
      periodEnd: "2026-04-30",
      openingBalance: 1000,
      statementEndingBalance: 1200,
      computedEndingBalance: 1128.43,
      varianceAmount: 71.57,
      notes: "Variance detected; investigate before close",
      status: "IN_PROGRESS",
      completedAt: null,
      createdAt: "2026-04-24T12:00:00Z",
    },
    bookEndingBalance: 1128.43,
    varianceAmount: 71.57,
    postedTransactionCount: 4,
    reviewRequiredCount: 2,
    canStartReconciliation: false,
    canCompleteReconciliation: true,
    statusMessage: "Resolve outstanding review items, then complete reconciliation.",
    transactions: [
      {
        transactionId: "txn-1",
        transactionDate: "2026-04-06",
        amount: 49.99,
        merchant: "UNKNOWN VENDOR",
        memo: "Needs review",
        status: "REVIEW_REQUIRED",
      },
      {
        transactionId: "txn-2",
        transactionDate: "2026-04-05",
        amount: 89.0,
        merchant: "CLOUDCO",
        memo: "Monthly software",
        status: "POSTED",
      },
    ],
  };
}

async function mockApi(page: Parameters<typeof test>[0]["page"]) {
  let remainingReviewTasks = reviewTasks();
  let memberships = [
    {
      id: "membership-owner",
      organizationId: "org-primary",
      user: {
        id: "user-1",
        email: "owner@acme.test",
        fullName: "Acme Owner",
        createdAt: "2026-04-20T12:00:00Z",
      },
      role: "OWNER",
      createdAt: "2026-04-20T12:05:00Z",
    },
    {
      id: "membership-admin",
      organizationId: "org-primary",
      user: {
        id: "user-2",
        email: "ops@acme.test",
        fullName: "Ops Admin",
        createdAt: "2026-04-20T12:10:00Z",
      },
      role: "ADMIN",
      createdAt: "2026-04-20T12:12:00Z",
    },
    {
      id: "membership-member",
      organizationId: "org-primary",
      user: {
        id: "user-3",
        email: "member@acme.test",
        fullName: "Staff Member",
        createdAt: "2026-04-20T12:20:00Z",
      },
      role: "MEMBER",
      createdAt: "2026-04-20T12:22:00Z",
    },
  ];
  let financialAccounts = [
    {
      id: "acct-operating",
      organizationId: "org-primary",
      name: "Operating Checking",
      accountType: "BANK",
      institutionName: "Infinite Matters Bank",
      currency: "USD",
      active: true,
      createdAt: "2026-04-24T12:00:00Z",
    },
  ];
  let importHistory = [
    {
      transactionId: "txn-history-1",
      financialAccountId: "acct-operating",
      financialAccountName: "Operating Checking",
      importedAt: "2026-04-24T12:00:00Z",
      transactionDate: "2026-04-05",
      amount: 89.0,
      merchant: "CLOUDCO",
      proposedCategory: "SOFTWARE",
      finalCategory: "SOFTWARE",
      route: "RULES",
      confidenceScore: 0.92,
      status: "POSTED",
    },
  ];
  const authActivity = [
    {
      id: "auth-activity-1",
      organizationId: null,
      actorUserId: "user-1",
      eventType: "AUTH_LOGIN_SUCCEEDED",
      entityType: "app_user",
      entityId: "user-1",
      details: "User authenticated",
      createdAt: "2026-04-24T11:58:00Z",
    },
  ];
  const authSessions = [
    {
      sessionId: "session-1",
      createdAt: "2026-04-24T11:58:00Z",
      expiresAt: "2026-05-24T11:58:00Z",
      lastUsedAt: "2026-04-24T12:10:00Z",
      revokedAt: null,
      revokedReason: null,
      reuseDetectedAt: null,
      replacedBySessionId: null,
      active: true,
    },
    {
      sessionId: "session-legacy",
      createdAt: "2026-04-23T09:15:00Z",
      expiresAt: "2026-05-23T09:15:00Z",
      lastUsedAt: "2026-04-23T17:25:00Z",
      revokedAt: null,
      revokedReason: null,
      reuseDetectedAt: null,
      replacedBySessionId: null,
      active: true,
    },
  ];
  const auditEvents = [
    {
      id: "audit-1",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "ORGANIZATION_MEMBER_ADDED",
      entityType: "organization_membership",
      entityId: "membership-1",
      details: "Added teammate to the bookkeeping workspace.",
      createdAt: "2026-04-24T12:05:00Z",
    },
  ];
  const authNotifications = [
    {
      id: "auth-notification-1",
      workflowTaskId: null,
      userId: "user-1",
      category: "AUTH_SECURITY",
      channel: "EMAIL",
      status: "SENT",
      deliveryState: "DELIVERED",
      message: "Your password was changed successfully.",
      referenceType: "app_user",
      referenceId: "user-1",
      recipientEmail: "owner@acme.test",
      providerName: "sendgrid",
      providerMessageId: "provider-auth-1",
      attemptCount: 1,
      lastError: null,
      lastFailureCode: null,
      deadLetterResolutionStatus: null,
      deadLetterResolutionReasonCode: null,
      deadLetterResolutionNote: null,
      deadLetterResolvedAt: null,
      deadLetterResolvedByUserId: null,
      scheduledFor: "2026-04-24T12:01:00Z",
      lastAttemptedAt: "2026-04-24T12:01:10Z",
      sentAt: "2026-04-24T12:01:12Z",
      createdAt: "2026-04-24T12:01:00Z",
    },
  ];
  const workflowNotifications = [
    {
      id: "workflow-notification-1",
      workflowTaskId: "task-1",
      userId: null,
      category: "WORKFLOW_TASK",
      channel: "EMAIL",
      status: "FAILED",
      deliveryState: "FAILED",
      message: "Review queue escalation could not be delivered.",
      referenceType: "workflow_task",
      referenceId: "task-1",
      recipientEmail: "ops@acme.test",
      providerName: "sendgrid",
      providerMessageId: "provider-workflow-1",
      attemptCount: 3,
      lastError: "Mailbox unavailable",
      lastFailureCode: "550",
      deadLetterResolutionStatus: null,
      deadLetterResolutionReasonCode: null,
      deadLetterResolutionNote: null,
      deadLetterResolvedAt: null,
      deadLetterResolvedByUserId: null,
      scheduledFor: "2026-04-24T12:02:00Z",
      lastAttemptedAt: "2026-04-24T12:03:10Z",
      sentAt: null,
      createdAt: "2026-04-24T12:02:00Z",
    },
    {
      id: "workflow-notification-2",
      workflowTaskId: "task-2",
      userId: null,
      category: "WORKFLOW_TASK",
      channel: "EMAIL",
      status: "SENT",
      deliveryState: "DELIVERED",
      message: "Monthly close reminder delivered successfully.",
      referenceType: "workflow_task",
      referenceId: "task-2",
      recipientEmail: "owner@acme.test",
      providerName: "sendgrid",
      providerMessageId: "provider-workflow-2",
      attemptCount: 1,
      lastError: null,
      lastFailureCode: null,
      deadLetterResolutionStatus: null,
      deadLetterResolutionReasonCode: null,
      deadLetterResolutionNote: null,
      deadLetterResolvedAt: null,
      deadLetterResolvedByUserId: null,
      scheduledFor: "2026-04-24T12:04:00Z",
      lastAttemptedAt: "2026-04-24T12:04:10Z",
      sentAt: "2026-04-24T12:04:12Z",
      createdAt: "2026-04-24T12:04:00Z",
    },
    {
      id: "workflow-notification-3",
      workflowTaskId: "task-3",
      userId: null,
      category: "WORKFLOW_TASK",
      channel: "EMAIL",
      status: "FAILED",
      deliveryState: "DEAD_LETTER",
      message: "Monthly close escalation entered dead-letter handling.",
      referenceType: "workflow_task",
      referenceId: "task-3",
      recipientEmail: "finance@acme.test",
      providerName: "sendgrid",
      providerMessageId: "provider-workflow-3",
      attemptCount: 4,
      lastError: "Remote mailbox rejected message repeatedly",
      lastFailureCode: "554",
      deadLetterResolutionStatus: null,
      deadLetterResolutionReasonCode: null,
      deadLetterResolutionNote: null,
      deadLetterResolvedAt: null,
      deadLetterResolvedByUserId: null,
      scheduledFor: "2026-04-24T12:06:00Z",
      lastAttemptedAt: "2026-04-24T12:07:10Z",
      sentAt: null,
      createdAt: "2026-04-24T12:06:00Z",
    },
  ];

  async function fulfillJson(
    route: Parameters<Parameters<typeof page.route>[1]>[0],
    body: unknown,
    status = 200
  ) {
    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  }

  await page.route("**/api/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const organizationHeader = request.headers()["x-organization-id"] ?? "org-primary";

    if (url.pathname === "/api/auth/token" && request.method() === "POST") {
      await fulfillJson(route, {
        user: {
          id: "user-1",
          email: "owner@acme.test",
          fullName: "Acme Owner",
        },
      });
      return;
    }

    if (url.pathname === "/api/auth/logout" && request.method() === "POST") {
      await fulfillJson(route, {});
      return;
    }

    if (url.pathname === "/api/auth/me" && request.method() === "GET") {
      await fulfillJson(route, {
        id: "user-1",
        email: "owner@acme.test",
        fullName: "Acme Owner",
        createdAt: "2026-04-20T12:00:00Z",
      });
      return;
    }

    if (url.pathname === "/api/auth/sessions" && request.method() === "GET") {
      await fulfillJson(route, authSessions);
      return;
    }

    if (url.pathname.startsWith("/api/auth/sessions/") && request.method() === "POST") {
      const sessionId = url.pathname.split("/")[4];
      const target = authSessions.find((session) => session.sessionId === sessionId);
      if (!target) {
        await fulfillJson(route, { message: "Session not found" }, 404);
        return;
      }

      const updated = {
        ...target,
        active: false,
        revokedAt: "2026-04-24T12:15:00Z",
        revokedReason: "Revoked from security activity workspace",
      };
      const index = authSessions.findIndex((session) => session.sessionId === sessionId);
      authSessions[index] = updated;
      await fulfillJson(route, updated);
      return;
    }

    if (url.pathname === "/api/auth/activity" && request.method() === "GET") {
      await fulfillJson(route, authActivity);
      return;
    }

    if (url.pathname === "/api/auth/notifications" && request.method() === "GET") {
      await fulfillJson(route, authNotifications);
      return;
    }

    if (url.pathname === "/api/audit/events" && request.method() === "GET") {
      await fulfillJson(route, auditEvents);
      return;
    }

    if (url.pathname === "/api/workflows/notifications" && request.method() === "GET") {
      await fulfillJson(route, workflowNotifications);
      return;
    }

    if (url.pathname === "/api/workflows/notifications/attention" && request.method() === "GET") {
      await fulfillJson(route, [workflowNotifications[0], workflowNotifications[2]]);
      return;
    }

    if (url.pathname === "/api/workflows/notifications/dead-letter" && request.method() === "GET") {
      await fulfillJson(route, [workflowNotifications[2]]);
      return;
    }

    if (url.pathname === "/api/workflows/notifications/dead-letter/history" && request.method() === "GET") {
      await fulfillJson(route, [
        {
          ...workflowNotifications[2],
          deadLetterResolutionStatus: "RESOLVED",
          deadLetterResolvedAt: "2026-04-24T12:20:00Z",
          deadLetterResolutionNote: "Resolved from notifications workspace",
        },
      ]);
      return;
    }

    if (
      url.pathname.startsWith("/api/workflows/notifications/") &&
      url.pathname.endsWith("/dead-letter/retry") &&
      request.method() === "POST"
    ) {
      const notificationId = url.pathname.split("/")[4];
      const index = workflowNotifications.findIndex((item) => item.id === notificationId);
      workflowNotifications[index] = {
        ...workflowNotifications[index],
        deliveryState: "PENDING",
        status: "QUEUED",
        lastError: null,
        lastAttemptedAt: "2026-04-24T12:09:00Z",
      };
      await fulfillJson(route, workflowNotifications[index]);
      return;
    }

    if (
      url.pathname.startsWith("/api/workflows/notifications/") &&
      url.pathname.endsWith("/dead-letter/resolve") &&
      request.method() === "POST"
    ) {
      const notificationId = url.pathname.split("/")[4];
      const index = workflowNotifications.findIndex((item) => item.id === notificationId);
      workflowNotifications[index] = {
        ...workflowNotifications[index],
        deadLetterResolutionStatus: "RESOLVED",
        deadLetterResolutionNote: "Resolved from notifications workspace",
      };
      await fulfillJson(route, workflowNotifications[index]);
      return;
    }

    if (
      url.pathname.startsWith("/api/workflows/notifications/") &&
      url.pathname.endsWith("/requeue") &&
      request.method() === "POST"
    ) {
      const notificationId = url.pathname.split("/")[4];
      const index = workflowNotifications.findIndex((item) => item.id === notificationId);
      workflowNotifications[index] = {
        ...workflowNotifications[index],
        deliveryState: "PENDING",
        status: "QUEUED",
        lastError: null,
        lastAttemptedAt: "2026-04-24T12:08:00Z",
      };
      await fulfillJson(route, workflowNotifications[index]);
      return;
    }

    if (url.pathname === "/api/users/organizations" && request.method() === "GET") {
      await fulfillJson(route, organizations);
      return;
    }

    if (url.pathname === "/api/users/memberships" && request.method() === "GET") {
      const targetOrganizationId = url.searchParams.get("organizationId") ?? "org-primary";
      await fulfillJson(
        route,
        memberships.filter((membership) => membership.organizationId === targetOrganizationId)
      );
      return;
    }

    if (url.pathname === "/api/users/memberships/by-email" && request.method() === "POST") {
      const body = request.postDataJSON() as {
        organizationId: string;
        email: string;
        role: string;
      };
      const createdMembership = {
        id: `membership-${memberships.length + 1}`,
        organizationId: body.organizationId,
        user: {
          id: `user-${memberships.length + 1}`,
          email: body.email,
          fullName: "New Workspace Member",
          createdAt: "2026-04-24T13:00:00Z",
        },
        role: body.role,
        createdAt: "2026-04-24T13:01:00Z",
      };
      memberships = [...memberships, createdMembership];
      await fulfillJson(route, createdMembership);
      return;
    }

    if (url.pathname.startsWith("/api/users/memberships/") && request.method() === "PATCH") {
      const membershipId = url.pathname.split("/")[4];
      const body = request.postDataJSON() as { role: string };
      const index = memberships.findIndex((membership) => membership.id === membershipId);
      memberships[index] = {
        ...memberships[index],
        role: body.role,
      };
      await fulfillJson(route, memberships[index]);
      return;
    }

    if (url.pathname === "/api/accounts" && request.method() === "GET") {
      await fulfillJson(route, financialAccounts);
      return;
    }

    if (url.pathname === "/api/accounts" && request.method() === "POST") {
      const body = request.postDataJSON() as {
        organizationId: string;
        name: string;
        accountType: string;
        institutionName: string;
        currency: string;
      };

      const createdAccount = {
        id: `acct-${financialAccounts.length + 1}`,
        organizationId: body.organizationId,
        name: body.name,
        accountType: body.accountType,
        institutionName: body.institutionName,
        currency: body.currency,
        active: true,
        createdAt: "2026-04-24T12:30:00Z",
      };
      financialAccounts = [...financialAccounts, createdAccount];
      await fulfillJson(route, createdAccount);
      return;
    }

    if (url.pathname === "/api/dashboard/snapshot" && request.method() === "GET") {
      const isReconciliationPage = page.url().includes("/reconciliation");
      await fulfillJson(
        route,
        isReconciliationPage
          ? reconciliationDashboard()
          : dashboardSnapshot(organizationHeader)
      );
      return;
    }

    if (url.pathname === "/api/reviews/tasks" && request.method() === "GET") {
      await fulfillJson(route, remainingReviewTasks);
      return;
    }

    if (url.pathname.startsWith("/api/reviews/tasks/") && request.method() === "POST") {
      const taskId = url.pathname.split("/").at(-2);
      remainingReviewTasks = remainingReviewTasks.filter((task) => task.taskId !== taskId);
      await fulfillJson(route, { ok: true });
      return;
    }

    if (url.pathname === "/api/reconciliations" && request.method() === "GET") {
      await fulfillJson(route, []);
      return;
    }

    if (url.pathname === "/api/reconciliations" && request.method() === "POST") {
      await fulfillJson(route, reconciliationAccountDetail().session);
      return;
    }

    if (url.pathname === "/api/transactions/import/csv" && request.method() === "POST") {
      const destinationAccountId = url.searchParams.get("financialAccountId") ?? "acct-operating";
      const destinationAccount =
        financialAccounts.find((account) => account.id === destinationAccountId) ??
        financialAccounts[0];
      importHistory = [
        {
          transactionId: "txn-new-1",
          financialAccountId: destinationAccount.id,
          financialAccountName: destinationAccount.name,
          importedAt: "2026-04-24T12:45:00Z",
          transactionDate: "2026-04-06",
          amount: 49.99,
          merchant: "UNKNOWN VENDOR",
          proposedCategory: "OTHER",
          finalCategory: null,
          route: "PREMIUM",
          confidenceScore: 0.83,
          status: "REVIEW_REQUIRED",
        },
        {
          transactionId: "txn-new-2",
          financialAccountId: destinationAccount.id,
          financialAccountName: destinationAccount.name,
          importedAt: "2026-04-24T12:45:00Z",
          transactionDate: "2026-04-05",
          amount: 89.0,
          merchant: "CLOUDCO",
          proposedCategory: "SOFTWARE",
          finalCategory: "SOFTWARE",
          route: "RULES",
          confidenceScore: 0.92,
          status: "POSTED",
        },
        ...importHistory,
      ];
      await fulfillJson(route, {
        importedCount: 3,
        duplicateCount: 0,
        reviewRequiredCount: 1,
        postedCount: 2,
        transactions: [
          {
            transactionId: "txn-new-1",
            transactionDate: "2026-04-06",
            amount: 49.99,
            merchant: "UNKNOWN VENDOR",
            proposedCategory: "OTHER",
            finalCategory: null,
            route: "PREMIUM",
            confidenceScore: 0.83,
            status: "REVIEW_REQUIRED",
          },
          {
            transactionId: "txn-new-2",
            transactionDate: "2026-04-05",
            amount: 89.0,
            merchant: "CLOUDCO",
            proposedCategory: "SOFTWARE",
            finalCategory: "SOFTWARE",
            route: "RULES",
            confidenceScore: 0.92,
            status: "POSTED",
          },
        ],
      });
      return;
    }

    if (url.pathname === "/api/transactions/import-history" && request.method() === "GET") {
      const financialAccountId = url.searchParams.get("financialAccountId");
      await fulfillJson(
        route,
        financialAccountId
          ? importHistory.filter((item) => item.financialAccountId === financialAccountId)
          : importHistory
      );
      return;
    }

    if (url.pathname === "/api/reconciliations/accounts/acct-operating" && request.method() === "GET") {
      await fulfillJson(route, reconciliationAccountDetail());
      return;
    }

    if (
      url.pathname === "/api/reconciliations/recon-session-1/complete" &&
      request.method() === "POST"
    ) {
      await fulfillJson(route, {
        ...reconciliationAccountDetail().session,
        status: "COMPLETED",
        varianceAmount: 0,
        notes: "Balanced successfully",
      });
      return;
    }

    await fulfillJson(
      route,
      { message: `Unhandled mock route: ${request.method()} ${url.pathname}` },
      404
    );
  });
}

test.beforeEach(async ({ page }) => {
  await mockApi(page);
});

async function seedOrganization(page: Parameters<typeof test>[0]["page"], organizationId = "org-primary") {
  await page.addInitScript((seededOrganizationId) => {
    window.sessionStorage.setItem("organizationId", seededOrganizationId);
  }, organizationId);
}

test("login stores organization context and lands on dashboard", async ({ page }) => {
  await page.goto("/login");

  await page.getByLabel("Email").fill("owner@acme.test");
  await page.getByLabel("Password").fill("password123");
  await page.getByRole("button", { name: "Enter workspace" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
  await expect(page.locator("select:visible").first()).toHaveValue("org-primary");
  await expect(page.getByText("$15234.12")).toBeVisible();
  await expect(page.getByText("Session and trust").filter({ visible: true })).toBeVisible();
  await expect(page.getByText("Acme Owner").filter({ visible: true })).toBeVisible();
});

test("workspace switching reloads dashboard data for the selected organization", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/dashboard");

  const workspaceSelect = page.locator("select:visible").first();

  await expect(workspaceSelect).toHaveValue("org-primary");
  await expect(page.getByText("$15234.12")).toBeVisible();
  await expect(page.getByText("Operating Checking · CLOUDCO")).toBeVisible();
  await expect(page.getByText("AUTH LOGIN SUCCEEDED")).toBeVisible();

  await workspaceSelect.selectOption("org-secondary");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByText("$8450.55")).toBeVisible();
});

test("activity page shows merged operational timeline and filter views", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/activity");

  await expect(page.getByRole("heading", { name: "Activity" })).toBeVisible();
  await expect(page.getByText("Auth Login Succeeded")).toBeVisible();
  await expect(page.getByText("Organization Member Added").last()).toBeVisible();
  await expect(page.getByText("Operating Checking: CLOUDCO")).toBeVisible();

  await page.getByRole("button", { name: "Revoke session" }).nth(1).click();
  await expect(page.getByText("Session revoked. The activity feed has been refreshed.")).toBeVisible();
  await expect(page.getByText("REVOKED", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Audit" }).click();
  await expect(page.getByText("Organization Member Added").last()).toBeVisible();
  await expect(page.getByText("Added teammate to the bookkeeping workspace.")).toBeVisible();
});

test("notifications inbox merges auth and workflow delivery signals", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/notifications");

  await expect(page.getByRole("heading", { name: "Notifications" })).toBeVisible();
  await expect(page.getByText("Your password was changed successfully.")).toBeVisible();
  await expect(page.getByText("Review queue escalation could not be delivered.").first()).toBeVisible();
  await expect(page.getByText("Mailbox unavailable").first()).toBeVisible();
  await expect(page.locator("span").filter({ hasText: "Attention" }).first()).toBeVisible();
  await expect(page.getByText("Monthly close escalation entered dead-letter handling.").first()).toBeVisible();

  await page.getByRole("button", { name: "Retry delivery" }).first().click();
  await expect(page.getByText("Delivery retry queued successfully.")).toBeVisible();

  await page.getByRole("button", { name: "Mark resolved" }).click();
  await expect(page.getByText("Dead-letter notification marked resolved.")).toBeVisible();
  await expect(page.getByText("Resolved from notifications workspace").last()).toBeVisible();

  await page.getByRole("button", { name: "Auth" }).click();
  await expect(page.getByText("Your password was changed successfully.")).toBeVisible();

  await page.getByRole("button", { name: "Attention" }).click();
  await expect(page.getByText("Review queue escalation could not be delivered.").first()).toBeVisible();
});

test("access workspace lets operators review and update memberships", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/access");

  await expect(page.getByRole("heading", { name: "Access", exact: true })).toBeVisible();
  await expect(page.getByText("Ops Admin")).toBeVisible();
  await expect(page.getByText("Staff Member")).toBeVisible();

  await page.getByPlaceholder("teammate@company.com").fill("new.member@acme.test");
  await page.locator("select").last().selectOption("ADMIN");
  await page.getByRole("button", { name: "Grant access" }).click();
  await expect(page.getByText("Workspace access updated successfully.")).toBeVisible();
  await expect(page.getByText("new.member@acme.test")).toBeVisible();

  const staffMemberCard = page.locator("div.rounded-lg").filter({
    has: page.getByText("Staff Member"),
  });
  await staffMemberCard.locator("select").selectOption("ADMIN");
  await expect(page.getByText("Member role updated successfully.")).toBeVisible();
});

test("review queue resolves a task from the UI", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/review-queue");

  await expect(page.getByRole("heading", { name: "Review Queue" })).toBeVisible();
  await expect(page.getByText("AMZN MKTP")).toBeVisible();

  await page.locator("select").last().selectOption("OTHER");
  await page.getByRole("button", { name: "Resolve Task" }).click();

  await expect(page.getByText("Task resolved successfully.")).toBeVisible();
  await expect(page.getByText("No review tasks remaining")).toBeVisible();
});

test("reconciliation flow starts from the account card and opens real account details", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/reconciliation");

  await expect(page.getByRole("heading", { name: "Reconciliation" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Operating Checking" })).toBeVisible();

  await page.getByLabel("Opening Balance").fill("1000.00");
  await page.getByLabel("Statement Ending Balance").fill("1200.00");
  await page.getByRole("button", { name: "Start Reconciliation" }).click();

  await expect(page).toHaveURL(/\/reconciliation\/acct-operating\?month=2026-04$/);
  await expect(page.getByRole("heading", { name: "Operating Checking" }).first()).toBeVisible();
  await expect(page.getByText("Resolve outstanding review items, then complete reconciliation.")).toBeVisible();
  await expect(page.getByText("UNKNOWN VENDOR")).toBeVisible();
  await expect(page.getByRole("button", { name: "Complete Reconciliation" })).toBeVisible();
});

test("setup flow creates an account and imports a csv", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/setup");

  await expect(page.getByRole("heading", { name: "Import your first real activity" })).toBeVisible();

  await page.getByLabel("Account Name").fill("Payroll Checking");
  await page.getByLabel("Institution Name").fill("Northwind Bank");
  await page.getByRole("button", { name: "Create Account" }).click();

  await expect(page.getByText("Payroll Checking is ready for imports.")).toBeVisible();

  await page.getByLabel("Destination Account").selectOption({ label: "Payroll Checking" });
  await page
    .getByLabel("CSV File")
    .setInputFiles({
      name: "demo.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("id,date,amount,merchant\n1,2026-04-06,49.99,UNKNOWN VENDOR\n"),
    });
  await expect(page.getByText("CSV headers need attention")).toBeVisible();
  await expect(page.getByRole("button", { name: "Import Transactions" })).toBeDisabled();

  await page
    .getByLabel("CSV File")
    .setInputFiles({
      name: "demo.csv",
      mimeType: "text/csv",
      buffer: Buffer.from("id,date,merchant,memo,amount,mcc\n1,2026-04-06,UNKNOWN VENDOR,Needs review,49.99,5734\n"),
    });
  await expect(page.getByText("CSV shape looks ready")).toBeVisible();
  await page.getByRole("button", { name: "Import Transactions" }).click();

  await expect(page.getByText("Import completed successfully.")).toBeVisible();
  await expect(page.getByText("Last import Apr 24 · UNKNOWN VENDOR")).toBeVisible();
  await expect(page.getByText("REVIEW_REQUIRED · PREMIUM")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Payroll Checking activity" })).toBeVisible();
});

test("setup flow can bootstrap a sample workspace in-app", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/setup");

  await page.getByRole("button", { name: "Load sample workspace" }).click();

  await expect(page.getByText("Sample workspace loaded successfully.")).toBeVisible();
  await expect(page.getByText("Demo Operating Checking is ready for imports.")).toBeVisible();
  await expect(page.getByText("Last import Apr 24 · UNKNOWN VENDOR")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Demo Operating Checking activity" })).toBeVisible();
});

test("dashboard onboarding handoff points empty workspaces into guided setup", async ({ page }) => {
  await seedOrganization(page, "org-empty");
  await page.goto("/dashboard");

  await expect(page.getByRole("link", { name: "Load sample workspace" })).toBeVisible();
  await page.getByRole("link", { name: "Use your own CSV" }).click();

  await expect(page).toHaveURL(/\/setup\?welcome=1$/);
  await expect(page.getByText("You are in the guided setup lane")).toBeVisible();
});

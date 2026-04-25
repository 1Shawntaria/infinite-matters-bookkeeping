import { expect, test } from "@playwright/test";

const organizations = [
  {
    id: "org-primary",
    name: "Acme Books Demo",
    planTier: "GROWTH",
    timezone: "America/Los_Angeles",
  },
  {
    id: "org-secondary",
    name: "Sunrise Client Ops",
    planTier: "STARTER",
    timezone: "America/New_York",
  },
  {
    id: "org-empty",
    name: "Northwind New Books",
    planTier: "STARTER",
    timezone: "America/Chicago",
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

    if (url.pathname === "/api/auth/activity" && request.method() === "GET") {
      await fulfillJson(route, authActivity);
      return;
    }

    if (url.pathname === "/api/users/organizations" && request.method() === "GET") {
      await fulfillJson(route, organizations);
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

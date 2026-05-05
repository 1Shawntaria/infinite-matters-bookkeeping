import { expect, test } from "@playwright/test";

const organizations = [
  {
    id: "org-primary",
    name: "Acme Books Demo",
    planTier: "GROWTH",
    timezone: "America/Los_Angeles",
    invitationTtlDays: 7,
    closeMaterialityThreshold: 500,
    minimumCloseNotesRequired: 1,
    requireSignoffBeforeClose: true,
    minimumSignoffCount: 1,
    requireOwnerSignoffBeforeClose: false,
    requireTemplateCompletionBeforeClose: true,
    role: "OWNER",
  },
  {
    id: "org-secondary",
    name: "Sunrise Client Ops",
    planTier: "STARTER",
    timezone: "America/New_York",
    invitationTtlDays: 5,
    closeMaterialityThreshold: 250,
    minimumCloseNotesRequired: 1,
    requireSignoffBeforeClose: false,
    minimumSignoffCount: 0,
    requireOwnerSignoffBeforeClose: false,
    requireTemplateCompletionBeforeClose: false,
    role: "ADMIN",
  },
  {
    id: "org-empty",
    name: "Northwind New Books",
    planTier: "STARTER",
    timezone: "America/Chicago",
    invitationTtlDays: 7,
    closeMaterialityThreshold: 500,
    minimumCloseNotesRequired: 1,
    requireSignoffBeforeClose: true,
    minimumSignoffCount: 1,
    requireOwnerSignoffBeforeClose: false,
    requireTemplateCompletionBeforeClose: true,
    role: "MEMBER",
  },
];

function defaultWorkflowAttentionTasks() {
  return [
    {
      taskId: "close-follow-up-1",
      transactionId: null,
      notificationId: null,
      taskType: "CLOSE_ATTESTATION_FOLLOW_UP",
      priority: "HIGH",
      overdue: false,
      title: "Confirm month-end attestation for 2026-04",
      description:
        "Attestation routing or summary was updated, but the month still does not show a recorded confirmation from the assigned approver.",
      dueDate: "2026-04-30",
      assignedToUserId: "user-1",
      assignedToUserName: "Taylor Owner",
      merchant: null,
      amount: null,
      transactionDate: null,
      proposedCategory: null,
      confidenceScore: null,
      route: null,
      actionPath: "/close?month=2026-04",
      resolutionComment: "Approver updated after checklist completion.",
      acknowledgedByUserId: null,
      acknowledgedAt: null,
      snoozedUntil: null,
      resolvedByUserId: null,
      resolvedAt: null,
    },
    {
      taskId: "close-follow-up-2",
      transactionId: null,
      notificationId: null,
      taskType: "FORCE_CLOSE_REVIEW",
      priority: "HIGH",
      overdue: true,
      title: "Review force-close controls for 2026-02",
      description:
        "A recent month was force-closed. Revisit the close story and verify the override is fully documented and understood.",
      dueDate: "2026-04-23",
      assignedToUserId: "user-1",
      assignedToUserName: "Taylor Owner",
      merchant: null,
      amount: null,
      transactionDate: null,
      proposedCategory: null,
      confidenceScore: null,
      route: null,
      actionPath: "/close?month=2026-02",
      resolutionComment: "Late bank statement override requires owner review.",
      acknowledgedByUserId: null,
      acknowledgedAt: null,
      snoozedUntil: null,
      resolvedByUserId: null,
      resolvedAt: null,
    },
  ];
}

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
        recommendedActionSeverity: null,
        attentionTasks: [],
      },
      period: {
        closeReady: true,
        unreconciledAccountCount: 0,
        recommendedActionLabel: null,
        recommendedActionKey: null,
        recommendedActionPath: null,
        recommendedActionUrgency: null,
        recommendedActionSeverity: null,
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
        recommendedActionSeverity: null,
        attentionTasks: [],
      },
      period: {
        closeReady: true,
        unreconciledAccountCount: 0,
        recommendedActionLabel: null,
        recommendedActionKey: null,
        recommendedActionPath: null,
        recommendedActionUrgency: null,
        recommendedActionSeverity: null,
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
      severity: null,
      source: "PERIOD_CLOSE",
    },
    workflowInbox: {
      openCount: 3,
      overdueCount: 1,
      dueTodayCount: 1,
      highPriorityCount: 2,
      unassignedCount: 1,
      assignedToCurrentUserCount: 1,
      recommendedActionLabel: "Resolve open review tasks",
      recommendedActionKey: "REVIEW_HIGH_PRIORITY_TASKS",
      recommendedActionPath: "/workflows/inbox",
      recommendedActionUrgency: "HIGH",
      recommendedActionSeverity: null,
      attentionTasks: defaultWorkflowAttentionTasks(),
    },
    period: {
      closeReady: false,
      unreconciledAccountCount: 1,
      recommendedActionLabel: "Finish account reconciliations",
      recommendedActionKey: "FINISH_RECONCILIATIONS",
      recommendedActionPath: "/reconciliation",
      recommendedActionUrgency: "HIGH",
      recommendedActionSeverity: null,
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
    recentNotifications: [
      {
        id: "dashboard-notification-close-escalation",
        workflowTaskId: "close-follow-up-1",
        userId: "user-1",
        category: "WORKFLOW",
        channel: "IN_APP",
        status: "SENT",
        deliveryState: "DELIVERED",
        message:
          "Escalation (2026-04-29): attestation for 2026-04 still lacks final confirmation after repeated reminders. Owner/admin review is now required.",
        referenceType: "close_control_follow_up_escalation",
        referenceId: "close-follow-up-1",
        recipientEmail: "owner@acme.test",
        providerName: null,
        providerMessageId: null,
        attemptCount: 0,
        lastError: null,
        lastFailureCode: null,
        deadLetterResolutionStatus: null,
        deadLetterResolutionReasonCode: null,
        deadLetterResolutionNote: null,
        deadLetterResolvedAt: null,
        deadLetterResolvedByUserId: null,
        closeControlAcknowledgementNote: "Owner reviewed the escalation and queued the next touch for tomorrow.",
        closeControlAcknowledgedAt: "2026-04-29T15:10:00Z",
        closeControlAcknowledgedByUserId: "user-1",
        closeControlDisposition: "REVISIT_TOMORROW",
        closeControlNextTouchOn: "2026-04-30",
        closeControlResolutionNote: null,
        closeControlResolvedAt: null,
        closeControlResolvedByUserId: null,
        scheduledFor: "2026-04-29T15:00:00Z",
        lastAttemptedAt: null,
        sentAt: "2026-04-29T15:00:00Z",
        createdAt: "2026-04-29T15:00:00Z",
      },
    ],
  };
}

function workflowInboxSummary(
  organizationId: string,
  attentionTasksOverride?: Array<{ taskId: string; [key: string]: unknown }>
) {
  const attentionTasks = attentionTasksOverride ?? [];

  if (organizationId === "org-empty") {
    return {
      cardId: "workflow-inbox",
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
      recommendedActionSeverity: null,
      attentionTasks,
    };
  }

  if (organizationId === "org-secondary") {
    return {
      cardId: "workflow-inbox",
      openCount: 1,
      overdueCount: 0,
      dueTodayCount: 0,
      highPriorityCount: 0,
      unassignedCount: 1,
      assignedToCurrentUserCount: 0,
      recommendedActionLabel: "Open review queue",
      recommendedActionKey: "REVIEW_HIGH_PRIORITY_TASKS",
      recommendedActionPath: "/review-queue",
      recommendedActionUrgency: "NORMAL",
      recommendedActionSeverity: null,
      attentionTasks,
    };
  }

  return {
    cardId: "workflow-inbox",
    openCount: 4 + attentionTasks.length,
    overdueCount: 1,
    dueTodayCount: 1,
    highPriorityCount: attentionTasks.length,
    unassignedCount: 1,
    assignedToCurrentUserCount: 1,
    recommendedActionLabel: "Resolve open review tasks",
    recommendedActionKey: "REVIEW_HIGH_PRIORITY_TASKS",
    recommendedActionPath: "/review-queue",
    recommendedActionUrgency: "HIGH",
    recommendedActionSeverity: null,
    attentionTasks,
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

function transactions() {
  return [
    {
      transactionId: "txn-1",
      financialAccountId: "acct-operating",
      financialAccountName: "Operating Checking",
      transactionDate: "2026-04-03",
      postedDate: "2026-04-03",
      amount: 142.18,
      currency: "USD",
      merchant: "AMZN MKTP",
      memo: "office restock",
      mcc: "5942",
      proposedCategory: "OTHER",
      finalCategory: null,
      ledgerAccountCode: "6900",
      ledgerAccountName: "Miscellaneous Expense",
      route: "PREMIUM",
      confidenceScore: 0.83,
      status: "REVIEW_REQUIRED",
      sourceType: "CSV",
      importedAt: "2026-04-24T12:30:00Z",
    },
    {
      transactionId: "txn-2",
      financialAccountId: "acct-operating",
      financialAccountName: "Operating Checking",
      transactionDate: "2026-04-05",
      postedDate: "2026-04-05",
      amount: 89.0,
      currency: "USD",
      merchant: "CLOUDCO",
      memo: "monthly software",
      mcc: "5734",
      proposedCategory: "SOFTWARE",
      finalCategory: "SOFTWARE",
      ledgerAccountCode: "6100",
      ledgerAccountName: "Software Expense",
      route: "RULES",
      confidenceScore: 0.92,
      status: "POSTED",
      sourceType: "CSV",
      importedAt: "2026-04-24T12:00:00Z",
    },
  ];
}

function ledgerAccounts() {
  return [
    {
      accountCode: "1000",
      accountName: "Operating Checking",
      classification: "ASSET",
      sourceKinds: ["FINANCIAL_ACCOUNT", "LEDGER_ACTIVITY"],
      categoryHints: ["BANK"],
      activityEntryCount: 1,
      lastEntryDate: "2026-04-05",
      debitTotal: 0,
      creditTotal: 89.0,
    },
    {
      accountCode: "6100",
      accountName: "Software Expense",
      classification: "EXPENSE",
      sourceKinds: ["SYSTEM_CATEGORY", "LEDGER_ACTIVITY"],
      categoryHints: ["SOFTWARE"],
      activityEntryCount: 1,
      lastEntryDate: "2026-04-05",
      debitTotal: 89.0,
      creditTotal: 0,
    },
    {
      accountCode: "6900",
      accountName: "Miscellaneous Expense",
      classification: "EXPENSE",
      sourceKinds: ["SYSTEM_CATEGORY"],
      categoryHints: ["OTHER"],
      activityEntryCount: 0,
      lastEntryDate: null,
      debitTotal: 0,
      creditTotal: 0,
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
  let invitations = [
    {
      id: "invite-pending-1",
      organizationId: "org-primary",
      organizationName: "Acme Books Demo",
      email: "pending@acme.test",
      role: "MEMBER",
      status: "PENDING",
      expiresAt: "2026-05-01T12:00:00Z",
      acceptedAt: null,
      revokedAt: null,
      createdAt: "2026-04-24T12:40:00Z",
      invitedByUser: {
        id: "user-1",
        email: "owner@acme.test",
        fullName: "Acme Owner",
        createdAt: "2026-04-20T12:00:00Z",
      },
      acceptedByUser: null,
      inviteUrl: "http://localhost:3000/invite/token-existing",
      delivery: {
        notificationId: "notification-invite-1",
        category: "WORKSPACE_ACCESS",
        channel: "EMAIL",
        status: "PENDING",
        deliveryState: "PENDING",
        attemptCount: 0,
        lastError: null,
        lastFailureCode: null,
        providerName: null,
        providerMessageId: null,
        scheduledFor: "2026-04-24T12:40:00Z",
        lastAttemptedAt: null,
        sentAt: null,
        createdAt: "2026-04-24T12:40:00Z",
      },
    },
    {
      id: "invite-expired-1",
      organizationId: "org-primary",
      organizationName: "Acme Books Demo",
      email: "expired@acme.test",
      role: "ADMIN",
      status: "EXPIRED",
      expiresAt: "2026-04-20T12:00:00Z",
      acceptedAt: null,
      revokedAt: null,
      createdAt: "2026-04-10T12:40:00Z",
      invitedByUser: {
        id: "user-1",
        email: "owner@acme.test",
        fullName: "Acme Owner",
        createdAt: "2026-04-20T12:00:00Z",
      },
      acceptedByUser: null,
      inviteUrl: null,
      delivery: {
        notificationId: "notification-invite-expired-1",
        category: "WORKSPACE_ACCESS",
        channel: "EMAIL",
        status: "FAILED",
        deliveryState: "FAILED",
        attemptCount: 3,
        lastError: "Mailbox unavailable",
        lastFailureCode: "BOUNCED",
        providerName: "sendgrid",
        providerMessageId: "sg-expired-1",
        scheduledFor: "2026-04-20T12:00:00Z",
        lastAttemptedAt: "2026-04-20T12:05:00Z",
        sentAt: null,
        createdAt: "2026-04-10T12:40:00Z",
      },
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
      transactionId: "txn-1",
      financialAccountId: "acct-operating",
      financialAccountName: "Operating Checking",
      importedAt: "2026-04-24T12:30:00Z",
      transactionDate: "2026-04-03",
      amount: 142.18,
      merchant: "AMZN MKTP",
      proposedCategory: "OTHER",
      finalCategory: null,
      route: "PREMIUM",
      confidenceScore: 0.83,
      status: "REVIEW_REQUIRED",
    },
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
  let closeTemplateItems = [
    {
      id: "close-template-1",
      label: "Confirm payroll liabilities were tied out",
      guidance: "Use payroll provider reports and the liability rollforward before treating payroll as complete.",
      sortOrder: 1,
      createdAt: "2026-04-24T12:35:00Z",
    },
  ];
  const closePlaybookItems = [
    {
      id: "playbook-item-1",
      templateItemId: "close-template-1",
      month: "2026-04",
      label: "Confirm payroll liabilities were tied out",
      guidance: "Use payroll provider reports and the liability rollforward before treating payroll as complete.",
      sortOrder: 1,
      assignee: {
        id: "user-2",
        email: "ops@acme.test",
        fullName: "Ops Admin",
      },
      approver: {
        id: "user-1",
        email: "owner@acme.test",
        fullName: "Acme Owner",
      },
      completedAt: null,
      completedBy: null,
      approvedAt: null,
      approvedBy: null,
      createdAt: "2026-04-24T12:35:00Z",
      completed: false,
      approved: false,
      satisfied: false,
    },
  ];
  const accountingPeriods = [
    {
      id: "period-2026-04",
      periodStart: "2026-04-01",
      periodEnd: "2026-04-30",
      status: "OPEN",
      closeMethod: null,
      overrideReason: null,
      overrideApprovedByUserId: null,
      closedAt: null,
      createdAt: "2026-04-01T00:00:00Z",
    },
    {
      id: "period-2026-03",
      periodStart: "2026-03-01",
      periodEnd: "2026-03-31",
      status: "CLOSED",
      closeMethod: "CHECKLIST",
      overrideReason: null,
      overrideApprovedByUserId: null,
      closedAt: "2026-04-02T18:00:00Z",
      createdAt: "2026-03-01T00:00:00Z",
    },
  ];
  let ledgerEntries = [
    {
      journalEntryId: "journal-1",
      transactionId: "txn-history-1",
      entryDate: "2026-04-05",
      description: "Imported CLOUDCO transaction",
      entryType: "TRANSACTION_IMPORT",
      adjustmentReason: null,
      createdAt: "2026-04-24T12:00:00Z",
      lines: [
        {
          accountCode: "6100",
          accountName: "Software Expense",
          entrySide: "DEBIT",
          amount: 89.0,
        },
        {
          accountCode: "1000",
          accountName: "Operating Checking",
          entrySide: "CREDIT",
          amount: 89.0,
        },
      ],
    },
  ];
  let closeNotes = [
    {
      id: "close-note-1",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "PERIOD_CLOSE_NOTE_ADDED",
      entityType: "accounting_period",
      entityId: "2026-04",
      details: "Waiting on owner sign-off for final accrual review.",
      createdAt: "2026-04-24T13:05:00Z",
    },
  ];
  let closeSignoffs = [
    {
      id: "close-signoff-1",
      organizationId: "org-primary",
      actorUserId: "user-2",
      eventType: "PERIOD_CLOSE_SIGNED_OFF",
      entityType: "accounting_period",
      entityId: "2026-03",
      details: "March close approved after variance review.",
      createdAt: "2026-04-02T18:30:00Z",
    },
  ];
  let closeAttestation = {
    accountingPeriodId: "period-2026-04",
    month: "2026-04",
    closeOwner: {
      id: "user-2",
      email: "ops@acme.test",
      fullName: "Ops Admin",
    },
    closeApprover: {
      id: "user-1",
      email: "owner@acme.test",
      fullName: "Acme Owner",
    },
    summary:
      "April is materially complete, recurring close work is routed, and owner approval is the final remaining step.",
    attestedAt: null,
    attestedBy: null,
    attested: false,
  };
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
      id: "audit-close-4",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "PERIOD_FORCE_CLOSED",
      entityType: "accounting_period",
      entityId: "2026-02",
      details: "Force closed period 2026-02-01 through 2026-02-28 with reason: Banking statement arrived late.",
      createdAt: "2026-04-24T12:22:00Z",
    },
    {
      id: "audit-close-3",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "PERIOD_CLOSED",
      entityType: "accounting_period",
      entityId: "2026-03",
      details: "Closed period 2026-03-01 through 2026-03-31.",
      createdAt: "2026-04-24T12:20:00Z",
    },
    {
      id: "audit-close-2",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "PERIOD_CLOSE_ATTESTED",
      entityType: "accounting_period",
      entityId: "2026-03",
      details: "Close attestation confirmed by user user-1 for month 2026-03",
      createdAt: "2026-04-24T12:19:00Z",
    },
    {
      id: "audit-close-1",
      organizationId: "org-primary",
      actorUserId: "user-2",
      eventType: "PERIOD_CLOSE_ATTESTATION_UPDATED",
      entityType: "accounting_period",
      entityId: "2026-04",
      details: "Close attestation updated by user user-2 for month 2026-04",
      createdAt: "2026-04-24T12:18:00Z",
    },
    {
      id: "audit-0",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "ORGANIZATION_INVITATION_CREATED",
      entityType: "organization_invitation",
      entityId: "invite-pending-1",
      details: "Invitation created for pending@acme.test as MEMBER.",
      createdAt: "2026-04-24T12:15:00Z",
    },
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
    {
      id: "audit-2",
      organizationId: "org-primary",
      actorUserId: "user-1",
      eventType: "PERIOD_CLOSE_ATTEMPTED",
      entityType: "accounting_period",
      entityId: "2026-04",
      details: "Close readiness reviewed for April 2026.",
      createdAt: "2026-04-24T11:40:00Z",
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
    {
      id: "workflow-notification-4",
      workflowTaskId: "close-follow-up-1",
      userId: "user-1",
      category: "WORKFLOW",
      channel: "IN_APP",
      status: "SENT",
      deliveryState: "DELIVERED",
      message:
        "Escalation (2026-04-29): attestation for 2026-04 still lacks final confirmation after repeated reminders. Owner/admin review is now required.",
      referenceType: "close_control_follow_up_escalation",
      referenceId: "close-follow-up-1",
      recipientEmail: "owner@acme.test",
      providerName: null,
      providerMessageId: null,
      attemptCount: 0,
      lastError: null,
      lastFailureCode: null,
      deadLetterResolutionStatus: null,
      deadLetterResolutionReasonCode: null,
      deadLetterResolutionNote: null,
      deadLetterResolvedAt: null,
      deadLetterResolvedByUserId: null,
      closeControlAcknowledgementNote: "Owner reviewed the escalation and queued the next touch for tomorrow.",
      closeControlAcknowledgedAt: "2026-04-29T15:10:00Z",
      closeControlAcknowledgedByUserId: "user-1",
      closeControlDisposition: "REVISIT_TOMORROW",
      closeControlNextTouchOn: "2026-04-30",
      closeControlResolutionNote: null,
      closeControlResolvedAt: null,
      closeControlResolvedByUserId: null,
      scheduledFor: "2026-04-29T15:00:00Z",
      lastAttemptedAt: null,
      sentAt: "2026-04-29T15:00:00Z",
      createdAt: "2026-04-29T15:00:00Z",
    },
    {
      id: "workflow-notification-5",
      workflowTaskId: "close-follow-up-2",
      userId: "user-1",
      category: "WORKFLOW",
      channel: "IN_APP",
      status: "SENT",
      deliveryState: "DELIVERED",
      message:
        "Escalation (2026-04-29): force-close review for 2026-02 remains unresolved after repeated reminders. Owner/admin review is now required.",
      referenceType: "close_control_follow_up_escalation",
      referenceId: "close-follow-up-2",
      recipientEmail: "owner@acme.test",
      providerName: null,
      providerMessageId: null,
      attemptCount: 0,
      lastError: null,
      lastFailureCode: null,
      deadLetterResolutionStatus: null,
      deadLetterResolutionReasonCode: null,
      deadLetterResolutionNote: null,
      deadLetterResolvedAt: null,
      deadLetterResolvedByUserId: null,
      closeControlAcknowledgementNote: null,
      closeControlAcknowledgedAt: null,
      closeControlAcknowledgedByUserId: null,
      closeControlDisposition: null,
      closeControlNextTouchOn: null,
      closeControlResolutionNote: null,
      closeControlResolvedAt: null,
      closeControlResolvedByUserId: null,
      scheduledFor: "2026-04-29T15:20:00Z",
      lastAttemptedAt: null,
      sentAt: "2026-04-29T15:20:00Z",
      createdAt: "2026-04-29T15:20:00Z",
    },
  ];
  let workflowAttentionTasks = defaultWorkflowAttentionTasks();

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
      if (page.url().includes("/invite/")) {
        await route.fulfill({
          status: 401,
          contentType: "application/json",
          body: JSON.stringify({ message: "Not signed in" }),
        });
        return;
      }
      await fulfillJson(route, {
        id: "user-1",
        email: "owner@acme.test",
        fullName: "Acme Owner",
        createdAt: "2026-04-20T12:00:00Z",
      });
      return;
    }

    if (url.pathname.startsWith("/api/auth/invitations/") && request.method() === "GET") {
      const token = url.pathname.split("/")[4];
      const invitation = invitations.find((item) => item.inviteUrl?.endsWith(`/invite/${token}`));
      if (!invitation) {
        await fulfillJson(route, { message: "Invitation not found" }, 404);
        return;
      }
      await fulfillJson(route, invitation);
      return;
    }

    if (url.pathname.startsWith("/api/auth/invitations/") && url.pathname.endsWith("/accept") && request.method() === "POST") {
      const token = url.pathname.split("/")[4];
      const index = invitations.findIndex((item) => item.inviteUrl?.endsWith(`/invite/${token}`));
      if (index === -1) {
        await fulfillJson(route, { message: "Invitation not found" }, 404);
        return;
      }
      invitations[index] = {
        ...invitations[index],
        status: "ACCEPTED",
        acceptedAt: "2026-04-24T14:15:00Z",
        acceptedByUser: {
          id: "user-invitee",
          email: invitations[index].email,
          fullName: "Invited Teammate",
          createdAt: "2026-04-24T14:14:00Z",
        },
      };
      memberships = [
        ...memberships,
        {
          id: `membership-invite-${memberships.length + 1}`,
          organizationId: invitations[index].organizationId,
          user: {
            id: "user-invitee",
            email: invitations[index].email,
            fullName: "Invited Teammate",
            createdAt: "2026-04-24T14:14:00Z",
          },
          role: invitations[index].role,
          createdAt: "2026-04-24T14:15:00Z",
        },
      ];
      await fulfillJson(route, {
        user: {
          id: "user-invitee",
          email: invitations[index].email,
          fullName: "Invited Teammate",
        },
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
      await fulfillJson(
        route,
        workflowNotifications.filter((item) => {
          if (item.referenceType === "close_control_follow_up_escalation") {
            return !item.closeControlResolvedAt;
          }
          return item.id === "workflow-notification-1" || item.id === "workflow-notification-3";
        })
      );
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
      url.pathname.endsWith("/close-control-escalation/acknowledge") &&
      request.method() === "POST"
    ) {
      const notificationId = url.pathname.split("/")[4];
      const index = workflowNotifications.findIndex((item) => item.id === notificationId);
      const body = JSON.parse(request.postData() ?? "{}");
      workflowNotifications[index] = {
        ...workflowNotifications[index],
        closeControlAcknowledgedAt: "2026-04-29T15:10:00Z",
        closeControlAcknowledgementNote: body.note || "Escalation reviewed from notifications workspace",
        closeControlAcknowledgedByUserId: "user-1",
        closeControlDisposition: body.disposition || "WAITING_ON_APPROVER",
        closeControlNextTouchOn: body.nextTouchOn || null,
      };
      await fulfillJson(route, workflowNotifications[index]);
      return;
    }

    if (
      url.pathname.startsWith("/api/workflows/notifications/") &&
      url.pathname.endsWith("/close-control-escalation/resolve") &&
      request.method() === "POST"
    ) {
      const notificationId = url.pathname.split("/")[4];
      const index = workflowNotifications.findIndex((item) => item.id === notificationId);
      const body = JSON.parse(request.postData() ?? "{}");
      workflowNotifications[index] = {
        ...workflowNotifications[index],
        closeControlResolvedAt: "2026-04-29T15:12:00Z",
        closeControlResolutionNote: body.note || "Escalation resolved from notifications workspace",
        closeControlResolvedByUserId: "user-1",
        closeControlDisposition: body.disposition || workflowNotifications[index].closeControlDisposition,
        closeControlNextTouchOn:
          body.nextTouchOn || workflowNotifications[index].closeControlNextTouchOn || null,
      };
      await fulfillJson(route, workflowNotifications[index]);
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

    if (url.pathname === "/api/organizations/settings" && request.method() === "GET") {
      const targetOrganizationId = url.searchParams.get("organizationId") ?? "org-primary";
      const organization = organizations.find((item) => item.id === targetOrganizationId) ?? organizations[0];
      await fulfillJson(route, organization);
      return;
    }

    if (url.pathname === "/api/organizations/settings" && request.method() === "PATCH") {
      const targetOrganizationId = url.searchParams.get("organizationId") ?? "org-primary";
      const body = request.postDataJSON() as {
        name?: string;
        timezone?: string;
        invitationTtlDays?: number;
        closeMaterialityThreshold?: number;
        minimumCloseNotesRequired?: number;
        requireSignoffBeforeClose?: boolean;
        minimumSignoffCount?: number;
        requireOwnerSignoffBeforeClose?: boolean;
        requireTemplateCompletionBeforeClose?: boolean;
      };
      const index = organizations.findIndex((item) => item.id === targetOrganizationId);
      organizations[index] = {
        ...organizations[index],
        name: body.name ?? organizations[index].name,
        timezone: body.timezone ?? organizations[index].timezone,
        invitationTtlDays: body.invitationTtlDays ?? organizations[index].invitationTtlDays,
        closeMaterialityThreshold: body.closeMaterialityThreshold ?? organizations[index].closeMaterialityThreshold,
        minimumCloseNotesRequired: body.minimumCloseNotesRequired ?? organizations[index].minimumCloseNotesRequired,
        requireSignoffBeforeClose: body.requireSignoffBeforeClose ?? organizations[index].requireSignoffBeforeClose,
        minimumSignoffCount: body.minimumSignoffCount ?? organizations[index].minimumSignoffCount,
        requireOwnerSignoffBeforeClose: body.requireOwnerSignoffBeforeClose ?? organizations[index].requireOwnerSignoffBeforeClose,
        requireTemplateCompletionBeforeClose:
          body.requireTemplateCompletionBeforeClose ?? organizations[index].requireTemplateCompletionBeforeClose,
      };
      await fulfillJson(route, organizations[index]);
      return;
    }

    if (url.pathname === "/api/organizations/close-template-items" && request.method() === "GET") {
      await fulfillJson(route, closeTemplateItems);
      return;
    }

    if (url.pathname === "/api/organizations/close-template-items" && request.method() === "POST") {
      const body = request.postDataJSON() as { label: string; guidance: string };
      const item = {
        id: `close-template-${closeTemplateItems.length + 1}`,
        label: body.label,
        guidance: body.guidance,
        sortOrder: closeTemplateItems.length + 1,
        createdAt: "2026-04-24T12:55:00Z",
      };
      closeTemplateItems = [...closeTemplateItems, item];
      await fulfillJson(route, item);
      return;
    }

    if (url.pathname.startsWith("/api/organizations/close-template-items/") && request.method() === "DELETE") {
      const itemId = url.pathname.split("/")[4];
      closeTemplateItems = closeTemplateItems.filter((item) => item.id !== itemId);
      await fulfillJson(route, {});
      return;
    }

    if (url.pathname === "/api/periods/playbook" && request.method() === "GET") {
      const month = url.searchParams.get("month") ?? "2026-04";
      await fulfillJson(route, closePlaybookItems.filter((item) => item.month === month));
      return;
    }

    if (url.pathname.startsWith("/api/periods/playbook/") && url.pathname.endsWith("/assignment") && request.method() === "POST") {
      const templateItemId = url.pathname.split("/")[4];
      const body = request.postDataJSON() as { month: string; assigneeUserId: string | null; approverUserId: string | null };
      const assigneeMembership = memberships.find((membership) => membership.user.id === body.assigneeUserId) ?? null;
      const approverMembership = memberships.find((membership) => membership.user.id === body.approverUserId) ?? null;
      const index = closePlaybookItems.findIndex((item) => item.templateItemId === templateItemId && item.month === body.month);
      closePlaybookItems[index] = {
        ...closePlaybookItems[index],
        assignee: assigneeMembership
          ? {
              id: assigneeMembership.user.id,
              email: assigneeMembership.user.email,
              fullName: assigneeMembership.user.fullName,
            }
          : null,
        approver: approverMembership
          ? {
              id: approverMembership.user.id,
              email: approverMembership.user.email,
              fullName: approverMembership.user.fullName,
            }
          : null,
      };
      await fulfillJson(route, closePlaybookItems[index]);
      return;
    }

    if (url.pathname.startsWith("/api/periods/playbook/") && url.pathname.endsWith("/complete") && request.method() === "POST") {
      const templateItemId = url.pathname.split("/")[4];
      const body = request.postDataJSON() as { month: string; marked: boolean };
      const index = closePlaybookItems.findIndex((item) => item.templateItemId === templateItemId && item.month === body.month);
      closePlaybookItems[index] = {
        ...closePlaybookItems[index],
        completedAt: body.marked ? "2026-04-28T07:00:00Z" : null,
        completedBy: body.marked
          ? {
              id: "user-1",
              email: "owner@acme.test",
              fullName: "Acme Owner",
            }
          : null,
        approvedAt: body.marked ? closePlaybookItems[index].approvedAt : null,
        approvedBy: body.marked ? closePlaybookItems[index].approvedBy : null,
        completed: body.marked,
        approved: body.marked ? closePlaybookItems[index].approved : false,
        satisfied: body.marked ? closePlaybookItems[index].approved || closePlaybookItems[index].approver == null : false,
      };
      await fulfillJson(route, closePlaybookItems[index]);
      return;
    }

    if (url.pathname.startsWith("/api/periods/playbook/") && url.pathname.endsWith("/approve") && request.method() === "POST") {
      const templateItemId = url.pathname.split("/")[4];
      const body = request.postDataJSON() as { month: string; marked: boolean };
      const index = closePlaybookItems.findIndex((item) => item.templateItemId === templateItemId && item.month === body.month);
      closePlaybookItems[index] = {
        ...closePlaybookItems[index],
        approvedAt: body.marked ? "2026-04-28T07:05:00Z" : null,
        approvedBy: body.marked
          ? {
              id: "user-1",
              email: "owner@acme.test",
              fullName: "Acme Owner",
            }
          : null,
        approved: body.marked,
        satisfied: body.marked && closePlaybookItems[index].completed,
      };
      await fulfillJson(route, closePlaybookItems[index]);
      return;
    }

    if (url.pathname === "/api/users/invitations" && request.method() === "GET") {
      const targetOrganizationId = url.searchParams.get("organizationId") ?? "org-primary";
      await fulfillJson(
        route,
        invitations.filter((invitation) => invitation.organizationId === targetOrganizationId)
      );
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

    if (url.pathname.startsWith("/api/users/memberships/") && request.method() === "DELETE") {
      const membershipId = url.pathname.split("/")[4];
      const targetMembership = memberships.find((membership) => membership.id === membershipId);
      const ownerMemberships = memberships.filter((membership) => membership.role === "OWNER");

      if (targetMembership?.role === "OWNER" && ownerMemberships.length <= 1) {
        await route.fulfill({
          status: 400,
          contentType: "application/json",
          body: JSON.stringify({
            timestamp: "2026-04-24T14:00:00Z",
            status: 400,
            error: "Bad Request",
            message: "The last owner cannot be removed from the workspace",
            path: url.pathname,
            requestId: "req-membership-delete",
            details: [],
          }),
        });
        return;
      }

      memberships = memberships.filter((membership) => membership.id !== membershipId);
      await route.fulfill({ status: 200, body: "" });
      return;
    }

    if (url.pathname === "/api/users/invitations" && request.method() === "POST") {
      const body = request.postDataJSON() as {
        organizationId: string;
        email: string;
        role: string;
      };
      const createdInvitation = {
        id: `invite-${invitations.length + 1}`,
        organizationId: body.organizationId,
        organizationName:
          organizations.find((organization) => organization.id === body.organizationId)?.name ??
          "Acme Books Demo",
        email: body.email,
        role: body.role,
        status: "PENDING",
        expiresAt: "2026-05-01T12:00:00Z",
        acceptedAt: null,
        revokedAt: null,
        createdAt: "2026-04-24T13:20:00Z",
        invitedByUser: {
          id: "user-1",
          email: "owner@acme.test",
          fullName: "Acme Owner",
          createdAt: "2026-04-20T12:00:00Z",
        },
        acceptedByUser: null,
        inviteUrl: `http://localhost:3000/invite/token-${invitations.length + 1}`,
        delivery: {
          notificationId: `notification-invite-${invitations.length + 1}`,
          category: "WORKSPACE_ACCESS",
          channel: "EMAIL",
          status: "PENDING",
          deliveryState: "PENDING",
          attemptCount: 0,
          lastError: null,
          lastFailureCode: null,
          providerName: null,
          providerMessageId: null,
          scheduledFor: "2026-04-24T13:20:00Z",
          lastAttemptedAt: null,
          sentAt: null,
          createdAt: "2026-04-24T13:20:00Z",
        },
      };
      invitations = [createdInvitation, ...invitations];
      await fulfillJson(route, createdInvitation);
      return;
    }

    if (url.pathname.startsWith("/api/users/invitations/") && request.method() === "DELETE") {
      const invitationId = url.pathname.split("/")[4];
      const index = invitations.findIndex((invitation) => invitation.id === invitationId);
      invitations[index] = {
        ...invitations[index],
        status: "REVOKED",
        revokedAt: "2026-04-24T13:45:00Z",
      };
      await fulfillJson(route, invitations[index]);
      return;
    }

    if (url.pathname.startsWith("/api/users/invitations/") && url.pathname.endsWith("/resend") && request.method() === "POST") {
      const invitationId = url.pathname.split("/")[4];
      const index = invitations.findIndex((invitation) => invitation.id === invitationId);
      invitations[index] = {
        ...invitations[index],
        status: "PENDING",
        expiresAt: "2026-05-02T12:00:00Z",
        inviteUrl: `http://localhost:3000/invite/token-resend-${invitationId}`,
        delivery: {
          notificationId: `notification-${invitationId}-resent`,
          category: "WORKSPACE_ACCESS",
          channel: "EMAIL",
          status: "PENDING",
          deliveryState: "PENDING",
          attemptCount: 0,
          lastError: null,
          lastFailureCode: null,
          providerName: null,
          providerMessageId: null,
          scheduledFor: "2026-04-24T13:50:00Z",
          lastAttemptedAt: null,
          sentAt: null,
          createdAt: "2026-04-24T13:50:00Z",
        },
      };
      await fulfillJson(route, invitations[index]);
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

    if (url.pathname.startsWith("/api/accounts/") && request.method() === "PATCH") {
      const accountId = url.pathname.split("/")[3];
      const body = request.postDataJSON() as {
        name: string;
        institutionName: string;
        active: boolean;
      };
      const index = financialAccounts.findIndex((account) => account.id === accountId);
      financialAccounts[index] = {
        ...financialAccounts[index],
        name: body.name,
        institutionName: body.institutionName,
        active: body.active,
      };
      await fulfillJson(route, financialAccounts[index]);
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

    if (url.pathname === "/api/workflows/inbox" && request.method() === "GET") {
      await fulfillJson(
        route,
        workflowInboxSummary(
          organizationHeader,
          organizationHeader === "org-primary" ? workflowAttentionTasks : []
        )
      );
      return;
    }

    if (
      url.pathname.startsWith("/api/workflows/inbox/attention-tasks/") &&
      url.pathname.endsWith("/acknowledge") &&
      request.method() === "POST"
    ) {
      const taskId = url.pathname.split("/")[5];
      const index = workflowAttentionTasks.findIndex((task) => task.taskId === taskId);
      workflowAttentionTasks[index] = {
        ...workflowAttentionTasks[index],
        acknowledgedByUserId: "user-1",
        acknowledgedAt: "2026-04-24T12:26:00Z",
      };
      await fulfillJson(route, workflowAttentionTasks[index]);
      return;
    }

    if (
      url.pathname.startsWith("/api/workflows/inbox/attention-tasks/") &&
      url.pathname.endsWith("/resolve") &&
      request.method() === "POST"
    ) {
      const taskId = url.pathname.split("/")[5];
      workflowAttentionTasks = workflowAttentionTasks.filter((task) => task.taskId !== taskId);
      await fulfillJson(route, { ok: true });
      return;
    }

    if (url.pathname === "/api/reviews/tasks" && request.method() === "GET") {
      await fulfillJson(route, remainingReviewTasks);
      return;
    }

    if (url.pathname === "/api/transactions" && request.method() === "GET") {
      await fulfillJson(route, transactions());
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

    if (url.pathname === "/api/periods" && request.method() === "GET") {
      await fulfillJson(route, accountingPeriods);
      return;
    }

    if (url.pathname === "/api/periods/checklist" && request.method() === "GET") {
      await fulfillJson(route, {
        periodStart: "2026-04-01",
        periodEnd: "2026-04-30",
        closeReady: false,
        items: [
          {
            itemType: "WORKFLOW_REVIEW",
            label: "Review queue cleared",
            complete: remainingReviewTasks.length === 0,
            detail:
              remainingReviewTasks.length === 0
                ? "No review tasks are blocking close."
                : `${remainingReviewTasks.length} review task(s) still need a final category.`,
          },
          {
            itemType: "ACCOUNT_RECONCILIATION",
            label: "Account reconciliations complete",
            complete: false,
            detail: "Operating Checking still needs reconciliation before close.",
          },
        ],
      });
      return;
    }

    if (url.pathname === "/api/periods/notes" && request.method() === "GET") {
      const month = url.searchParams.get("month");
      await fulfillJson(
        route,
        month ? closeNotes.filter((note) => note.entityId === month) : closeNotes
      );
      return;
    }

    if (url.pathname === "/api/periods/notes" && request.method() === "POST") {
      const requestBody = JSON.parse(request.postData() || "{}");
      const createdNote = {
        id: `close-note-${closeNotes.length + 1}`,
        organizationId: organizationHeader,
        actorUserId: "user-1",
        eventType: "PERIOD_CLOSE_NOTE_ADDED",
        entityType: "accounting_period",
        entityId: requestBody.month,
        details: requestBody.note,
        createdAt: "2026-04-24T13:30:00Z",
      };
      closeNotes = [createdNote, ...closeNotes];
      await fulfillJson(route, createdNote);
      return;
    }

    if (url.pathname === "/api/periods/attestation" && request.method() === "GET") {
      await fulfillJson(route, closeAttestation);
      return;
    }

    if (url.pathname === "/api/periods/attestation" && request.method() === "POST") {
      const requestBody = JSON.parse(request.postData() || "{}");
      const closeOwner =
        memberships.find((membership) => membership.user.id === requestBody.closeOwnerUserId)?.user ?? null;
      const closeApprover =
        memberships.find((membership) => membership.user.id === requestBody.closeApproverUserId)?.user ?? null;

      closeAttestation = {
        ...closeAttestation,
        closeOwner: closeOwner
          ? {
              id: closeOwner.id,
              email: closeOwner.email,
              fullName: closeOwner.fullName,
            }
          : null,
        closeApprover: closeApprover
          ? {
              id: closeApprover.id,
              email: closeApprover.email,
              fullName: closeApprover.fullName,
            }
          : null,
        summary: requestBody.summary,
        attested: false,
        attestedAt: null,
        attestedBy: null,
      };
      await fulfillJson(route, closeAttestation);
      return;
    }

    if (url.pathname === "/api/periods/attestation/confirm" && request.method() === "POST") {
      closeAttestation = {
        ...closeAttestation,
        attested: true,
        attestedAt: "2026-04-24T13:45:00Z",
        attestedBy: {
          id: "user-1",
          email: "owner@acme.test",
          fullName: "Acme Owner",
        },
      };
      await fulfillJson(route, closeAttestation);
      return;
    }

    if (url.pathname === "/api/periods/signoffs" && request.method() === "GET") {
      const month = url.searchParams.get("month");
      await fulfillJson(
        route,
        month ? closeSignoffs.filter((signoff) => signoff.entityId === month) : closeSignoffs
      );
      return;
    }

    if (url.pathname === "/api/periods/signoffs" && request.method() === "POST") {
      const requestBody = JSON.parse(request.postData() || "{}");
      const createdSignoff = {
        id: `close-signoff-${closeSignoffs.length + 1}`,
        organizationId: organizationHeader,
        actorUserId: "user-1",
        eventType: "PERIOD_CLOSE_SIGNED_OFF",
        entityType: "accounting_period",
        entityId: requestBody.month,
        details: requestBody.note,
        createdAt: "2026-04-24T13:35:00Z",
      };
      closeSignoffs = [createdSignoff, ...closeSignoffs];
      await fulfillJson(route, createdSignoff);
      return;
    }

    if (url.pathname === "/api/periods/close" && request.method() === "POST") {
      const requestBody = JSON.parse(request.postData() || "{}");
      const matchingPeriod = accountingPeriods.find(
        (period) => period.periodStart.slice(0, 7) === requestBody.month
      );
      if (matchingPeriod) {
        matchingPeriod.status = "CLOSED";
        matchingPeriod.closeMethod = "CHECKLIST";
        matchingPeriod.closedAt = "2026-04-24T13:15:00Z";
      }
      await fulfillJson(route, matchingPeriod ?? accountingPeriods[0]);
      return;
    }

    if (url.pathname === "/api/periods/force-close" && request.method() === "POST") {
      const requestBody = JSON.parse(request.postData() || "{}");
      const matchingPeriod = accountingPeriods.find(
        (period) => period.periodStart.slice(0, 7) === requestBody.month
      );
      if (matchingPeriod) {
        matchingPeriod.status = "CLOSED";
        matchingPeriod.closeMethod = "OVERRIDE";
        matchingPeriod.overrideReason = requestBody.reason;
        matchingPeriod.closedAt = "2026-04-24T13:20:00Z";
      }
      await fulfillJson(route, matchingPeriod ?? accountingPeriods[0]);
      return;
    }

    if (url.pathname === "/api/ledger/entries" && request.method() === "GET") {
      await fulfillJson(route, ledgerEntries);
      return;
    }

    if (url.pathname === "/api/ledger/accounts" && request.method() === "GET") {
      await fulfillJson(route, ledgerAccounts());
      return;
    }

    if (url.pathname === "/api/adjustments" && request.method() === "POST") {
      const requestBody = JSON.parse(request.postData() || "{}");
      ledgerEntries = [
        {
          journalEntryId: "journal-adjustment-1",
          transactionId: null,
          entryDate: requestBody.entryDate,
          description: requestBody.description,
          entryType: "ADJUSTMENT",
          adjustmentReason: requestBody.adjustmentReason,
          createdAt: "2026-04-24T13:25:00Z",
          lines: requestBody.lines,
        },
        ...ledgerEntries,
      ];
      await fulfillJson(route, ledgerEntries[0]);
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
  await expect(page.locator("span").filter({ hasText: "Scheduled" }).first()).toBeVisible();
  await expect(page.getByRole("heading", { name: "Escalated attestation is queued for tomorrow" })).toBeVisible();
  await expect(page.getByText("Owner/admin review is already on record for 2026-04. The workflow is intentionally parked until Apr 30, 2026: Owner reviewed the escalation and queued the next touch for tomorrow.")).toBeVisible();
  await expect(page.getByText("Planned next touch: Apr 30, 2026")).toBeVisible();
  await expect(page.getByText("This suggestion follows the attestation due date so the approver handoff stays on track without creating extra churn.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Review the latest override month" })).toBeVisible();
  await page.getByRole("link", { name: "Open follow-up month" }).click();
  await expect(page).toHaveURL(/\/close\?month=2026-04/);
  await expect(page.locator('input[type="month"]')).toHaveValue("2026-04");
  await page.goto("/dashboard");
  await page.getByRole("link", { name: "Resume override review on Apr 23" }).click();
  await expect(page).toHaveURL(/\/close\?month=2026-02/);
  await expect(page.locator('input[type="month"]')).toHaveValue("2026-02");
  await page.goto("/dashboard");
  await expect(page.getByText("Session and trust").filter({ visible: true })).toBeVisible();
  await expect(page.getByText("Acme Owner").filter({ visible: true })).toBeVisible();
});

test("workspace switching reloads dashboard data for the selected organization", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/dashboard");

  const workspaceSelect = page.locator("select:visible").first();

  await expect(workspaceSelect).toHaveValue("org-primary");
  await expect(page.getByText("$15234.12")).toBeVisible();
  await expect(page.getByText("Operating Checking").first()).toBeVisible();
  await expect(page.getByText("CLOUDCO").first()).toBeVisible();
  await expect(page.getByText("AUTH LOGIN SUCCEEDED")).toBeVisible();

  await workspaceSelect.selectOption("org-secondary");
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByText("$8450.55")).toBeVisible();
});

test("activity page shows merged operational timeline and filter views", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/activity");

  await expect(page.getByRole("heading", { name: "Activity" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Attestation control quality" })).toBeVisible();
  await expect(page.getByText("Force-close activity needs review")).toBeVisible();
  await expect(page.getByText("Attestation plans updated")).toBeVisible();
  await expect(page.getByText("Attestations confirmed")).toBeVisible();
  await expect(page.getByText("Review the latest override month")).toBeVisible();
  await expect(page.getByText("Planned next touch: Apr 23, 2026")).toBeVisible();
  await expect(page.getByText("Auth Login Succeeded")).toBeVisible();
  await expect(page.getByText("Organization Member Added").last()).toBeVisible();
  await expect(page.getByText("Operating Checking: CLOUDCO")).toBeVisible();

  await page.getByRole("link", { name: "Resume override review on Apr 23" }).click();
  await expect(page).toHaveURL(/\/close\?month=2026-02/);
  await expect(page.locator('input[type="month"]')).toHaveValue("2026-02");
  await page.goto("/activity");

  await page.getByRole("button", { name: "Revoke session" }).nth(1).click();
  await expect(page.getByText("Session revoked. The activity feed has been refreshed.")).toBeVisible();
  await expect(page.getByText("REVOKED", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "Audit" }).click();
  await expect(page.getByText("Period Close Attempted")).toBeVisible();
  await expect(page.getByText("Close readiness reviewed for April 2026.")).toBeVisible();

  await page.getByRole("button", { name: "Access" }).click();
  await expect(page.getByText("Organization Invitation Created").last()).toBeVisible();
  await expect(page.getByText("Invitation created for pending@acme.test as MEMBER.")).toBeVisible();
});

test("notifications inbox merges auth and workflow delivery signals", async ({ page }) => {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const suggestedDateIso = tomorrow.toISOString().slice(0, 10);
  const suggestedDateLabel = tomorrow.toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

  await seedOrganization(page);
  await page.goto("/notifications");

  await expect(page.getByRole("heading", { name: "Notifications" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Escalated close controls" })).toBeVisible();
  await expect(page.getByText("Escalated attestation follow-up")).toBeVisible();
  await expect(page.getByText("Focus month 2026-04")).toBeVisible();
  await expect(page.getByText("Owner reviewed the escalation and queued the next touch for tomorrow.")).toBeVisible();
  await expect(page.getByText("Next touch Apr 30, 2026")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Workflow follow-up" })).toBeVisible();
  await expect(page.getByText("Confirm month-end attestation for 2026-04")).toBeVisible();
  await expect(page.getByText("Review force-close controls for 2026-02")).toBeVisible();
  await expect(page.getByText("Assigned Taylor Owner")).toHaveCount(2);
  await expect(page.getByText("Your password was changed successfully.")).toBeVisible();
  await expect(page.getByText("Review queue escalation could not be delivered.").first()).toBeVisible();
  await expect(page.getByText("Mailbox unavailable").first()).toBeVisible();
  await expect(page.locator("span").filter({ hasText: "Attention" }).first()).toBeVisible();
  await expect(page.getByText("Monthly close escalation entered dead-letter handling.").first()).toBeVisible();

  await page.getByRole("link", { name: "Revisit attestation on Apr 30" }).first().click();
  await expect(page).toHaveURL(/\/close\?month=2026-04/);
  await expect(page.locator('input[type="month"]')).toHaveValue("2026-04");
  await page.goto("/notifications");

  const forceCloseEscalationCard = page.locator("div.rounded-lg").filter({
    has: page.getByText("Escalated force-close review"),
  });
  await forceCloseEscalationCard.locator("textarea").fill("Owner reviewed the escalation and queued the next touch for tomorrow.");
  await forceCloseEscalationCard.locator("select").selectOption("REVISIT_TOMORROW");
  await expect(forceCloseEscalationCard.getByText(`Suggested ${suggestedDateLabel}`)).toBeVisible();
  await expect(forceCloseEscalationCard.getByText("Suggested for the next review day because this override review is already overdue.")).toBeVisible();
  await forceCloseEscalationCard.getByRole("button", { name: "Use suggestion" }).click();
  await expect(forceCloseEscalationCard.locator('input[type="date"]')).toHaveValue(suggestedDateIso);
  await forceCloseEscalationCard.getByRole("button", { name: "Save review note" }).click();
  await expect(page.getByText("Escalated close-control review acknowledged.")).toBeVisible();
  await expect(page.locator("p").filter({ hasText: "Revisit tomorrow" }).first()).toBeVisible();
  await expect(
    page.locator("p").filter({
      hasText: "Owner reviewed the escalation and queued the next touch for tomorrow.",
    }).first()
  ).toBeVisible();
  await expect(page.getByText(`Next touch ${suggestedDateLabel}`)).toBeVisible();

  await page.getByRole("button", { name: "Resolve escalation" }).last().click();
  await expect(page.getByText("Escalated close-control review resolved.")).toBeVisible();
  await expect(page.getByText("Escalated force-close review")).toHaveCount(0);

  await page.getByRole("link", { name: "Revisit attestation on Apr 30" }).first().click();
  await expect(page).toHaveURL(/\/close\?month=2026-04/);
  await expect(page.locator('input[type="month"]')).toHaveValue("2026-04");
  await page.goto("/notifications");

  await page.getByRole("button", { name: "Mark reviewed" }).first().click();
  await expect(page.getByText("Workflow follow-up marked reviewed.")).toBeVisible();
  await expect(page.locator("span").filter({ hasText: "Reviewed" }).first()).toBeVisible();

  await page.getByRole("button", { name: "Clear signal" }).click();
  await expect(page.getByText("Workflow follow-up cleared.")).toBeVisible();
  await expect(page.getByText("Review force-close controls for 2026-02")).toHaveCount(0);

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

test("exceptions workspace groups blockers that still stand in the way of close", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/exceptions");

  await expect(page.getByRole("heading", { name: "Exceptions" })).toBeVisible();
  await expect(page.getByText("Active blockers")).toBeVisible();
  await expect(page.locator("span").filter({ hasText: "Scheduled" }).first()).toBeVisible();
  await expect(page.getByText("Escalated close-control risk is active")).toBeVisible();
  await expect(page.getByText("Escalated attestation follow-up").first()).toBeVisible();
  await expect(page.getByText("Revisit attestation on Apr 30").first()).toBeVisible();
  await expect(page.getByText("Next touch Apr 30, 2026").first()).toBeVisible();
  await expect(page.getByText("Review AMZN MKTP")).toBeVisible();
  await expect(page.getByText("Operating Checking")).toBeVisible();
  await expect(page.getByText("Review queue escalation could not be delivered.").first()).toBeVisible();
  await expect(page.getByText("Account reconciliations complete", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "Open close workspace" }).click();
  await expect(page).toHaveURL(/\/close$/);
});

test("run close workspace sequences the month-end workflow", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/run-close");

  await expect(page.getByRole("heading", { name: "Run Close" })).toBeVisible();
  await expect(page.getByText("Close runbook progress")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Resolve review queue decisions" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Finish reconciliations" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Clear delivery and exception risk" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Post adjustments and leave notes" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Approve and close the month" })).toBeVisible();
  await page.getByRole("link", { name: "Open review queue" }).first().click();
  await expect(page).toHaveURL(/\/review-queue$/);
});

test("readiness workspace gives an owner-level pre-close summary", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/readiness");

  await expect(page.getByRole("heading", { name: "Close Readiness" })).toBeVisible();
  await expect(page.locator("span").filter({ hasText: "Scheduled" }).first()).toBeVisible();
  await expect(page.getByText("Wait before closing")).toBeVisible();
  await expect(page.getByText("Readiness score")).toBeVisible();
  await expect(page.getByText("Final pre-close review")).toBeVisible();
  await expect(page.getByText("Outstanding review tasks")).toBeVisible();
  await expect(page.getByText("Month attestation", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Escalation reviewed, revisit tomorrow" })).toBeVisible();
  await expect(page.getByText("Owner/admin review is already on record for 2026-04. The workflow is intentionally parked until Apr 30, 2026: Owner reviewed the escalation and queued the next touch for tomorrow.")).toBeVisible();
  await expect(page.getByText("Planned next touch: Apr 30, 2026")).toBeVisible();
  await expect(page.getByText("This suggestion follows the attestation due date so the approver handoff stays on track without creating extra churn.")).toBeVisible();
  await page.getByRole("link", { name: "Open follow-up month" }).click();
  await expect(page).toHaveURL(/\/close\?month=2026-04/);
  await expect(page.locator('input[type="month"]')).toHaveValue("2026-04");
  await page.goto("/readiness");
  await page.getByRole("link", { name: "Resume guided close" }).click();
  await expect(page).toHaveURL(/\/run-close$/);
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

  const opsAdminCard = page.locator("div.rounded-lg").filter({
    has: page.getByText("Ops Admin"),
  });
  await opsAdminCard.getByRole("button", { name: "Remove access" }).click();
  await expect(page.getByText("Ops Admin no longer has workspace access.")).toBeVisible();
  await expect(page.getByText("ops@acme.test")).toHaveCount(0);

  await page.getByPlaceholder("new.hire@company.com").fill("invitee@acme.test");
  await page.getByRole("button", { name: "Create invite" }).click();
  await expect(page.getByText("Invitation created successfully and queued for delivery.")).toBeVisible();
  await expect(page.getByText(/http:\/\/localhost:3000\/invite\/token-/)).toBeVisible();
  const createdInviteCard = page.locator("div.rounded-lg").filter({
    has: page.getByText("invitee@acme.test"),
  });
  await expect(createdInviteCard.getByText("Delivery queued")).toBeVisible();

  const expiredInviteCard = page.locator("div.rounded-lg").filter({
    has: page.getByText("expired@acme.test"),
  });
  await expect(expiredInviteCard.getByText("Delivery failed")).toBeVisible();
  await expiredInviteCard.getByRole("button", { name: "Resend invite" }).click();
  await expect(page.getByText("Invitation for expired@acme.test was resent and expiry was renewed.")).toBeVisible();
  await expect(page.getByText("http://localhost:3000/invite/token-resend-invite-expired-1")).toBeVisible();

  const pendingInviteCard = page.locator("div.rounded-lg").filter({
    has: page.getByText("pending@acme.test"),
  });
  await pendingInviteCard.getByRole("button", { name: "Revoke invite" }).click();
  await expect(page.getByText("Invitation for pending@acme.test has been revoked.")).toBeVisible();
});

test("settings page lets operators update workspace profile and invitation policy", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/settings");

  await expect(page.getByRole("heading", { name: "Settings" })).toBeVisible();

  await page.getByLabel("Workspace name").fill("Acme Finance Ops");
  await page.getByLabel("Timezone").fill("America/New_York");
  await page.getByRole("button", { name: "Save profile" }).click();

  await expect(page.getByText("Workspace profile updated for Acme Finance Ops.")).toBeVisible();
  await expect(page.getByLabel("Workspace name")).toHaveValue("Acme Finance Ops");
  await expect(page.getByLabel("Timezone")).toHaveValue("America/New_York");

  const ttlInput = page.getByLabel("Invitation expiry window (days)");
  await expect(ttlInput).toHaveValue("7");
  await ttlInput.fill("14");
  await page.getByLabel("Materiality threshold ($)").fill("750");
  await page.getByLabel("Minimum close notes required").fill("2");
  await page.getByLabel("Minimum signoffs required").fill("2");
  await page.getByLabel("An owner sign-off is required for standard close").check();
  await page.getByRole("button", { name: "Save policy" }).click();

  await expect(page.getByText("Close policy updated: 14-day invites, $750 materiality, 2 required close note(s), and 2 required signoff(s).")).toBeVisible();
  await expect(ttlInput).toHaveValue("14");
  await expect(page.getByLabel("Materiality threshold ($)")).toHaveValue("750");
  await expect(page.getByLabel("Minimum close notes required")).toHaveValue("2");
  await expect(page.getByLabel("Minimum signoffs required")).toHaveValue("2");

  await page.getByLabel("Template item label").fill("Tie out deferred revenue schedule");
  await page.getByLabel("Operator guidance").fill("Use the customer contract summary and rollforward before clearing deferred revenue.");
  await page.getByRole("button", { name: "Add playbook item" }).click();
  await expect(page.getByText('Close playbook item "Tie out deferred revenue schedule" added.')).toBeVisible();
  await expect(page.getByText(/^2\. Tie out deferred revenue schedule$/)).toBeVisible();
});

test("invite page creates an account and accepts a workspace invitation", async ({ page }) => {
  await mockApi(page);
  await page.goto("/invite/token-existing");

  await expect(page.getByRole("heading", { name: "You've been invited into a workspace." })).toBeVisible();
  await expect(page.getByText("Acme Books Demo")).toBeVisible();

  await page.getByLabel("Full name").fill("Invited Teammate");
  await page.getByLabel("Password").fill("password123");
  await page.getByRole("button", { name: "Create account and accept" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
});

test("review queue resolves a task from the UI", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/review-queue");

  await expect(page.getByRole("heading", { name: "Review Queue" })).toBeVisible();
  await expect(page.getByText("AMZN MKTP")).toBeVisible();

  await page.getByRole("button", { name: "Resolve Task" }).click();

  await expect(page.getByText("Task resolved successfully.")).toBeVisible();
  await expect(page.getByText("No review tasks remaining")).toBeVisible();
});

test("close workspace exposes checklist, ledger, and adjustment controls", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/close");

  await expect(page.getByRole("heading", { name: "Close Management" })).toBeVisible();
  await expect(page.getByText("Confirm payroll liabilities were tied out")).toBeVisible();
  await expect(page.getByRole("heading", { name: "What the next reviewer needs to know" })).toBeVisible();
  await expect(page.getByText("Review queue cleared", { exact: true })).toBeVisible();
  await expect(page.getByText("Account reconciliations complete", { exact: true })).toBeVisible();
  await expect(page.getByText("Imported CLOUDCO transaction")).toBeVisible();
  await page.getByRole("button", { name: "Mark complete" }).click();
  await expect(page.getByText("Recurring close playbook item completed.")).toBeVisible();
  await page.getByRole("button", { name: "Approve item" }).click();
  await expect(page.getByText("Recurring close playbook item approved.")).toBeVisible();
  await page.getByRole("button", { name: "Accrued expense" }).click();
  await expect(page.getByLabel("Description")).toHaveValue("Accrue month-end expense");
  await expect(page.getByLabel("Adjustment reason")).toHaveValue(
    "Recognize an unpaid month-end expense in the current period."
  );
  await page.getByPlaceholder("April accrual package").fill("April accrual pack");
  await page.getByRole("button", { name: "Save draft" }).click();
  await expect(page.getByText('Saved draft "April accrual pack".')).toBeVisible();
  await expect(page.getByText("April accrual pack", { exact: true })).toBeVisible();
  await page.getByPlaceholder("Monthly travel accrual").fill("Travel accrual template");
  await page.getByRole("button", { name: "Save template" }).click();
  await expect(page.getByText('Saved template "Travel accrual template".')).toBeVisible();
  await expect(page.getByText("Travel accrual template", { exact: true })).toBeVisible();
  await expect(page.getByText("Waiting on owner sign-off for final accrual review.")).toBeVisible();
  await expect(page.getByText("Month attestation", { exact: true })).toBeVisible();
  await expect(page.getByText("Acme Owner still needs to confirm the month-level attestation before standard close can proceed.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Close period", exact: true })).toBeDisabled();
  await page
    .getByLabel("Attestation summary for 2026-04")
    .fill("April is materially complete, remaining judgment items are documented, and leadership accepts the final handoff posture.");
  await page.getByRole("button", { name: "Save attestation plan" }).click();
  await expect(page.getByText("Month-end attestation plan saved.")).toBeVisible();
  await page.getByRole("button", { name: "Confirm month attestation" }).click();
  await expect(page.getByText("Month-end attestation confirmed.")).toBeVisible();
  await expect(page.getByText("Attestation confirmed", { exact: true })).toBeVisible();
  await expect(page.getByText("Month-level attestation is confirmed, so standard close can proceed once the rest of the controls are clear.")).toBeVisible();
  await page
    .getByLabel("Close note for 2026-04")
    .fill("Controller approved a temporary estimate for the travel accrual.");
  await page.getByRole("button", { name: "Save close note" }).click();
  await expect(page.getByText("Close note saved to the month-end history.")).toBeVisible();
  await expect(
    page.getByText("Controller approved a temporary estimate for the travel accrual.")
  ).toBeVisible();
  await page.locator('input[placeholder="Amount"]').nth(0).fill("250.00");
  await page.locator('input[placeholder="Amount"]').nth(1).fill("250.00");
  await page.getByRole("button", { name: "Post adjustment" }).click();
  await expect(page.getByText("Adjustment posted successfully and reflected in the ledger.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Posted adjustments for 2026-04" })).toBeVisible();
  await expect(page.getByText("Accrue month-end expense").last()).toBeVisible();
  await expect(page.getByRole("button", { name: "Reuse as template" })).toBeVisible();
  await page
    .getByLabel("Approval summary for 2026-04")
    .fill("April close reviewed, reconciliations cleared, and adjustments approved.");
  await page.getByRole("button", { name: "Approve close" }).click();
  await expect(page.getByText("Close sign-off recorded for the selected month.")).toBeVisible();
  await expect(
    page.getByText("April close reviewed, reconciliations cleared, and adjustments approved.")
  ).toBeVisible();
  await expect(page.getByText("You have already signed off")).toBeVisible();
  await expect(page.getByRole("button", { name: "Post adjustment" })).toBeVisible();
});

test("close workspace rejects the same person as attestation owner and approver", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/close");

  await page.getByLabel("Close owner").selectOption("user-1");
  await page.getByLabel("Approver").last().selectOption("user-1");
  await page
    .getByLabel("Attestation summary for 2026-04")
    .fill("April is materially complete and awaiting an independent final certification.");
  await page.getByRole("button", { name: "Save attestation plan" }).click();

  await expect(
    page.getByText("Choose a different approver so the month-end attestation keeps a true four-eyes review.")
  ).toBeVisible();
  await expect(page.getByText("Attestation not saved")).toBeVisible();
});

test("transactions workspace helps investigate imported and posted activity", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/transactions");

  await expect(page.getByRole("heading", { name: "Transactions" })).toBeVisible();
  await expect(page.getByText("AMZN MKTP")).toBeVisible();
  await expect(page.getByText("Account: Operating Checking").first()).toBeVisible();
  await expect(page.getByText("office restock")).toBeVisible();
  await page.getByRole("link", { name: "Open activity trail" }).first().click();
  await expect(page).toHaveURL(/\/activity\?lane=IMPORT&entityId=txn-1/);
  await expect(page.getByText("Focused activity trace")).toBeVisible();
  await expect(page.getByText("Operating Checking: AMZN MKTP").first()).toBeVisible();

  await page.goto("/transactions");

  await page.getByLabel("Status").selectOption("POSTED");
  await expect(page.getByText("CLOUDCO")).toBeVisible();
  await expect(page.getByText("AMZN MKTP")).toHaveCount(0);
});

test("accounting workspace ties account references back to transactions and close", async ({ page }) => {
  await seedOrganization(page);
  await page.goto("/accounting?accountCode=6100");

  await expect(page.getByRole("heading", { name: "Chart of Accounts" })).toBeVisible();
  await expect(page.getByLabel("Search code, name, or category hint")).toHaveValue("6100");
  await expect(page.getByText("6100 · Software Expense")).toBeVisible();
  await expect(page.getByText("SYSTEM_CATEGORY, LEDGER_ACTIVITY")).toBeVisible();
  await expect(page.getByRole("link", { name: "View related transactions" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Use in close" })).toBeVisible();

  await page.getByLabel("Account name").fill("Reserve Checking");
  await page.getByLabel("Institution").fill("Treasury Bank");
  await page.getByRole("checkbox", { name: "Active" }).uncheck();
  await page.getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByText("Reserve Checking updated successfully.")).toBeVisible();
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

test("member close access stays read-only in the UI", async ({ page }) => {
  await seedOrganization(page, "org-empty");
  await page.goto("/close");

  await expect(page.getByText("Read-only close access")).toBeVisible();
  await expect(page.getByRole("button", { name: "Close period", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Force close period", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Post adjustment" })).toBeDisabled();
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

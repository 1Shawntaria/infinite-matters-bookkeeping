package com.infinitematters.bookkeeping.workflows;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {
    List<WorkflowTask> findByOrganizationIdAndStatusOrderByCreatedAtAsc(UUID organizationId, WorkflowTaskStatus status);

    List<WorkflowTask> findByOrganizationIdAndAssignedToUserIdAndStatusOrderByCreatedAtAsc(UUID organizationId,
                                                                                           UUID assignedToUserId,
                                                                                           WorkflowTaskStatus status);

    Optional<WorkflowTask> findByTransactionIdAndStatus(UUID transactionId, WorkflowTaskStatus status);

    Optional<WorkflowTask> findByNotificationIdAndTaskTypeAndStatus(UUID notificationId,
                                                                    WorkflowTaskType taskType,
                                                                    WorkflowTaskStatus status);

    List<WorkflowTask> findByOrganizationIdAndTaskTypeAndStatusOrderByCreatedAtAsc(UUID organizationId,
                                                                                   WorkflowTaskType taskType,
                                                                                   WorkflowTaskStatus status);

    List<WorkflowTask> findByOrganizationIdAndTaskTypeOrderByCreatedAtAsc(UUID organizationId,
                                                                          WorkflowTaskType taskType);

    @Query("""
            select t from WorkflowTask t
            where t.organization.id = :organizationId
              and t.status = com.infinitematters.bookkeeping.workflows.WorkflowTaskStatus.OPEN
              and (
                    (t.transaction is not null and t.transaction.transactionDate between :periodStart and :periodEnd)
                    or t.taskType = com.infinitematters.bookkeeping.workflows.WorkflowTaskType.RECONCILIATION_EXCEPTION
                  )
            order by t.createdAt asc
            """)
    List<WorkflowTask> findOpenTasksForPeriod(@Param("organizationId") UUID organizationId,
                                              @Param("periodStart") LocalDate periodStart,
                                              @Param("periodEnd") LocalDate periodEnd);

    Optional<WorkflowTask> findByOrganizationIdAndTaskTypeAndStatusAndDescription(UUID organizationId,
                                                                                  WorkflowTaskType taskType,
                                                                                  WorkflowTaskStatus status,
                                                                                  String description);
}

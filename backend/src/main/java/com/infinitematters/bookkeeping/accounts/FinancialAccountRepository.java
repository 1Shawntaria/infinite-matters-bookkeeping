package com.infinitematters.bookkeeping.accounts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {
    List<FinancialAccount> findByOrganizationId(UUID organizationId);
}

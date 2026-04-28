package com.infinitematters.bookkeeping.accounts;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.OrganizationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FinancialAccountService {
    private final FinancialAccountRepository repository;
    private final OrganizationService organizationService;

    public FinancialAccountService(FinancialAccountRepository repository, OrganizationService organizationService) {
        this.repository = repository;
        this.organizationService = organizationService;
    }

    public FinancialAccount create(UUID organizationId, String name, AccountType accountType,
                                   String institutionName, String currency) {
        Organization organization = organizationService.get(organizationId);
        FinancialAccount account = new FinancialAccount();
        account.setOrganization(organization);
        account.setName(name);
        account.setAccountType(accountType);
        account.setInstitutionName(institutionName);
        account.setCurrency(currency);
        account.setActive(true);
        return repository.save(account);
    }

    public List<FinancialAccount> listByOrganization(UUID organizationId) {
        return repository.findByOrganizationId(organizationId);
    }

    public FinancialAccount get(UUID accountId) {
        return repository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown financial account: " + accountId));
    }

    public FinancialAccount update(UUID accountId,
                                   UUID organizationId,
                                   String name,
                                   String institutionName,
                                   boolean active) {
        FinancialAccount account = get(accountId);
        if (!account.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException(
                    "Financial account does not belong to organization " + organizationId);
        }

        account.setName(name);
        account.setInstitutionName(institutionName);
        account.setActive(active);
        return repository.save(account);
    }
}

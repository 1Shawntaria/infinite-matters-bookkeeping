alter table reconciliation_sessions
    add column variance_amount numeric(19, 2);

alter table reconciliation_sessions
    add column notes varchar(1000);

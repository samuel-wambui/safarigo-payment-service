alter table payments
    add column if not exists idempotency_key varchar(120);

create unique index if not exists uk_payments_provider_idempotency_key
    on payments (provider, idempotency_key);

create index if not exists idx_payments_idempotency_key
    on payments (idempotency_key);

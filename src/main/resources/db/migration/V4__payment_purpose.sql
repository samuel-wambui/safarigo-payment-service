alter table payments
    add column if not exists payment_purpose varchar(160);

create index if not exists idx_payments_payment_purpose
    on payments (payment_purpose);

create table if not exists payments (
    id uuid primary key,
    user_id uuid not null,
    booking_id uuid not null,
    paystack_customer_email varchar(254) not null,
    amount_minor bigint not null,
    currency varchar(3) not null,
    provider varchar(40) not null,
    provider_reference varchar(100) not null,
    access_code varchar(120),
    authorization_url varchar(1000),
    status varchar(40) not null,
    created_by_actor varchar(120),
    paid_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uk_payments_provider_reference unique (provider_reference)
);

create index if not exists idx_payments_user_id on payments (user_id);
create index if not exists idx_payments_booking_id on payments (booking_id);
create index if not exists idx_payments_status on payments (status);
create index if not exists idx_payments_created_at on payments (created_at);

create table if not exists provider_transactions (
    id uuid primary key,
    payment_id uuid not null,
    provider varchar(40) not null,
    provider_reference varchar(100) not null,
    provider_transaction_id varchar(120),
    status varchar(60),
    gateway_response varchar(500),
    amount_minor bigint,
    currency varchar(3),
    raw_payload text,
    received_at timestamp with time zone not null
);

create index if not exists idx_provider_transactions_payment_id on provider_transactions (payment_id);
create index if not exists idx_provider_transactions_reference on provider_transactions (provider_reference);

create table if not exists payment_webhook_events (
    id uuid primary key,
    provider varchar(40) not null,
    event_type varchar(120),
    provider_reference varchar(100),
    signature varchar(256) not null,
    payload_hash varchar(128) not null,
    payload text not null,
    valid_signature boolean not null,
    processed_at timestamp with time zone,
    received_at timestamp with time zone not null,
    constraint uk_payment_webhook_events_payload_hash unique (payload_hash)
);

create index if not exists idx_payment_webhook_events_reference on payment_webhook_events (provider_reference);
create index if not exists idx_payment_webhook_events_received_at on payment_webhook_events (received_at);

create table if not exists refunds (
    id uuid primary key,
    payment_id uuid not null,
    user_id uuid not null,
    amount_minor bigint not null,
    reason varchar(500),
    status varchar(40) not null,
    provider_reference varchar(120),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_refunds_payment_id on refunds (payment_id);
create index if not exists idx_refunds_user_id on refunds (user_id);
create index if not exists idx_refunds_status on refunds (status);

create table if not exists payment_outbox_events (
    id uuid primary key,
    aggregate_type varchar(80) not null,
    aggregate_id uuid not null,
    event_type varchar(120) not null,
    payload text not null,
    status varchar(40) not null,
    attempts integer not null default 0,
    created_at timestamp with time zone not null,
    processed_at timestamp with time zone
);

create index if not exists idx_payment_outbox_events_status on payment_outbox_events (status);
create index if not exists idx_payment_outbox_events_aggregate on payment_outbox_events (aggregate_type, aggregate_id);

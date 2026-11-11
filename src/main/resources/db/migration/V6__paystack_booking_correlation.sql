alter table payments
    add column if not exists conversation_id varchar(200);

update payments
set conversation_id = 'legacy:' || booking_id::text
where conversation_id is null;

alter table payments
    alter column conversation_id set not null;

alter table payments
    add column if not exists notification_url varchar(1000);

create index if not exists idx_payments_conversation_id
    on payments (conversation_id);

alter table payment_webhook_events
    alter column signature type varchar(1000);

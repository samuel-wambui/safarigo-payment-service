drop index if exists idx_mpesa_config_offering_type;

alter table mpesa_config_entity
    rename column offering_type to payment_purpose;

create index if not exists idx_mpesa_config_payment_purpose
    on mpesa_config_entity (payment_purpose);

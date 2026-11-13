alter table payments
    add column if not exists source_amount_minor bigint;

alter table payments
    add column if not exists source_currency varchar(3);

update payments
set source_amount_minor = amount_minor,
    source_currency = currency
where source_amount_minor is null
   or source_currency is null;

alter table payments
    alter column source_amount_minor set not null;

alter table payments
    alter column source_currency set not null;

alter table payments
    add column if not exists fx_rate numeric(19, 8);

alter table payments
    add column if not exists fx_rate_date date;

alter table payments
    add column if not exists fx_provider varchar(50);

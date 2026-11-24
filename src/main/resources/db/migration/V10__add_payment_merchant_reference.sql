alter table payments
add column merchant_reference varchar(100);

update payments
set merchant_reference = 'LEGACY' || id
where merchant_reference is null;

alter table payments
alter column merchant_reference set not null;

alter table payments
add constraint uq_payments_merchant_reference
unique (merchant_reference);

alter table notifications
    add column provider_name varchar(128);

alter table notifications
    add column provider_message_id varchar(255);

create table user_list_notification_push_schedules (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  user_id varchar(8) not null references users (id),
  list_id varchar(8) not null references lists (id),
  is_completed boolean not null default false,
  push_date timestamptz not null,
  primary key (id)
);

create table user_list_notification_push_schedule_user_notifications (
  user_list_notification_push_schedule_id varchar(8) not null references user_list_notification_push_schedules (id),
  user_notification_id varchar(8) not null references user_notifications (id),
  primary key (user_list_notification_push_schedule_id, user_notification_id)
);

create table user_list_notification_push_preferences (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  user_id varchar(8) not null references users (id),
  list_id varchar(8) not null references lists (id),
  max_notification_pushes_per_day integer not null,
  primary key (id),
  unique (user_id, list_id)
);

create table user_attributes (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  parent_id varchar(8) not null references users (id),
  attribute varchar(128) not null,
  value text not null,
  primary key (id)
);

update lists set body = null where body = '';
update list_items set title = null where title = '';
update list_items set body = null where body = '';

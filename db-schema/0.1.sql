create table user_notification_push_schedules (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  user_id varchar(8) not null references users (id),
  is_completed boolean not null default false,
  push_date timestamptz not null,
  primary key (id)
);

create table user_notification_push_schedules_pushes (
  user_notification_push_schedule_id varchar(8) not null references user_notification_push_schedules (id),
  user_notification_push_id varchar(8) not null references user_notification_pushes (id),
  primary key (user_notification_push_schedule_id, user_notification_push_id)
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

create table ids (
  value character varying(8) not null,
  primary key (value)
);

create table images (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean default true,
  is_internal boolean default true,
  url varchar(256) not null,
  secure_url varchar(256) not null,
  mime_type varchar(32) not null,
  original_filename varchar(1024),
  original_image_id varchar(8) references images (id),
  size integer,
  width integer,
  height integer,
  transform_type varchar(32),
  transform_width integer,
  transform_height integer,
  original_url character varying(1024),
  preview_data bytea,
  primary key (id)
);

create table users (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  name varchar(32) null,
  email varchar(512) null,
  tz varchar(64) null,
  image_id varchar(8) null references images (id),
  primary key (id)
);

create table lists (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  user_id varchar(8) references users (id),
  title text not null,
  body text null,
  primary key (id)
);

create table list_comments (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  parent_id varchar(8) references lists (id),
  user_id varchar(8) references users (id),
  body text null,
  primary key (id)
);

create table list_users (
  list_id varchar(8) not null references lists (id),
  user_id varchar(8) not null references users (id),
  unique (list_id, user_id)
);

create table list_items (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  list_id varchar(8) references lists (id),
  user_id varchar(8) references users (id),
  title text null,
  body text null,
  primary key (id)
);

create table list_item_images (
  image_id varchar(8) not null references images (id),
  list_item_id varchar(8) not null references list_items (id),
  primary key (image_id, list_item_id)
);

create table list_item_comments (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  parent_id varchar(8) references list_items (id),
  user_id varchar(8) references users (id),
  body text null,
  primary key (id)
);

create table list_item_votes (
  id varchar(8) not null references ids (value),
  creation_date timestamptz not null default now(),
  last_updated_date timestamptz not null default now(),
  is_active boolean not null default true,
  parent_id varchar(8) references list_items (id),
  user_id varchar(8) references users (id),
  primary key (id),
  unique (parent_id, user_id)
);

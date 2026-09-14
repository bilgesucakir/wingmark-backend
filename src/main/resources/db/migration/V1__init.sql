create table users (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    username varchar(30) not null,
    first_name varchar(100),
    last_name varchar(100),
    profile_picture varchar(500),
    favorite_species_id uuid,
    role varchar(20) not null default 'USER',
    email_verified boolean not null default false,
    last_login_at timestamp with time zone,
    constraint uq_users_email unique (email),
    constraint uq_users_username unique (username)
);

create table user_settings (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    user_id uuid not null,
    unit_preference varchar(20) not null default 'METRIC',
    locale varchar(20),
    constraint uq_user_settings_user unique (user_id),
    constraint fk_user_settings_user foreign key (user_id) references users (id) on delete cascade
);

create table refresh_tokens (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    user_id uuid not null,
    token_hash varchar(255) not null,
    device_info varchar(255),
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint uq_refresh_tokens_hash unique (token_hash),
    constraint fk_refresh_tokens_user foreign key (user_id) references users (id) on delete cascade
);
create index idx_refresh_tokens_user on refresh_tokens (user_id);

create table password_reset_tokens (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    user_id uuid not null,
    token_hash varchar(255) not null,
    expires_at timestamp with time zone not null,
    used_at timestamp with time zone,
    constraint uq_password_reset_tokens_hash unique (token_hash),
    constraint fk_password_reset_tokens_user foreign key (user_id) references users (id) on delete cascade
);

create table species (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    common_name varchar(150) not null,
    scientific_name varchar(150) not null,
    family varchar(150),
    taxonomic_order varchar(150),
    description text,
    lifespan varchar(100),
    diet text,
    habitat varchar(255),
    size_description varchar(255),
    conservation_status varchar(100),
    native_range varchar(255),
    constraint uq_species_scientific_name unique (scientific_name)
);

create table species_images (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    species_id uuid not null,
    life_stage varchar(20) not null,
    gender varchar(20) not null,
    image_url varchar(500) not null,
    caption varchar(255),
    constraint fk_species_images_species foreign key (species_id) references species (id) on delete cascade
);
create index idx_species_images_species on species_images (species_id);

create table bird_logs (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    user_id uuid not null,
    species_id uuid,
    species_status varchar(20),
    pet boolean not null default false,
    custom_name varchar(150),
    life_stage varchar(20) not null default 'UNKNOWN',
    gender varchar(20) not null default 'UNKNOWN',
    photo_url varchar(500),
    note text,
    latitude double precision not null,
    longitude double precision not null,
    location_name varchar(255),
    observed_at timestamp with time zone not null,
    visibility varchar(20) not null default 'PRIVATE',
    detected_species_id uuid,
    detection_confidence double precision,
    constraint fk_bird_logs_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_bird_logs_species foreign key (species_id) references species (id) on delete set null
);
create index idx_bird_logs_user on bird_logs (user_id);
create index idx_bird_logs_user_location on bird_logs (user_id, latitude, longitude);

create table badges (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    name varchar(150) not null,
    description text,
    icon varchar(255),
    criteria_type varchar(30) not null,
    criteria_value integer not null,
    criteria_metadata text,
    tier varchar(20)
);

create table user_badges (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    user_id uuid not null,
    badge_id uuid not null,
    earned_at timestamp with time zone,
    progress integer,
    constraint uq_user_badges_user_badge unique (user_id, badge_id),
    constraint fk_user_badges_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_user_badges_badge foreign key (badge_id) references badges (id) on delete cascade
);

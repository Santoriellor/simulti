
    create table game_results (
        enemies_killed integer,
        final_score integer,
        wave_reached integer,
        created_at timestamp(6) with time zone,
        id uuid not null,
        player_id uuid,
        session_id uuid,
        primary key (id)
    );

    create table game_session_players (
        game_session_id uuid not null,
        player_id uuid not null
    );

    create table game_sessions (
        wave integer,
        ended_at timestamp(6) with time zone,
        started_at timestamp(6) with time zone,
        host_id uuid,
        id uuid not null,
        room_id varchar(255),
        status varchar(255),
        primary key (id)
    );

    create table leaderboard (
        games_played integer,
        high_score integer,
        total_score bigint,
        updated_at timestamp(6) with time zone,
        id uuid not null,
        user_id uuid,
        primary key (id)
    );

    create table players (
        games_played INT DEFAULT 0,
        high_score INT DEFAULT 0,
        total_score BIGINT DEFAULT 0,
        id uuid not null,
        user_id uuid not null unique,
        primary key (id)
    );

    create table sessions (
        created_at timestamp(6) with time zone,
        expires_at timestamp(6) with time zone,
        id uuid not null,
        user_id uuid not null,
        token varchar(255) not null unique,
        primary key (id)
    );

    create table users (
        created_at TIMESTAMP WITH TIME ZONE,
        id uuid not null,
        email varchar(255) not null unique,
        password varchar(255) not null,
        username varchar(255) not null unique,
        primary key (id)
    );

    alter table if exists game_results 
       add constraint FKmk5qsbx4hefgk6hftjgbxrl3 
       foreign key (player_id) 
       references users;

    alter table if exists game_results 
       add constraint FK8l5o1yvnkb7vbf0wgr31fi0j2 
       foreign key (session_id) 
       references game_sessions;

    alter table if exists game_session_players 
       add constraint FKbuyomjt0nvftw6i4xk4ujq8vs 
       foreign key (game_session_id) 
       references game_sessions;

    alter table if exists game_sessions 
       add constraint FKr7n3r4ci9aq22dnhm5oswsxsi 
       foreign key (host_id) 
       references players;

    alter table if exists leaderboard 
       add constraint FKkrvli8v2u3owoa54i6hc2l0bu 
       foreign key (user_id) 
       references users;

    alter table if exists players 
       add constraint FK3rfv9832bif6rea5edetib8it 
       foreign key (user_id) 
       references users;

    alter table if exists sessions 
       add constraint FKruie73rneumyyd1bgo6qw8vjt 
       foreign key (user_id) 
       references users;

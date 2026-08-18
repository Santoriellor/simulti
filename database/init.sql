-- Schema of record for space-multi.
--
-- Applied by the postgres image from /docker-entrypoint-initdb.d ONLY when the
-- data directory is empty, i.e. on a brand-new environment. It does not run
-- against an existing volume, so editing it never mutates a live database.
--
-- This file is authoritative because application.yml sets ddl-auto: validate.
-- Hibernate will no longer create or alter anything: if an entity and this
-- schema disagree, the backend refuses to start. Change an entity => change
-- this file in the same commit, and hand-write the ALTER for environments that
-- already exist.
--
-- Dumped from the live production database 2026-08-18 (pg_dump --schema-only),
-- not hand-written, because ddl-auto: update had drifted the previous version of
-- this file far out of date - it was missing game_rooms, game_room_players and
-- players entirely.
--
-- Deliberately EXCLUDED: game_sessions and game_session_players. Both exist in
-- production but are backed by no entity - leftovers from when GameSession was
-- renamed to GameRoom. ddl-auto: update never drops anything, which is how dead
-- schema accumulates unnoticed. A fresh environment should not recreate them.

CREATE TABLE public.game_results (
    enemies_killed integer,
    final_score integer,
    wave_reached integer,
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    player_id uuid,
    session_id uuid
);

CREATE TABLE public.game_room_players (
    game_room_id uuid NOT NULL,
    player_id uuid NOT NULL
);

CREATE TABLE public.game_rooms (
    room_id uuid NOT NULL,
    ended_at timestamp(6) with time zone,
    max_player integer,
    room_name character varying(255),
    started_at timestamp(6) with time zone,
    status character varying(255),
    wave integer,
    host_id uuid
);

CREATE TABLE public.leaderboard (
    games_played integer,
    high_score integer,
    total_score bigint,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    user_id uuid
);

CREATE TABLE public.players (
    games_played integer DEFAULT 0,
    high_score integer DEFAULT 0,
    total_score bigint DEFAULT 0,
    id uuid NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE public.sessions (
    created_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token character varying(255) NOT NULL
);

CREATE TABLE public.users (
    created_at timestamp with time zone,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    username character varying(255) NOT NULL
);

ALTER TABLE ONLY public.game_results
    ADD CONSTRAINT game_results_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.game_rooms
    ADD CONSTRAINT game_rooms_pkey PRIMARY KEY (room_id);

ALTER TABLE ONLY public.leaderboard
    ADD CONSTRAINT leaderboard_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.players
    ADD CONSTRAINT players_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT sessions_token_key UNIQUE (token);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);

ALTER TABLE ONLY public.players
    ADD CONSTRAINT fk3rfv9832bif6rea5edetib8it FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.game_room_players
    ADD CONSTRAINT fkf5eg4r0hfyuhqhyub87rrmr0c FOREIGN KEY (game_room_id) REFERENCES public.game_rooms(room_id);

ALTER TABLE ONLY public.game_results
    ADD CONSTRAINT fkh31uecrpcpjnme0u21ij7j387 FOREIGN KEY (session_id) REFERENCES public.game_rooms(room_id);

ALTER TABLE ONLY public.leaderboard
    ADD CONSTRAINT fkkrvli8v2u3owoa54i6hc2l0bu FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.game_results
    ADD CONSTRAINT fkmk5qsbx4hefgk6hftjgbxrl3 FOREIGN KEY (player_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.sessions
    ADD CONSTRAINT fkruie73rneumyyd1bgo6qw8vjt FOREIGN KEY (user_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.game_rooms
    ADD CONSTRAINT fksm06igutc1bkr42chwj03i1xg FOREIGN KEY (host_id) REFERENCES public.players(id);

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE camera (
    id              BIGSERIAL PRIMARY KEY,
    slug            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    location        VARCHAR(16)  NOT NULL,
    sub_stream_path VARCHAR(64)  UNIQUE,
    sort_order      INT          NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE access_group (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE group_member (
    group_id BIGINT NOT NULL REFERENCES access_group (id) ON DELETE CASCADE,
    user_id  BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE group_camera (
    id           BIGSERIAL PRIMARY KEY,
    group_id     BIGINT  NOT NULL REFERENCES access_group (id) ON DELETE CASCADE,
    camera_id    BIGINT  NOT NULL REFERENCES camera (id) ON DELETE CASCADE,
    can_live     BOOLEAN NOT NULL DEFAULT TRUE,
    can_playback BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (group_id, camera_id)
);

CREATE TABLE audit_event (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64) NOT NULL,
    camera_slug VARCHAR(64),
    action      VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip          VARCHAR(64)
);
CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at DESC);

-- Seed: the five planned cameras (slug = MediaMTX path name)
INSERT INTO camera (slug, name, location, sub_stream_path, sort_order) VALUES
    ('home-entrance', 'Building entrance', 'HOME',   'home-entrance-sub', 10),
    ('home-parking',  'Parking',           'HOME',   'home-parking-sub',  20),
    ('home-door',     'Apartment door',    'HOME',   'home-door-sub',     30),
    ('studio-door',   'Studio door',       'STUDIO', 'studio-door-sub',   40),
    ('studio-inside', 'Studio inside',     'STUDIO', 'studio-inside-sub', 50);

-- Seed: the two sharing groups
INSERT INTO access_group (name) VALUES ('Neighbours'), ('Partners');

INSERT INTO group_camera (group_id, camera_id, can_live, can_playback)
SELECT g.id, c.id, TRUE, TRUE
FROM access_group g JOIN camera c
  ON (g.name = 'Neighbours' AND c.slug IN ('home-entrance', 'home-parking'))
  OR (g.name = 'Partners'   AND c.slug IN ('studio-door', 'studio-inside'));

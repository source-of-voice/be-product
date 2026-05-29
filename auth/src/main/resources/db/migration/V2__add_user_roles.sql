INSERT INTO roles (id, name, description)
VALUES
    (1, 'UNLOGGED', 'unlogged'),
    (2, 'USER', 'user'),
    (3, 'ADMIN', 'admin'),
    (4, 'REVIEWER', 'reviewer')
    ON CONFLICT (id) DO UPDATE
                            SET
                                name = EXCLUDED.name,
                            description = EXCLUDED.description;
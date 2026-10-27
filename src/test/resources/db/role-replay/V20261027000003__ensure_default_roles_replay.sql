-- Preserve role identifiers and existing user references. Reject ambiguous mappings.
DROP PROCEDURE IF EXISTS ensure_default_roles_v20261027000002;
DELIMITER $$
CREATE PROCEDURE ensure_default_roles_v20261027000002()
BEGIN
    IF EXISTS (
        SELECT 1 FROM roles
        WHERE (id = 1 AND BINARY name <> BINARY 'SUPER_ADMIN')
           OR (id = 2 AND BINARY name <> BINARY 'ADMIN')
           OR (id = 3 AND BINARY name <> BINARY 'USER')
           OR (name = 'SUPER_ADMIN' AND id <> 1)
           OR (name = 'ADMIN' AND id <> 2)
           OR (name = 'USER' AND id <> 3)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default role identifier or name conflicts with existing data';
    END IF;

    INSERT INTO roles (id, name, description)
    SELECT 1, 'SUPER_ADMIN', '최고 권한 관리자'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 1);
    INSERT INTO roles (id, name, description)
    SELECT 2, 'ADMIN', '관리자'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 2);
    INSERT INTO roles (id, name, description)
    SELECT 3, 'USER', '사용자'
    WHERE NOT EXISTS (SELECT 1 FROM roles WHERE id = 3);

    UPDATE roles SET description = '최고 권한 관리자' WHERE id = 1;
    UPDATE roles SET description = '관리자' WHERE id = 2;
    UPDATE roles SET description = '사용자' WHERE id = 3;
END$$
DELIMITER ;
CALL ensure_default_roles_v20261027000002();
DROP PROCEDURE ensure_default_roles_v20261027000002;

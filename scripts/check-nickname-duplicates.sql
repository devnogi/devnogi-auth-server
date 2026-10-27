-- Run against the target auth schema before deployment. Any returned row blocks migration.
SELECT nickname, COUNT(*) AS duplicate_count
FROM users
WHERE is_verified = FALSE
GROUP BY nickname
HAVING COUNT(*) > 1;

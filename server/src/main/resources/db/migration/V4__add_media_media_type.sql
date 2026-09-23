SET @media_type_column_exists = (
    SELECT COUNT(1) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'media_files' AND column_name = 'media_type'
);
SET @media_type_column_sql = IF(
    @media_type_column_exists = 0,
    'ALTER TABLE media_files ADD COLUMN media_type VARCHAR(16) NOT NULL DEFAULT ''VIDEO'', ALGORITHM=INSTANT, LOCK=NONE',
    'SELECT 1'
);
PREPARE media_type_column_statement FROM @media_type_column_sql;
EXECUTE media_type_column_statement;
DEALLOCATE PREPARE media_type_column_statement;

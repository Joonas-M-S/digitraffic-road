-- UTF-8 nfd
UPDATE "schema_version" SET "description" = REPLACE("description", 'a?', 'a'), "script" = REPLACE("script", 'a?', 'a');
UPDATE "schema_version" SET "description" = REPLACE("description", 'o?', 'o'), "script" = REPLACE("script", 'o?', 'o');
-- UTF-8 nfc
UPDATE "schema_version" SET "description" = REPLACE("description", '�', 'a'), "script" = REPLACE("script", '�', 'a');
UPDATE "schema_version" SET "description" = REPLACE("description", '�', 'o'), "script" = REPLACE("script", '�', 'o');
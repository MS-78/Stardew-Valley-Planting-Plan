-- Migration: Convert single-season crops to multi-season support
-- Run this script if you already have an existing database from before the seasons change.
-- For fresh installations, this is NOT needed (schema.sql + init.sql handle it).
--
-- Usage:
--   docker exec -i stardew-mysql mysql -uroot -p<your_password> stardew_planner < migration-v2-multi-season.sql

-- Step 1: Rename column season → seasons and widen to VARCHAR(50)
ALTER TABLE crops CHANGE COLUMN season seasons VARCHAR(50) NOT NULL COMMENT 'Comma-separated seasons: spring,summer,fall';

-- Step 2: Update multi-season crops (corn and sunflower) to have both seasons
UPDATE crops SET seasons = 'summer,fall' WHERE id = 'crop-u02' AND name = '玉米';
UPDATE crops SET seasons = 'summer,fall' WHERE id = 'crop-u10' AND name = '向日葵';

-- Step 3: Remove the duplicate fall entries for corn and sunflower
DELETE FROM crops WHERE id = 'crop-f10' AND name = '玉米';
DELETE FROM crops WHERE id = 'crop-f11' AND name = '向日葵';

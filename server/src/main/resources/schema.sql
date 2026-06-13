SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Schema for Stardew Valley Planting Planner
-- No CREATE DATABASE here (handled by Docker MYSQL_DATABASE env var or manual setup)

CREATE TABLE IF NOT EXISTS crops (
    id              VARCHAR(36)     PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    seasons         VARCHAR(50)     NOT NULL COMMENT 'Comma-separated seasons: spring,summer,fall',
    is_walkable     TINYINT(1)      NOT NULL DEFAULT 1,
    seed_source     VARCHAR(100)    NOT NULL,
    seed_price      INT             NOT NULL,
    growth_days     INT             NOT NULL,
    can_regrow      TINYINT(1)      NOT NULL DEFAULT 0,
    regrow_interval INT             DEFAULT NULL,
    base_sell_price INT             NOT NULL,
    artisan_sell_price INT          DEFAULT NULL,
    silver_price    INT             DEFAULT NULL COMMENT 'Silver star sell price',
    gold_price      INT             DEFAULT NULL COMMENT 'Gold star sell price',
    iridium_price   INT             DEFAULT NULL COMMENT 'Iridium star sell price',
    base_energy     INT             DEFAULT NULL COMMENT 'Base energy recovery',
    base_health     INT             DEFAULT NULL COMMENT 'Base health recovery',
    silver_energy   INT             DEFAULT NULL COMMENT 'Silver star energy recovery',
    silver_health   INT             DEFAULT NULL COMMENT 'Silver star health recovery',
    gold_energy     INT             DEFAULT NULL COMMENT 'Gold star energy recovery',
    gold_health     INT             DEFAULT NULL COMMENT 'Gold star health recovery',
    iridium_energy  INT             DEFAULT NULL COMMENT 'Iridium star energy recovery',
    iridium_health  INT             DEFAULT NULL COMMENT 'Iridium star health recovery',
    farmer_mult     DECIMAL(5,2)    DEFAULT 1.00,
    agri_mult       DECIMAL(5,2)    DEFAULT 1.00,
    icon            VARCHAR(255)    DEFAULT NULL,
    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Crop data table';

CREATE TABLE IF NOT EXISTS tools (
    id                  VARCHAR(36)     PRIMARY KEY,
    name                VARCHAR(100)    NOT NULL,
    type                VARCHAR(20)     NOT NULL COMMENT 'sprinkler/scarecrow',
    coverage_offsets    JSON            NOT NULL COMMENT 'Coverage config: {shape, range}',
    blocks_walking      TINYINT(1)      NOT NULL DEFAULT 1,
    price               INT             NOT NULL DEFAULT 0,
    icon                VARCHAR(255)    DEFAULT NULL,
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Tool data table';

CREATE TABLE IF NOT EXISTS categories (
    id          VARCHAR(36)     PRIMARY KEY,
    name        VARCHAR(100)    NOT NULL,
    type        VARCHAR(10)     NOT NULL COMMENT 'crop/tool',
    season      VARCHAR(10)     DEFAULT NULL COMMENT 'Season when type=crop',
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Category data table';

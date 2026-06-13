SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- Seed data for Stardew Valley Planting Planner
-- Uses REPLACE INTO to overwrite any garbled/dirty data
-- Columns: id, name, seasons, is_walkable, seed_source, seed_price, growth_days, can_regrow, regrow_interval,
--           base_sell_price, silver_price, gold_price, iridium_price,
--           base_energy, base_health, silver_energy, silver_health, gold_energy, gold_health, iridium_energy, iridium_health

-- Categories (4 records)
REPLACE INTO categories (id, name, type, season) VALUES
('cat-spring', '春季作物', 'crop', 'spring'),
('cat-summer', '夏季作物', 'crop', 'summer'),
('cat-fall',   '秋季作物', 'crop', 'fall'),
('cat-tool',   '工具',     'tool', NULL);

-- Spring crops (10 types)
REPLACE INTO crops (id, name, seasons, is_walkable, seed_source, seed_price, growth_days, can_regrow, regrow_interval,
  base_sell_price, silver_price, gold_price, iridium_price,
  base_energy, base_health, silver_energy, silver_health, gold_energy, gold_health, iridium_energy, iridium_health) VALUES
('crop-s01', '蓝爵',   'spring', 1, '皮埃尔杂货店', 30,  7,  0, NULL, 50,  62,  75,  100, 45, 20, 63,  28, 81,  36, 117, 52),
('crop-s02', '花椰菜', 'spring', 1, '皮埃尔杂货店', 80,  12, 0, NULL, 175, 218, 262, 350, 75, 33, 105, 47, 135, 60, 195, 87),
('crop-s03', '蒜',     'spring', 1, '皮埃尔杂货店', 40,  4,  0, NULL, 60,  75,  90,  120, 20, 9,  28,  12, 36,  16, 52,  23),
('crop-s04', '青豆',   'spring', 0, '皮埃尔杂货店', 60,  10, 1, 3,   40,  50,  60,  80,  25, 11, 35,  15, 45,  20, 65,  29),
('crop-s05', '甘蓝菜', 'spring', 1, '皮埃尔杂货店', 70,  6,  0, NULL, 110, 137, 165, 220, 50, 22, 70,  31, 90,  40, 130, 58),
('crop-s06', '防风草', 'spring', 1, '皮埃尔杂货店', 20,  4,  0, NULL, 35,  43,  52,  70,  25, 11, 35,  15, 45,  20, 65,  29),
('crop-s07', '土豆',   'spring', 1, '皮埃尔杂货店', 50,  6,  0, NULL, 80,  100, 120, 160, 25, 11, 35,  15, 45,  20, 65,  29),
('crop-s08', '草莓',   'spring', 1, '皮埃尔杂货店', 100, 8,  1, 4,   120, 150, 180, 240, 50, 22, 70,  31, 90,  40, 130, 58),
('crop-s09', '郁金香', 'spring', 1, '皮埃尔杂货店', 20,  6,  0, NULL, 30,  37,  45,  60,  45, 20, 63,  28, 81,  36, 117, 52),
('crop-s10', '未碾米', 'spring', 1, '皮埃尔杂货店', 40,  7,  0, NULL, 30,  37,  45,  60,  3,  1,  4,   1,  5,   2,  7,   3);

-- Summer crops (12 types, including multi-season crops)
REPLACE INTO crops (id, name, seasons, is_walkable, seed_source, seed_price, growth_days, can_regrow, regrow_interval,
  base_sell_price, silver_price, gold_price, iridium_price,
  base_energy, base_health, silver_energy, silver_health, gold_energy, gold_health, iridium_energy, iridium_health) VALUES
('crop-u01', '蓝莓',     'summer',     1, '皮埃尔杂货店', 80,  13, 1, 4,  50,  62,  75,  100, 25, 11, 35,  15, 45,  20, 65,  29),
('crop-u02', '玉米',     'summer,fall', 1, '皮埃尔杂货店', 150, 14, 1, 4,  50,  62,  75,  100, 25, 11, 35,  15, 45,  20, 65,  29),
('crop-u03', '啤酒花',   'summer',     0, '皮埃尔杂货店', 60,  11, 1, 1,  25,  31,  37,  50,  45, 20, 63,  28, 81,  36, 117, 52),
('crop-u04', '辣椒',     'summer',     1, '皮埃尔杂货店', 40,  5,  1, 3,  40,  50,  60,  80,  13, 5,  18,  8,  23,  10, 33,  14),
('crop-u05', '甜瓜',     'summer',     1, '皮埃尔杂货店', 80,  12, 0, NULL, 250, 312, 375, 500, 113,50, 158, 71, 203, 91, 293, 131),
('crop-u06', '虞美人花', 'summer',     1, '皮埃尔杂货店', 100, 7,  0, NULL, 140, 175, 210, 280, 45, 20, 63,  28, 81,  36, 117, 52),
('crop-u07', '萝卜',     'summer',     1, '皮埃尔杂货店', 40,  6,  0, NULL, 90,  112, 135, 180, 45, 20, 63,  28, 81,  36, 117, 52),
('crop-u08', '红叶卷心菜','summer',    1, '皮埃尔杂货店', 100, 9,  0, NULL, 260, 325, 390, 520, 75, 33, 105, 47, 135, 60, 195, 87),
('crop-u09', '夏季亮片', 'summer',     1, '皮埃尔杂货店', 50,  8,  0, NULL, 90,  112, 135, 180, 45, 20, 63,  28, 81,  36, 117, 52),
('crop-u10', '向日葵',   'summer,fall', 1, '皮埃尔杂货店', 200, 8,  0, NULL, 80,  100, 120, 160, 45, 20, 63,  28, 81,  36, 117, 52),
('crop-u11', '西红柿',   'summer',     1, '皮埃尔杂货店', 50,  11, 1, 4,  60,  75,  90,  120, 20, 9,  28,  12, 36,  16, 52,  23),
('crop-u12', '小麦',     'summer',     1, '皮埃尔杂货店', 10,  4,  0, NULL, 25,  31,  37,  50,  0,  0,  0,   0,  0,   0,  0,   0);

-- Fall crops (9 types, multi-season crops corn/sunflower listed above in summer)
REPLACE INTO crops (id, name, seasons, is_walkable, seed_source, seed_price, growth_days, can_regrow, regrow_interval,
  base_sell_price, silver_price, gold_price, iridium_price,
  base_energy, base_health, silver_energy, silver_health, gold_energy, gold_health, iridium_energy, iridium_health) VALUES
('crop-f01', '苋菜',     'fall', 1, '皮埃尔杂货店', 70,  7,  0, NULL, 150, 187, 225, 300, 50, 22, 70,  31, 90,  40, 130, 58),
('crop-f02', '洋蓟',     'fall', 1, '皮埃尔杂货店', 30,  8,  0, NULL, 160, 200, 240, 320, 30, 13, 42,  18, 54,  24, 78,  35),
('crop-f03', '小白菜',   'fall', 1, '皮埃尔杂货店', 50,  4,  0, NULL, 80,  100, 120, 160, 25, 11, 35,  15, 45,  20, 65,  29),
('crop-f04', '蔓越莓',   'fall', 1, '皮埃尔杂货店', 240, 7,  1, 5,   75,  93,  112, 150, 38, 17, 53,  23, 68,  30, 98,  44),
('crop-f05', '茄子',     'fall', 1, '皮埃尔杂货店', 20,  5,  1, 5,   60,  75,  90,  120, 20, 9,  28,  12, 36,  16, 52,  23),
('crop-f06', '玫瑰仙子', 'fall', 1, '皮埃尔杂货店', 200, 12, 0, NULL, 290, 362, 435, 580, 45, 20, 63,  28, 81,  36, 117, 52),
('crop-f07', '葡萄',     'fall', 0, '皮埃尔杂货店', 60,  10, 1, 3,   80,  100, 120, 160, 38, 17, 53,  23, 68,  30, 98,  44),
('crop-f08', '南瓜',     'fall', 1, '皮埃尔杂货店', 100, 13, 0, NULL, 320, 400, 480, 640, 0,  0,  0,   0,  0,   0,  0,   0),
('crop-f09', '山药',     'fall', 1, '皮埃尔杂货店', 60,  10, 0, NULL, 160, 200, 240, 320, 45, 20, 63,  28, 81,  36, 117, 52);

-- Tools (2 types)
REPLACE INTO tools (id, name, type, coverage_offsets, blocks_walking, price) VALUES
('tool-sprinkler', '喷水器', 'sprinkler', '{"shape": "cross", "range": 1}', 1, 0),
('tool-scarecrow', '稻草人', 'scarecrow', '{"shape": "square", "range": 6}', 1, 0);

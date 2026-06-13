/**
 * 前端作物收益计算器
 * 复用后端 CropCalculator 的公式（PRD 第7节）
 * 用于手动拖拽时实时计算 totalRevenue 和 ROI
 */

/**
 * 不可重复收获作物收益计算
 * 收获次数 = floor(27 / growthDays)
 * 总产出 = baseSellPrice × harvests
 * 总投入 = seedPrice × harvests（每次补种都需购买种子）
 *
 * @param {number} growthDays - 生长天数
 * @param {number} seedPrice - 种子价格
 * @param {number} baseSellPrice - 基础售价
 * @returns {Object|null} { harvestCount, totalRevenue, totalCost, roi }
 */
export function calculateNonRegrowable(growthDays, seedPrice, baseSellPrice) {
  if (growthDays <= 0 || growthDays >= 28) return null
  const harvests = Math.floor(27 / growthDays)
  const totalRevenue = baseSellPrice * harvests
  const totalCost = seedPrice * harvests
  const roi = totalCost === 0 ? Infinity : totalRevenue / totalCost
  return { harvestCount: harvests, totalRevenue, totalCost, roi }
}

/**
 * 可重复收获作物收益计算
 * 首次收获日 = 1 + growthDays
 * 收获次数 = floor((28 - growthDays - 1) / regrowInterval) + 1
 * 总产出 = baseSellPrice × harvests
 * 总投入 = seedPrice（仅初始种子费用，无需补种）
 *
 * @param {number} growthDays - 生长天数
 * @param {number} regrowInterval - 再生间隔
 * @param {number} seedPrice - 种子价格
 * @param {number} baseSellPrice - 基础售价
 * @returns {Object|null} { harvestCount, totalRevenue, totalCost, roi }
 */
export function calculateRegrowable(growthDays, regrowInterval, seedPrice, baseSellPrice) {
  if (growthDays <= 0 || growthDays >= 28) return null
  // 防御性处理：regrowInterval=0 降级为一次性
  if (regrowInterval <= 0) return calculateNonRegrowable(growthDays, seedPrice, baseSellPrice)
  const harvests = Math.floor((28 - growthDays - 1) / regrowInterval) + 1
  const totalRevenue = baseSellPrice * harvests
  const totalCost = seedPrice // 仅初始种子费用
  const roi = totalCost === 0 ? Infinity : totalRevenue / totalCost
  return { harvestCount: harvests, totalRevenue, totalCost, roi }
}

/**
 * 统一计算入口：根据作物是否可重复收获选择对应公式
 *
 * @param {Object} crop - 作物数据对象（需含 growthDays, canRegrow, regrowInterval, seedPrice, baseSellPrice）
 * @returns {Object|null} { harvestCount, totalRevenue, totalCost, roi }
 */
export function calculateCropRevenue(crop) {
  if (!crop) return null
  if (crop.canRegrow) {
    return calculateRegrowable(crop.growthDays, crop.regrowInterval, crop.seedPrice, crop.baseSellPrice)
  } else {
    return calculateNonRegrowable(crop.growthDays, crop.seedPrice, crop.baseSellPrice)
  }
}

/**
 * 计算ROI
 *
 * @param {number} totalRevenue - 总产出
 * @param {number} totalCost - 总投入
 * @returns {number} ROI
 */
export function calculateROI(totalRevenue, totalCost) {
  if (totalCost === 0) return totalRevenue > 0 ? Infinity : 0
  return totalRevenue / totalCost
}

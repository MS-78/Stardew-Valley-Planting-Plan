import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  // Planning state
  const planningId = ref(null)
  const season = ref('spring')
  const width = ref(12)
  const height = ref(12)
  const budget = ref(5000)
  const grid = ref([])

  // Data lists
  const crops = ref([])
  const tools = ref([])
  const categories = ref([])

  // Stats
  const stats = ref({
    cropCounts: {},
    toolCounts: {},
    totalCost: 0,
    totalRevenue: 0,
    roi: 0,
    budgetRemaining: 0,
    landUtilization: 0,
    totalHealthRecovery: 0,
    totalEnergyRecovery: 0
  })

  // Drag tracking state (shared between SidePanel and GridCanvas)
  // null = not dragging
  // { source: 'sidebar', item: { id, name, type, data } }
  // { source: 'grid', cell: { ... }, fromRow, fromCol }
  const dragState = ref(null)

  // Constraint violation highlighting — cells to highlight on the grid canvas
  // Array of { row, col } objects
  const highlightedViolations = ref([])

  function resetPlanning() {
    planningId.value = null
    grid.value = []
    highlightedViolations.value = []
    stats.value = {
      cropCounts: {},
      toolCounts: {},
      totalCost: 0,
      totalRevenue: 0,
      roi: 0,
      budgetRemaining: 0,
      landUtilization: 0,
      totalHealthRecovery: 0,
      totalEnergyRecovery: 0
    }
  }

  return {
    planningId, season, width, height, budget, grid,
    crops, tools, categories, stats, dragState, highlightedViolations,
    resetPlanning
  }
})

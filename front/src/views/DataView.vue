<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { getCrops, createCrop, updateCrop, deleteCrop } from '../api/cropApi'
import { getTools, createTool, updateTool, deleteTool } from '../api/toolApi'
import { getCategories, createCategory, updateCategory, deleteCategory } from '../api/categoryApi'
import { ElMessage, ElMessageBox } from 'element-plus'

const activeTab = ref('categories')
const categories = ref([])
const crops = ref([])
const tools = ref([])
const loading = ref(false)

// Search
const categorySearch = ref('')
const cropSearch = ref('')
const toolSearch = ref('')

// Pagination
const categoryPage = ref(1)
const cropPage = ref(1)
const toolPage = ref(1)
const pageSize = ref(10)

// Filters
const categoryTypeFilter = ref('')
const cropSeasonFilter = ref([])
const cropGrowthMin = ref(null)
const cropGrowthMax = ref(null)
const cropRegrowFilter = ref('')
const cropWalkableFilter = ref('')
const toolTypeFilter = ref('')

// Dialog state
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogType = ref('') // 'category', 'crop', 'tool'
const dialogMode = ref('') // 'create', 'edit'
const dialogLoading = ref(false)
const editingId = ref(null)

// Form refs
const formRef = ref(null)

// Category form
const categoryForm = ref({
  name: '',
  type: 'crop',
  season: 'spring'
})

// Crop form
const cropForm = ref({
  name: '',
  seasons: ['spring'],
  isWalkable: true,
  seedSource: '皮埃尔杂货店',
  seedPrice: 0,
  growthDays: 1,
  canRegrow: false,
  regrowInterval: null,
  baseSellPrice: 0,
  artisanSellPrice: null,
  silverPrice: null,
  goldPrice: null,
  iridiumPrice: null,
  baseEnergy: null,
  baseHealth: null,
  silverEnergy: null,
  silverHealth: null,
  goldEnergy: null,
  goldHealth: null,
  iridiumEnergy: null,
  iridiumHealth: null,
  farmerMult: 1.0,
  agriMult: 1.0,
  icon: null
})

// Tool form
const toolForm = ref({
  name: '',
  type: 'sprinkler',
  coverageOffsets: '{"shape": "cross", "range": 1}',
  blocksWalking: true,
  price: 0,
  icon: null
})

// Form validation rules
const categoryRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const cropRules = {
  name: [{ required: true, message: '请输入作物名称', trigger: 'blur' }],
  seasons: [{ required: true, type: 'array', min: 1, message: '请选择至少一个季节', trigger: 'change' }],
  seedSource: [{ required: true, message: '请输入种子来源', trigger: 'blur' }],
  seedPrice: [{ required: true, message: '请输入种子价格', trigger: 'blur' }],
  growthDays: [{ required: true, message: '请输入生长天数', trigger: 'blur' }],
  baseSellPrice: [{ required: true, message: '请输入基础售价', trigger: 'blur' }]
}

const toolRules = {
  name: [{ required: true, message: '请输入工具名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择工具类型', trigger: 'change' }],
  coverageOffsets: [{ required: true, message: '请输入覆盖配置', trigger: 'blur' }]
}

// Filtered data (search + filters)
const filteredCategories = computed(() => {
  let list = categories.value
  if (categorySearch.value) {
    const kw = categorySearch.value.toLowerCase()
    list = list.filter(c => c.name.toLowerCase().includes(kw))
  }
  if (categoryTypeFilter.value) {
    list = list.filter(c => c.type === categoryTypeFilter.value)
  }
  return list
})

const filteredCrops = computed(() => {
  let list = crops.value
  if (cropSearch.value) {
    const kw = cropSearch.value.toLowerCase()
    list = list.filter(c => c.name.toLowerCase().includes(kw))
  }
  if (cropSeasonFilter.value.length > 0) {
    list = list.filter(c => {
      const seasons = c.seasons || [c.season]
      return cropSeasonFilter.value.some(s => seasons.includes(s))
    })
  }
  if (cropGrowthMin.value != null || cropGrowthMax.value != null) {
    list = list.filter(c => {
      const d = c.growthDays
      if (cropGrowthMin.value != null && d < cropGrowthMin.value) return false
      if (cropGrowthMax.value != null && d > cropGrowthMax.value) return false
      return true
    })
  }
  if (cropRegrowFilter.value !== '') {
    const val = cropRegrowFilter.value === 'true'
    list = list.filter(c => c.canRegrow === val)
  }
  if (cropWalkableFilter.value !== '') {
    const val = cropWalkableFilter.value === 'true'
    list = list.filter(c => c.isWalkable === val)
  }
  return list
})

const filteredTools = computed(() => {
  let list = tools.value
  if (toolSearch.value) {
    const kw = toolSearch.value.toLowerCase()
    list = list.filter(t => t.name.toLowerCase().includes(kw))
  }
  if (toolTypeFilter.value) {
    list = list.filter(t => t.type === toolTypeFilter.value)
  }
  return list
})

// Paged data (search + pagination)
const pagedCategories = computed(() => {
  const start = (categoryPage.value - 1) * pageSize.value
  return filteredCategories.value.slice(start, start + pageSize.value)
})
const pagedCrops = computed(() => {
  const start = (cropPage.value - 1) * pageSize.value
  return filteredCrops.value.slice(start, start + pageSize.value)
})
const pagedTools = computed(() => {
  const start = (toolPage.value - 1) * pageSize.value
  return filteredTools.value.slice(start, start + pageSize.value)
})

// Season labels
const seasonLabel = (s) => {
  const map = { spring: '春季', summer: '夏季', fall: '秋季' }
  return map[s] || s
}

async function loadData() {
  loading.value = true
  try {
    const [catRes, cropRes, toolRes] = await Promise.all([
      getCategories(),
      getCrops(),
      getTools()
    ])
    categories.value = catRes.data || []
    crops.value = cropRes.data || []
    tools.value = toolRes.data || []
  } catch (e) {
    ElMessage.error('加载数据失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

// 搜索或筛选时重置页码
watch(categorySearch, () => { categoryPage.value = 1 })
watch(categoryTypeFilter, () => { categoryPage.value = 1 })
watch(cropSearch, () => { cropPage.value = 1 })
watch(cropSeasonFilter, () => { cropPage.value = 1 })
watch(cropGrowthMin, () => { cropPage.value = 1 })
watch(cropGrowthMax, () => { cropPage.value = 1 })
watch(cropRegrowFilter, () => { cropPage.value = 1 })
watch(cropWalkableFilter, () => { cropPage.value = 1 })
watch(toolSearch, () => { toolPage.value = 1 })
watch(toolTypeFilter, () => { toolPage.value = 1 })

// ========== Category CRUD ==========

function openCategoryCreate() {
  dialogType.value = 'category'
  dialogMode.value = 'create'
  dialogTitle.value = '新增分类'
  editingId.value = null
  categoryForm.value = { name: '', type: 'crop', season: 'spring' }
  dialogVisible.value = true
}

function openCategoryEdit(row) {
  dialogType.value = 'category'
  dialogMode.value = 'edit'
  dialogTitle.value = '编辑分类'
  editingId.value = row.id
  categoryForm.value = {
    name: row.name,
    type: row.type,
    season: row.season || 'spring'
  }
  dialogVisible.value = true
}

async function handleDeleteCategory(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${row.name}」吗？删除后不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteCategory(row.id)
    ElMessage.success('分类已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e?.toString() !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

// ========== Crop CRUD ==========

function openCropCreate() {
  dialogType.value = 'crop'
  dialogMode.value = 'create'
  dialogTitle.value = '新增作物'
  editingId.value = null
  cropForm.value = {
    name: '',
    seasons: ['spring'],
    isWalkable: true,
    seedSource: '皮埃尔杂货店',
    seedPrice: 0,
    growthDays: 1,
    canRegrow: false,
    regrowInterval: null,
    baseSellPrice: 0,
    artisanSellPrice: null,
    silverPrice: null,
    goldPrice: null,
    iridiumPrice: null,
    baseEnergy: null,
    baseHealth: null,
    silverEnergy: null,
    silverHealth: null,
    goldEnergy: null,
    goldHealth: null,
    iridiumEnergy: null,
    iridiumHealth: null,
    farmerMult: 1.0,
    agriMult: 1.0,
    icon: null
  }
  dialogVisible.value = true
}

function openCropEdit(row) {
  dialogType.value = 'crop'
  dialogMode.value = 'edit'
  dialogTitle.value = '编辑作物'
  editingId.value = row.id
  cropForm.value = {
    name: row.name,
    seasons: row.seasons || (row.season ? [row.season] : ['spring']),
    isWalkable: row.isWalkable,
    seedSource: row.seedSource,
    seedPrice: row.seedPrice,
    growthDays: row.growthDays,
    canRegrow: row.canRegrow,
    regrowInterval: row.regrowInterval,
    baseSellPrice: row.baseSellPrice,
    artisanSellPrice: row.artisanSellPrice,
    silverPrice: row.silverPrice,
    goldPrice: row.goldPrice,
    iridiumPrice: row.iridiumPrice,
    baseEnergy: row.baseEnergy,
    baseHealth: row.baseHealth,
    silverEnergy: row.silverEnergy,
    silverHealth: row.silverHealth,
    goldEnergy: row.goldEnergy,
    goldHealth: row.goldHealth,
    iridiumEnergy: row.iridiumEnergy,
    iridiumHealth: row.iridiumHealth,
    farmerMult: row.farmerMult || 1.0,
    agriMult: row.agriMult || 1.0,
    icon: row.icon
  }
  dialogVisible.value = true
}

async function handleDeleteCrop(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除作物「${row.name}」吗？删除后不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteCrop(row.id)
    ElMessage.success('作物已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e?.toString() !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

// ========== Tool CRUD ==========

function openToolCreate() {
  dialogType.value = 'tool'
  dialogMode.value = 'create'
  dialogTitle.value = '新增工具'
  editingId.value = null
  toolForm.value = {
    name: '',
    type: 'sprinkler',
    coverageOffsets: '{"shape": "cross", "range": 1}',
    blocksWalking: true,
    price: 0,
    icon: null
  }
  dialogVisible.value = true
}

function openToolEdit(row) {
  dialogType.value = 'tool'
  dialogMode.value = 'edit'
  dialogTitle.value = '编辑工具'
  editingId.value = row.id
  toolForm.value = {
    name: row.name,
    type: row.type,
    coverageOffsets: typeof row.coverageOffsets === 'object'
      ? JSON.stringify(row.coverageOffsets)
      : row.coverageOffsets,
    blocksWalking: row.blocksWalking,
    price: row.price,
    icon: row.icon
  }
  dialogVisible.value = true
}

async function handleDeleteTool(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除工具「${row.name}」吗？删除后不可恢复。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteTool(row.id)
    ElMessage.success('工具已删除')
    await loadData()
  } catch (e) {
    if (e !== 'cancel' && e?.toString() !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

// ========== Dialog submit ==========

async function handleDialogConfirm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (err) {
    return
  }

  dialogLoading.value = true
  try {
    if (dialogType.value === 'category') {
      const data = { ...categoryForm.value }
      if (data.type === 'tool') data.season = null
      if (dialogMode.value === 'create') {
        await createCategory(data)
        ElMessage.success('分类已创建')
      } else {
        await updateCategory(editingId.value, data)
        ElMessage.success('分类已更新')
      }
    } else if (dialogType.value === 'crop') {
      const data = { ...cropForm.value }
      if (!data.canRegrow) data.regrowInterval = null
      if (dialogMode.value === 'create') {
        await createCrop(data)
        ElMessage.success('作物已创建')
      } else {
        await updateCrop(editingId.value, data)
        ElMessage.success('作物已更新')
      }
    } else if (dialogType.value === 'tool') {
      const data = { ...toolForm.value }
      if (dialogMode.value === 'create') {
        await createTool(data)
        ElMessage.success('工具已创建')
      } else {
        await updateTool(editingId.value, data)
        ElMessage.success('工具已更新')
      }
    }
    dialogVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    dialogLoading.value = false
  }
}

// Helper function for search highlighting
function highlightText(text, keyword) {
  if (!keyword || !text) return text
  // Escape HTML entities first
  const safe = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const regex = new RegExp(`(${escaped})`, 'gi')
  return safe.replace(regex, '<mark>$1</mark>')
}
</script>

<template>
  <div class="data-view">
    <div class="data-header">
      <h3>植株与工具数据管理</h3>
      <el-button @click="loadData" :loading="loading">
        刷新数据
      </el-button>
    </div>
    <div class="data-content">
      <el-tabs v-model="activeTab">
        <!-- 分类管理 -->
        <el-tab-pane label="分类管理" name="categories">
          <div class="tab-toolbar">
            <el-input
              v-model="categorySearch"
              placeholder="搜索分类名称..."
              clearable
              style="width: 250px"
              prefix-icon="Search"
            />
            <el-button type="primary" @click="openCategoryCreate">
              + 新增分类
            </el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="categoryTypeFilter" placeholder="类型" clearable size="small" style="width: 120px">
              <el-option label="作物" value="crop" />
              <el-option label="工具" value="tool" />
            </el-select>
          </div>
          <el-table :data="pagedCategories" v-loading="loading" stripe border empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="150" />
            <el-table-column prop="name" label="名称" min-width="120">
              <template #default="{ row }">
                <span v-html="highlightText(row.name, categorySearch)"></span>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.type === 'crop' ? 'success' : 'info'" size="small">
                  {{ row.type === 'crop' ? '作物' : '工具' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="season" label="季节" width="100">
              <template #default="{ row }">
                {{ row.season ? seasonLabel(row.season) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openCategoryEdit(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteCategory(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-pagination">
            <el-pagination
              v-model:current-page="categoryPage"
              :page-size="pageSize"
              :total="filteredCategories.length"
              layout="total, prev, pager, next"
              small
            />
          </div>
        </el-tab-pane>

        <!-- 作物管理 -->
        <el-tab-pane label="作物管理" name="crops">
          <div class="tab-toolbar">
            <el-input
              v-model="cropSearch"
              placeholder="搜索作物名称..."
              clearable
              style="width: 250px"
              prefix-icon="Search"
            />
            <el-button type="primary" @click="openCropCreate">
              + 新增作物
            </el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="cropSeasonFilter" placeholder="季节" clearable multiple collapse-tags collapse-tags-tooltip size="small" style="width: 200px">
              <el-option label="春季" value="spring" />
              <el-option label="夏季" value="summer" />
              <el-option label="秋季" value="fall" />
            </el-select>
            <span class="filter-range">
              <el-input-number v-model="cropGrowthMin" placeholder="最少" :min="1" :max="28" controls-position="right" size="small" style="width: 100px" />
              <span class="filter-range-sep">~</span>
              <el-input-number v-model="cropGrowthMax" placeholder="最多" :min="1" :max="28" controls-position="right" size="small" style="width: 100px" />
              <span class="filter-range-label">天</span>
            </span>
            <el-select v-model="cropRegrowFilter" placeholder="可重复" clearable size="small" style="width: 120px">
              <el-option label="可重复" value="true" />
              <el-option label="一次性" value="false" />
            </el-select>
            <el-select v-model="cropWalkableFilter" placeholder="可踩踏" clearable size="small" style="width: 120px">
              <el-option label="可踩踏" value="true" />
              <el-option label="棚架" value="false" />
            </el-select>
          </div>
          <el-table :data="pagedCrops" v-loading="loading" stripe border empty-text="暂无数据">
            <el-table-column prop="name" label="名称" min-width="100">
              <template #default="{ row }">
                <span v-html="highlightText(row.name, cropSearch)"></span>
              </template>
            </el-table-column>
            <el-table-column prop="seasons" label="适季" width="150">
              <template #default="{ row }">
                <el-tag
                  v-for="s in (row.seasons || [row.season])"
                  :key="s"
                  size="small"
                  style="margin-right: 4px; margin-bottom: 2px"
                >
                  {{ seasonLabel(s) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="seedPrice" label="种子价" width="80" />
            <el-table-column prop="growthDays" label="生长天数" width="80" />
            <el-table-column label="售价" align="center">
              <el-table-column prop="baseSellPrice" label="普通" width="70" />
              <el-table-column prop="silverPrice" label="银星" width="70">
                <template #default="{ row }">{{ row.silverPrice ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="goldPrice" label="金星" width="70">
                <template #default="{ row }">{{ row.goldPrice ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="iridiumPrice" label="铱星" width="70">
                <template #default="{ row }">{{ row.iridiumPrice ?? '-' }}</template>
              </el-table-column>
            </el-table-column>
            <el-table-column label="能量回复" align="center">
              <el-table-column prop="baseEnergy" label="普通" width="60">
                <template #default="{ row }">{{ row.baseEnergy ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="silverEnergy" label="银星" width="60">
                <template #default="{ row }">{{ row.silverEnergy ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="goldEnergy" label="金星" width="60">
                <template #default="{ row }">{{ row.goldEnergy ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="iridiumEnergy" label="铱星" width="60">
                <template #default="{ row }">{{ row.iridiumEnergy ?? '-' }}</template>
              </el-table-column>
            </el-table-column>
            <el-table-column label="生命回复" align="center">
              <el-table-column prop="baseHealth" label="普通" width="60">
                <template #default="{ row }">{{ row.baseHealth ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="silverHealth" label="银星" width="60">
                <template #default="{ row }">{{ row.silverHealth ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="goldHealth" label="金星" width="60">
                <template #default="{ row }">{{ row.goldHealth ?? '-' }}</template>
              </el-table-column>
              <el-table-column prop="iridiumHealth" label="铱星" width="60">
                <template #default="{ row }">{{ row.iridiumHealth ?? '-' }}</template>
              </el-table-column>
            </el-table-column>
            <el-table-column prop="canRegrow" label="可重复" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.canRegrow" type="success" size="small">可重复</el-tag>
                <el-tag v-else type="info" size="small">一次性</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="isWalkable" label="可踩踏" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.isWalkable" type="success" size="small">可踩踏</el-tag>
                <el-tag v-else type="info" size="small">棚架</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openCropEdit(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteCrop(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-pagination">
            <el-pagination
              v-model:current-page="cropPage"
              :page-size="pageSize"
              :total="filteredCrops.length"
              layout="total, prev, pager, next"
              small
            />
          </div>
        </el-tab-pane>

        <!-- 工具管理 -->
        <el-tab-pane label="工具管理" name="tools">
          <div class="tab-toolbar">
            <el-input
              v-model="toolSearch"
              placeholder="搜索工具名称..."
              clearable
              style="width: 250px"
              prefix-icon="Search"
            />
            <el-button type="primary" @click="openToolCreate">
              + 新增工具
            </el-button>
          </div>
          <div class="filter-bar">
            <el-select v-model="toolTypeFilter" placeholder="类型" clearable size="small" style="width: 120px">
              <el-option label="喷水器" value="sprinkler" />
              <el-option label="稻草人" value="scarecrow" />
            </el-select>
          </div>
          <el-table :data="pagedTools" v-loading="loading" stripe border empty-text="暂无数据">
            <el-table-column prop="id" label="ID" width="180" />
            <el-table-column prop="name" label="名称" min-width="100">
              <template #default="{ row }">
                <span v-html="highlightText(row.name, toolSearch)"></span>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag :type="row.type === 'sprinkler' ? '' : 'warning'" size="small">
                  {{ row.type === 'sprinkler' ? '喷水器' : '稻草人' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="coverageOffsets" label="覆盖配置" min-width="200">
              <template #default="{ row }">
                <code>{{ typeof row.coverageOffsets === 'object' ? JSON.stringify(row.coverageOffsets) : row.coverageOffsets }}</code>
              </template>
            </el-table-column>
            <el-table-column prop="blocksWalking" label="阻挡行走" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.blocksWalking" type="warning" size="small">是</el-tag>
                <el-tag v-else type="success" size="small">否</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openToolEdit(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="handleDeleteTool(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="table-pagination">
            <el-pagination
              v-model:current-page="toolPage"
              :page-size="pageSize"
              :total="filteredTools.length"
              layout="total, prev, pager, next"
              small
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 通用编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="620px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <!-- 分类表单 -->
      <el-form
        v-if="dialogType === 'category'"
        ref="formRef"
        :model="categoryForm"
        :rules="categoryRules"
        label-width="100px"
      >
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="categoryForm.type" style="width: 100%">
            <el-option label="作物" value="crop" />
            <el-option label="工具" value="tool" />
          </el-select>
        </el-form-item>
        <el-form-item label="季节" prop="season" v-if="categoryForm.type === 'crop'">
          <el-select v-model="categoryForm.season" style="width: 100%">
            <el-option label="春季" value="spring" />
            <el-option label="夏季" value="summer" />
            <el-option label="秋季" value="fall" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 作物表单 -->
      <el-form
        v-else-if="dialogType === 'crop'"
        ref="formRef"
        :model="cropForm"
        :rules="cropRules"
        label-width="100px"
      >
        <el-form-item label="作物名称" prop="name">
          <el-input v-model="cropForm.name" placeholder="请输入作物名称" />
        </el-form-item>
        <el-form-item label="适季" prop="seasons">
          <el-select v-model="cropForm.seasons" multiple style="width: 100%">
            <el-option label="春季" value="spring" />
            <el-option label="夏季" value="summer" />
            <el-option label="秋季" value="fall" />
          </el-select>
        </el-form-item>
        <el-form-item label="可踩踏" prop="isWalkable">
          <el-switch v-model="cropForm.isWalkable" active-text="是" inactive-text="否" />
          <span class="form-hint-inline">棚架作物不可踩踏</span>
        </el-form-item>
        <el-form-item label="种子来源" prop="seedSource">
          <el-input v-model="cropForm.seedSource" placeholder="如: 皮埃尔杂货店" />
        </el-form-item>
        <el-form-item label="种子价格" prop="seedPrice">
          <el-input-number v-model="cropForm.seedPrice" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="生长天数" prop="growthDays">
          <el-input-number v-model="cropForm.growthDays" :min="1" :max="28" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="可重复收获" prop="canRegrow">
          <el-switch v-model="cropForm.canRegrow" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="再生间隔" prop="regrowInterval" v-if="cropForm.canRegrow">
          <el-input-number v-model="cropForm.regrowInterval" :min="1" :max="28" controls-position="right" style="width: 100%" />
          <div class="form-hint">两次收获之间的天数</div>
        </el-form-item>
        <el-form-item label="基础售价" prop="baseSellPrice">
          <el-input-number v-model="cropForm.baseSellPrice" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="银星售价">
          <el-input-number v-model="cropForm.silverPrice" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金星售价">
          <el-input-number v-model="cropForm.goldPrice" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="铱星售价">
          <el-input-number v-model="cropForm.iridiumPrice" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="工匠售价" prop="artisanSellPrice">
          <el-input-number v-model="cropForm.artisanSellPrice" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">回复属性</el-divider>
        <el-form-item label="基础能量">
          <el-input-number v-model="cropForm.baseEnergy" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="基础生命">
          <el-input-number v-model="cropForm.baseHealth" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="银星能量">
          <el-input-number v-model="cropForm.silverEnergy" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="银星生命">
          <el-input-number v-model="cropForm.silverHealth" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金星能量">
          <el-input-number v-model="cropForm.goldEnergy" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="金星生命">
          <el-input-number v-model="cropForm.goldHealth" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="铱星能量">
          <el-input-number v-model="cropForm.iridiumEnergy" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="铱星生命">
          <el-input-number v-model="cropForm.iridiumHealth" :min="0" :max="9999" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>

      <!-- 工具表单 -->
      <el-form
        v-else-if="dialogType === 'tool'"
        ref="formRef"
        :model="toolForm"
        :rules="toolRules"
        label-width="100px"
      >
        <el-form-item label="工具名称" prop="name">
          <el-input v-model="toolForm.name" placeholder="请输入工具名称" />
        </el-form-item>
        <el-form-item label="工具类型" prop="type">
          <el-select v-model="toolForm.type" style="width: 100%">
            <el-option label="喷水器" value="sprinkler" />
            <el-option label="稻草人" value="scarecrow" />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖配置" prop="coverageOffsets">
          <el-input v-model="toolForm.coverageOffsets" placeholder='如: {"shape":"cross","range":1}' />
          <div class="form-hint">JSON格式: shape=cross(十字)/square(正方形), range=范围</div>
        </el-form-item>
        <el-form-item label="阻挡行走" prop="blocksWalking">
          <el-switch v-model="toolForm.blocksWalking" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="toolForm.price" :min="0" :max="99999" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleDialogConfirm" :loading="dialogLoading">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.data-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.data-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
}

.data-header h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}

.data-content {
  flex: 1;
  padding: 16px 24px;
  overflow: auto;
}

.tab-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0 4px;
}

.filter-bar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 4px;
  flex-wrap: wrap;
  align-items: center;
}

.filter-range {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.filter-range-sep {
  color: #909399;
  font-size: 13px;
}
.filter-range-label {
  color: #909399;
  font-size: 12px;
}

.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.form-hint-inline {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

:deep(mark) {
  background-color: #fff3cd;
  padding: 0 2px;
  border-radius: 2px;
}

code {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
}
</style>

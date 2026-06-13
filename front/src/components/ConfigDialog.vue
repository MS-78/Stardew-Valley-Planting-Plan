<template>
  <el-dialog
    :model-value="visible"
    title="开始种植规划"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
    @closed="$emit('closed')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
    >
      <el-form-item label="季节" prop="season">
        <el-select v-model="form.season" placeholder="请选择季节" style="width: 100%">
          <el-option label="🌸 春季" value="spring" />
          <el-option label="☀️ 夏季" value="summer" />
          <el-option label="🍂 秋季" value="fall" />
        </el-select>
      </el-form-item>

      <el-form-item label="地块宽度" prop="width">
        <el-input-number
          v-model="form.width"
          :min="6"
          :max="100"
          :step="1"
          controls-position="right"
          style="width: 100%"
        />
        <div class="form-hint">格子数（6-100），建议 10-50</div>
      </el-form-item>

      <el-form-item label="地块高度" prop="height">
        <el-input-number
          v-model="form.height"
          :min="6"
          :max="100"
          :step="1"
          controls-position="right"
          style="width: 100%"
        />
        <div class="form-hint">格子数（6-100），建议 10-50</div>
      </el-form-item>

      <el-form-item label="种植预算" prop="budget">
        <el-input-number
          v-model="form.budget"
          :min="1"
          :max="999999"
          :step="100"
          controls-position="right"
          style="width: 100%"
        />
        <div class="form-hint">单位：金币(G)</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="handleConfirm" :loading="loading">
        确认并开始规划
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'confirm', 'closed'])

const formRef = ref(null)

const form = reactive({
  season: 'spring',
  width: 12,
  height: 12,
  budget: 5000
})

const rules = {
  season: [
    { required: true, message: '请选择季节', trigger: 'change' }
  ],
  width: [
    { required: true, message: '请输入地块宽度', trigger: 'blur' }
  ],
  height: [
    { required: true, message: '请输入地块高度', trigger: 'blur' }
  ],
  budget: [
    { required: true, message: '请输入种植预算', trigger: 'blur' }
  ]
}

const handleConfirm = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('confirm', { ...form })
  } catch (err) {
    // validation failed, form will show errors
  }
}
</script>

<style scoped>
.form-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>

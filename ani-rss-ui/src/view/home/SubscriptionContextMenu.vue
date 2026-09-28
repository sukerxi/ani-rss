<template>
  <teleport to="body">
    <div v-if="visible"
         ref="menuRef"
         class="subscription-context-menu"
         :style="{top: position.y + 'px', left: position.x + 'px'}"
         @click.stop
         @contextmenu.prevent.stop
         @mousedown.stop>
      <div class="context-menu-item" @click="updateTotalEpisodeNumber(false)">
        <el-icon>
          <RefreshRight/>
        </el-icon>
        <span>更新总集数</span>
      </div>
      <div class="context-menu-item is-warning" @click="updateTotalEpisodeNumber(true)">
        <el-icon>
          <Refresh/>
        </el-icon>
        <span>更新总集数 [F]</span>
      </div>
      <div class="context-menu-item is-divided" @click="scrape(false)">
        <el-icon>
          <RefreshRight/>
        </el-icon>
        <span>刮削</span>
      </div>
      <div class="context-menu-item is-warning" @click="scrape(true)">
        <el-icon>
          <Refresh/>
        </el-icon>
        <span>刮削 [F]</span>
      </div>
      <div class="context-menu-item is-divided is-primary" @click="enable(true)">
        <el-icon>
          <CircleCheck/>
        </el-icon>
        <span>启用</span>
      </div>
      <div class="context-menu-item is-warning" @click="enable(false)">
        <el-icon>
          <CircleClose/>
        </el-icon>
        <span>禁用</span>
      </div>
      <div class="context-menu-item is-divided" @click="importData">
        <el-icon>
          <Download/>
        </el-icon>
        <span>导入</span>
      </div>
      <div class="context-menu-item" @click="exportData">
        <el-icon>
          <Upload/>
        </el-icon>
        <span>导出</span>
      </div>
      <div class="context-menu-item is-divided is-danger" @click="remove">
        <el-icon>
          <Remove/>
        </el-icon>
        <span>删除</span>
      </div>
    </div>
  </teleport>
</template>

<script setup>
import {nextTick, onBeforeUnmount, onMounted, ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import {
  CircleCheck,
  CircleClose,
  Download,
  Refresh,
  RefreshRight,
  Remove,
  Upload
} from "@element-plus/icons-vue";
import * as http from "@/js/http.js";

const emit = defineEmits(['delete', 'import'])

const visible = ref(false)
const menuRef = ref()
const current = ref(null)
const position = ref({x: 0, y: 0})

const GAP = 8

const adjustPosition = () => {
  const el = menuRef.value
  if (!el) {
    return
  }
  const rect = el.getBoundingClientRect()
  let {x, y} = position.value
  if (x + rect.width > window.innerWidth - GAP) {
    x = Math.max(GAP, window.innerWidth - rect.width - GAP)
  }
  if (y + rect.height > window.innerHeight - GAP) {
    y = Math.max(GAP, window.innerHeight - rect.height - GAP)
  }
  position.value = {x, y}
}

const open = (event, item) => {
  current.value = item
  visible.value = true
  position.value = {x: event.clientX, y: event.clientY}
  nextTick(adjustPosition)
}

const close = () => {
  visible.value = false
  current.value = null
}

const run = async (request, {reload = false} = {}, target = current.value) => {
  close()
  if (!target) {
    return
  }
  const res = await request([target.id])
  ElMessage.success(res.message)
  if (reload) {
    window.$reLoadList?.()
  }
}

// 已完结：总集数已知且当前进度已达到总集数
const isFinished = item => !!item.totalEpisodeNumber
    && Number(item.currentEpisodeNumber) >= Number(item.totalEpisodeNumber)

const updateTotalEpisodeNumber = force => {
  return run(ids => http.updateTotalEpisodeNumber(force, ids), {reload: true})
}

const scrape = force => {
  return run(ids => http.batchScrape(force, ids))
}

const enable = value => {
  const item = current.value
  const request = ids => http.batchEnable(value, ids)
  // 启用，或订阅已完结：直接执行
  if (value || !item || isFinished(item)) {
    return run(request, {reload: true})
  }
  // 禁用未完结订阅前二次确认，避免错过后续集数
  close()
  const currentEpisode = item.currentEpisodeNumber || 0
  const totalEpisode = item.totalEpisodeNumber || '*'
  return ElMessageBox.confirm(
      `《${item.title}》尚未完结（${currentEpisode} / ${totalEpisode}），禁用后将不再自动下载后续集数，是否确认禁用？`,
      '禁用订阅',
      {
        confirmButtonText: '确认禁用',
        confirmButtonClass: 'is-text is-has-bg el-button--danger',
        cancelButtonText: '取消',
        cancelButtonClass: 'is-text is-has-bg',
        type: 'warning'
      }
  ).then(() => run(request, {reload: true}, item)).catch(() => {})
}

const importData = () => {
  close()
  emit('import')
}

const exportData = () => {
  const item = current.value
  close()
  if (!item) {
    return
  }
  const blob = new Blob([JSON.stringify([item])], {type: 'application/json'})
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.style.display = 'none'
  anchor.href = url
  anchor.download = 'ani.v2.json'
  document.body.appendChild(anchor)
  try {
    anchor.click()
  } finally {
    document.body.removeChild(anchor)
    URL.revokeObjectURL(url)
  }
}

const remove = () => {
  const item = current.value
  close()
  if (item) {
    emit('delete', item)
  }
}

const handleDocumentMouseDown = event => {
  if (visible.value && menuRef.value && !menuRef.value.contains(event.target)) {
    close()
  }
}

const handleKeydown = event => {
  if (event.key === 'Escape') {
    close()
  }
}

const handleCloseEvent = () => {
  if (visible.value) {
    close()
  }
}

onMounted(() => {
  document.addEventListener('mousedown', handleDocumentMouseDown)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('resize', handleCloseEvent)
  // scroll 事件不冒泡，需在捕获阶段监听以覆盖 el-scrollbar 内部滚动
  window.addEventListener('scroll', handleCloseEvent, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleDocumentMouseDown)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', handleCloseEvent)
  window.removeEventListener('scroll', handleCloseEvent, true)
})

defineExpose({open, close})
</script>

<style scoped>
.subscription-context-menu {
  position: fixed;
  z-index: 3000;
  min-width: 168px;
  margin: 0;
  padding: 6px 0;
  box-sizing: border-box;
  background-color: var(--el-bg-color-overlay, #ffffff);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base, 6px);
  box-shadow: var(--el-box-shadow-light);
  user-select: none;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  font-size: 14px;
  line-height: 36px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  white-space: nowrap;
}

.context-menu-item .el-icon {
  font-size: 14px;
}

.context-menu-item:hover {
  background-color: var(--el-fill-color-light);
}

.context-menu-item.is-primary {
  color: var(--el-color-primary);
}

.context-menu-item.is-warning {
  color: var(--el-color-warning);
}

.context-menu-item.is-danger {
  color: var(--el-color-danger);
}

.context-menu-item.is-divided {
  margin-top: 6px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>

<template>
  <AniBTView ref="aniBTRef" @callback="mikanCallback"/>
  <MikanView ref="mikanRef" @callback="mikanCallback"/>
  <AnimeGardenView ref="animeGardenRef" @callback="mikanCallback"/>
  <div class="standby-rss-view">
    <el-alert v-if="!config.standbyRss" :closable="false"
              show-icon
              class="standby-alert" type="warning">
      <template #title>
        当前备用RSS功能并未开启, 可前往 <strong>设置-基本设置-RSS设置-备用RSS</strong> 启用
      </template>
    </el-alert>
    <div class="standby-mode">
      <div class="standby-mode-title">补位模式（多个字幕组时，同一集如何取舍）</div>
      <el-radio-group
          :model-value="modeValue"
          :disabled="!config.standbyRss"
          class="standby-mode-group"
          @update:model-value="v => props.ani.standbyMode = v">
        <el-radio v-for="m in modeOptions" :key="m.value" :value="m.value"
                  class="standby-mode-item">
          <span class="mode-text">
            <span class="mode-label">{{ m.label }}</span>
            <span class="mode-desc">{{ m.desc }}</span>
          </span>
        </el-radio>
      </el-radio-group>
    </div>
    <div class="flex standby-toolbar">
      <el-button text bg icon="Plus" @click="plus" type="primary"/>
      <el-button
          @click="mikanRef?.show(props.ani)"
          text bg>
        <template #icon>
          <img src="@/icon/icon-Mikan.png" alt="mikan" class="icon"/>
        </template>
      </el-button>
      <el-button
          @click="aniBTShow"
          text bg>
        <template #icon>
          <img src="@/icon/icon-AniBT.png" alt="ani-bt" class="icon"/>
        </template>
      </el-button>
      <el-button bg text
                 @click="animeGardenShow">
        <template #icon>
          <img src="@/icon/icon-AnimeGarden.png" alt="anime-garden" class="icon"/>
        </template>
      </el-button>
    </div>
    <div class="standby-chain">
      <el-tag size="small" type="primary">主 RSS · {{ props.ani.subgroup || '未知字幕组' }}</el-tag>
      <template v-for="(s, i) in standbyRss" :key="i">
        <el-icon class="chain-arrow"><ArrowRightBold/></el-icon>
        <el-tag size="small" type="info">备{{ i + 1 }} · {{ s.label || '未知字幕组' }}</el-tag>
      </template>
      <el-text v-if="!standbyRss.length" size="small" type="info">
        暂未配置备用字幕组；添加后同一集将按「主 → 备」顺序补位
      </el-text>
    </div>
    <div class="standby-table-wrapper">
      <el-table :data="standbyRss" height="100%" size="small">
        <template #empty>
          <el-empty :image-size="56"
                    description="点击上方 + 或来源图标添加备用字幕组"/>
        </template>
        <el-table-column fixed label="字幕组" min-width="100px">
          <template #default="it">
            <div v-if="editIndex !== it.$index">
              {{ standbyRss[it.$index].label }}
            </div>
            <div v-else>
              <el-input v-model:model-value="standbyRss[it.$index].label" placeholder="未知字幕组"/>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="RSS" min-width="400px">
          <template #default="it">
            <div v-if="editIndex !== it.$index">
              <el-text line-clamp="1" size="small" truncated>
                {{ standbyRss[it.$index].url }}
              </el-text>
            </div>
            <div v-else>
              <el-input v-model:model-value="standbyRss[it.$index].url" placeholder="https://xxx.xxx" type="textarea"
                        size="small"
                        autosize/>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="偏移" width="150px">
          <template #default="it">
            <div v-if="editIndex !== it.$index">
              {{ standbyRss[it.$index].offset }}
            </div>
            <el-input-number v-else v-model:model-value="standbyRss[it.$index].offset" size="small"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300">
          <template #default="it">
            <div class="flex">
              <el-button bg text icon="Edit" @click="editIndex = it.$index" v-if="editIndex !== it.$index"/>
              <el-button bg text icon="Check" @click="normalize" type="primary" v-else/>
              <el-button bg text @click="del(it.$index)" icon="Delete" type="danger"/>
              <el-button :disabled="it.$index < 1" bg icon="ArrowUpBold" text type="primary"
                         @click="move(it.$index,-1)"/>
              <el-button :disabled="it.$index >= standbyRss.length-1" bg icon="ArrowDownBold" text type="primary"
                         @click="move(it.$index,1)"/>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from "vue";
import {ArrowRightBold} from "@element-plus/icons-vue";
import MikanView from "./MikanView.vue";
import AniBTView from "@/view/home/AniBTView.vue";
import AnimeGardenView from "@/view/home/AnimeGardenView.vue";
import * as http from "@/js/http.js";

const props = defineProps(['ani'])
const editIndex = ref(-1)
const aniBTRef = ref()
const mikanRef = ref()
const animeGardenRef = ref()
const config = ref({
  standbyRss: true
})

const standbyRss = computed({
  get: () => Array.isArray(props.ani.standbyRssList) ? props.ani.standbyRssList : [],
  set: value => {
    props.ani.standbyRssList = value
  }
})

// 旧订阅未设置补位模式时按 default（跟随全局）展示
const modeValue = computed(() => props.ani.standbyMode || 'default')

const modeOptions = computed(() => {
  const options = [
    {
      value: 'replace',
      label: '洗版（优先替换）',
      desc: '按「主 → 备」顺序补位；优先字幕组后来发布同一集时，自动替换已下载的低优先级版本'
    },
    {
      value: 'sticky',
      label: '不覆盖（先到先得）',
      desc: '同一集一旦下载即定稿；优先字幕组后来发布同集也不替换、不重复下载'
    },
    {
      value: 'coexist',
      label: '共存（全部保留）',
      desc: '同一集保留所有字幕组的版本，互不替换'
    }
  ]
  if (!props.ani.standbyMode || props.ani.standbyMode === 'default') {
    options.unshift({
      value: 'default',
      label: `跟随全局设置（当前：${config.value.coexist ? '共存' : '洗版'}）`,
      desc: '沿用「设置 → RSS 设置」中的全局开关；手动选择任一模式后即固化为本订阅独立配置'
    })
  }
  return options
})

onMounted(() => {
  http.config()
      .then(res => {
        config.value = res.data;
      })
})

let plus = () => {
  if (!Array.isArray(props.ani.standbyRssList)) {
    props.ani.standbyRssList = []
  }
  let object = {
    label: '未知字幕组',
    url: '',
    offset: props.ani.offset
  }
  standbyRss.value.push(object)
  editIndex.value = standbyRss.value.length - 1
  return object
}

let del = (index) => {
  editIndex.value = -1
  standbyRss.value = standbyRss.value.filter((s, i) => i !== index)
}

const normalize = () => {
  editIndex.value = -1
  standbyRss.value = standbyRss.value
      .map(it => {
        it.url = it.url.trim()
        return it;
      })
      .filter(it => it.url !== '')
}

let move = (index, offset) => {
  let v = standbyRss.value[index]
  standbyRss.value[index] = standbyRss.value[index + offset]
  standbyRss.value[index + offset] = v
}

let mikanCallback = v => {
  let {subgroup, match, url} = v

  let later = plus()
  later.url = url
  later.label = subgroup

  let newMatch = JSON.parse(match).map(s => `{{${subgroup}}}:${s}`)

  // 剔除旧的同字幕组规则
  props.ani.match = props.ani.match.filter(it => it.indexOf(`{{${subgroup}}}:`) !== 0)

  props.ani.match.push(...newMatch)

  editIndex.value = -1
}

let animeGardenShow = () => {
  let bgmUrl = props.ani.bgmUrl;
  animeGardenRef.value?.show(bgmUrl)
}

let aniBTShow = () => {
  let bgmUrl = props.ani.bgmUrl;
  aniBTRef.value?.show(bgmUrl)
}

defineExpose({normalize})

</script>

<style scoped>
.standby-rss-view {
  height: 500px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.standby-alert {
  margin-bottom: 8px;
}

.standby-mode {
  flex-shrink: 0;
  margin-bottom: 10px;
}

.standby-mode-title {
  margin-bottom: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.standby-mode-group {
  display: flex;
  flex-direction: column;
  align-items: stretch; /* 覆盖 EP .el-radio-group 默认的 align-items:center，避免卡片按内容宽度收缩后水平居中错位 */
  gap: 6px;
  width: 100%;
}

.standby-mode-item {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 7px 10px;
  align-items: flex-start;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  transition: border-color .2s;
}

.standby-mode-item.is-checked {
  border-color: var(--el-color-primary);
}

.standby-mode-item :deep(.el-radio__label) {
  white-space: normal;
}

.mode-text {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
}

.mode-label {
  font-weight: 600;
  line-height: 1.4;
}

.mode-desc {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.standby-toolbar {
  flex-shrink: 0;
  width: 100%;
  margin-bottom: 8px;
}

.standby-chain {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.chain-arrow {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.standby-table-wrapper {
  flex: 1;
  min-height: 160px;
}

.standby-spacer {
  margin: 3px;
}

.standby-action-spacer {
  margin-left: 4px;
}

.icon {
  width: 24px;
  height: 24px;
  border-radius: 8px;
}
</style>

<template>
  <el-dialog v-model="batchAdditionDialogVisible" align-center center title="正在批量添加订阅"
             width="500"
             :close-on-click-modal="false"
             :close-on-press-escape="false"
             :show-close="batchFinished">
    <div>
      <el-progress :percentage="batchTotal ? Number.parseInt((batchAdditionNum / batchTotal) * 100.0) : 0"/>
    </div>
    <div>
      {{ batchAdditionNum }} / {{ batchTotal }}
    </div>
    <div v-if="batchFinished" class="batch-result">
      <el-text size="small">
        成功 {{ batchSuccessCount }} 部，失败 {{ batchFailCount }} 部
      </el-text>
      <el-scrollbar max-height="220px" class="batch-result-scroll">
        <div v-for="(result, index) in batchResults" :key="index" class="batch-result-row">
          <el-tag size="small" :type="result.ok ? 'success' : 'danger'">
            {{ result.ok ? '成功' : '失败' }}
          </el-tag>
          <el-text size="small" truncated class="batch-result-name">{{ result.name }}</el-text>
          <el-text v-if="!result.ok" size="small" type="danger" class="batch-result-message">
            {{ result.message }}
          </el-text>
        </div>
      </el-scrollbar>
    </div>
    <div v-if="batchFinished" class="dialog-footer">
      <el-button bg text @click="batchAdditionDialogVisible = false">关闭</el-button>
    </div>
  </el-dialog>
  <el-dialog v-model="matchDialogVisible" align-center center title="匹配" width="auto">
    <el-alert
        :closable="false"
        class="match-hint"
        show-icon
        title="同一选项里的多个标签需同时命中才会下载（AND）；只要有一个标签没命中，该资源就会被过滤。拿不准请选「全部」。"
        type="info"/>
    <div class="match-content">
      <el-radio-group v-model="addAni.match">
        <div v-for="(regexItems, index) in regexList" :key="index" class="match-item">
          <el-radio :value="JSON.stringify(regexItems.map(it => it.regex))">
            <div class="match-option">
              <template v-if="regexItems.length">
                <el-tag v-for="regexItem in regexItems" :key="regexItem.label"
                        size="small" class="tag-margin">
                  {{ regexItem.label }}
                </el-tag>
              </template>
              <el-tag v-else size="small" type="success">全部</el-tag>
              <el-tooltip
                  v-if="regexItems.length && matchMeta(index).sampleTitle"
                  :content="`命中样例: ${matchMeta(index).sampleTitle}`"
                  placement="top"
                  :show-after="150">
                <el-tag size="small" type="info" class="match-count">
                  近期 {{ matchMeta(index).count }} 条
                </el-tag>
              </el-tooltip>
            </div>
          </el-radio>
        </div>
      </el-radio-group>
    </div>
    <div class="dialog-footer">
      <el-button icon="Check" @click="async ()=>{
          emit('callback', addAni)
          dialogVisible = false
          matchDialogVisible = false
      }" text bg>确定
      </el-button>
    </div>
  </el-dialog>
  <el-dialog v-model="dialogVisible" center title="Mikan">
    <el-checkbox-group v-model="rssList">
      <div class="content-wrapper">
        <div class="search-section">
          <div class="search-header">
            <el-input v-model:model-value="text" clearable placeholder="请输入搜索标题"
                      prefix-icon="Search"
                      @clear="()=>{
                        text = ''
                        search()
                      }"
                      @keyup.enter="search"/>
            <el-button :loading="searchLoading" bg icon="Search" text @click="search">搜索</el-button>
          </div>
          <div v-if="data.seasons.length" class="flex season-selector">
            <el-select v-model:model-value="seasonSelect" class="season-select"
                       :disabled="text.length > 0 || loading"
                       @change="change">
              <el-option v-for="season in data.seasons" :key="season['seasonLabel']"
                         :label="season['seasonLabel']" :value="season['seasonLabel']"/>
            </el-select>
            <el-button :disabled="rssList.length < 1" bg icon="Plus" text @click="batchAddition">批量添加</el-button>
          </div>
        </div>
        <div v-loading="loading" class="scroll-container">
          <el-tabs v-model="activeName" class="week-tabs">
            <el-tab-pane v-for="week in data.weeks" :key="week.weekLabel"
                         :label="week.weekLabel" :name="week.weekLabel" lazy>
              <el-scrollbar class="week-pane-scrollbar">
                <div class="collapse-content">
                  <el-collapse accordion @change="collapseChange">
                    <el-collapse-item v-for="it in week.items" :name="it.url">
                      <template #title>
                        <div class="flex collapse-title">
                          <img :src="proxyImage(it['cover'])" class="cover" @click.stop="open(it.url)">
                          <div class="flex collapse-title">
                            <el-text :truncated="false" line-clamp="1" size="small"
                                     class="title-text">
                              {{ it.title }}
                            </el-text>
                          </div>
                          <div v-if="it['score'] > 0" class="score-margin">
                            <h4 class="score-color">
                              {{ it['score'].toFixed(1) }}
                            </h4>
                          </div>
                          <el-badge v-if="it['exists']" class="item badge-margin" type="primary"
                                    value="已订阅"/>
                        </div>
                      </template>
                      <div v-if="selectName === it.url" v-loading="groupLoading"
                           class="group-content">
                        <el-collapse accordion>
                          <el-collapse-item v-for="group in groups[it.url]">
                            <template #title>
                              <div class="group-title-wrapper">
                                <div class="group-checkbox-wrapper">
                                  <el-checkbox :value="JSON.stringify(group)"
                                               :disabled="group._exists"
                                               class="checkbox-margin" @click.stop/>
                                </div>
                                <div class="group-label">
                                  <el-text style="max-width: 100px;" truncated>{{ group.label }}</el-text>
                                  &nbsp;
                                  <el-text class="mx-1" size="small">{{ group['updateDay'] }}</el-text>
                                </div>
                                <el-tooltip
                                    v-if="isWide && group['groupRegex']['tags'].length"
                                    :content="group['groupRegex']['tags'].join('、')"
                                    placement="top"
                                    :show-after="150">
                                  <div class="group-tags">
                                    <el-tag v-for="tag in group['groupRegex']['tags']"
                                            :key="tag"
                                            size="small"
                                            class="tag-margin">
                                      {{ tag }}
                                    </el-tag>
                                  </div>
                                </el-tooltip>
                                <div class="group-action">
                                  <el-tooltip v-if="group._exists" content="该番剧已订阅" placement="top">
                                    <span>
                                      <el-button bg disabled icon="Plus">
                                        添加
                                      </el-button>
                                    </span>
                                  </el-tooltip>
                                  <el-button v-else bg @click.stop="callback(group)" icon="Plus">
                                    添加
                                  </el-button>
                                </div>
                              </div>
                            </template>
                            <div class="group-items">
                              <div v-for="ti in group.items" class="item-margin">
                                <el-card shadow="never">
                                  <div>
                                    <h5>
                                      {{ ti.title }}
                                    </h5>
                                    <div class="item-footer">
                                      <p>
                                        {{ ti['formatSize'] }}
                                        {{ ti['createdAt'] }}
                                      </p>
                                      <div>
                                        <el-button :icon="DocumentCopy" bg text @click="copy(ti['magnet'])"/>
                                        <el-button :icon="DownloadIcon" bg text @click="openUrl(ti['torrent'])"/>
                                      </div>
                                    </div>
                                  </div>
                                </el-card>
                              </div>
                            </div>
                          </el-collapse-item>
                        </el-collapse>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </div>
              </el-scrollbar>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </el-checkbox-group>
  </el-dialog>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue";
import {ElMessage, ElText} from "element-plus";
import {DocumentCopy, Download as DownloadIcon} from "@element-plus/icons-vue";
import {proxyImage} from "@/js/global.js";
import * as http from "@/js/http.js";

// 标签列只在宽屏下展示（随窗口尺寸实时响应）
const isWide = ref(typeof window !== 'undefined' && window.innerWidth > 900)
const updateIsWide = () => {
  isWide.value = window.innerWidth > 900
}
onMounted(() => window.addEventListener('resize', updateIsWide))
onBeforeUnmount(() => window.removeEventListener('resize', updateIsWide))

// 批量添加订阅
let rssList = ref([]);

let groupLoading = ref(false)
let activeName = ref("")
let dialogVisible = ref(false)
let loading = ref(false)
let data = ref({
  'seasons': [],
  'items': [],
  'weeks': []
})

let seasonSelect = ref('')

let show = (ani) => {
  seasonSelect.value = ''
  dialogVisible.value = true
  text.value = ''
  data.value = {
    'seasons': [],
    'items': [],
    'weeks': []
  }
  rssList.value = []
  searchAni(ani)
  list(text.value)
}

let searchAni = ani => {
  if (!ani) {
    return
  }

  if (ani.url) {
    let url = new URL(ani.url);
    let searchParams = url.searchParams;
    let mikanId = searchParams.get("bangumiId");
    if (mikanId) {
      text.value = `id: ${mikanId}`
      return
    }
  }

  let title = ani.mikanTitle ? ani.mikanTitle : ani.title
  title = title.replace(/ ?\((19|20)\d{2}\)/g, "").trim()
  title = title.replace(/ ?\[tmdbid=(\d+)]/g, "").trim()
  if (title.length > 2) {
    text.value = title
  }
}

let text = ref('')

let searchLoading = ref(false)
let search = () => {
  if (text.value.length === 1) {
    ElMessage.error("搜索最少需要两个字符")
    return
  }
  searchLoading.value = true
  list(text.value).finally(() => {
    searchLoading.value = false
  })
}

let list = async (text, body) => {
  loading.value = true
  text = text ? text : ''
  body = body ? body : {}
  return http.mikan(text, body)
      .then(res => {
        let {seasons, weeks, totalItems} = res.data;

        if (totalItems < 1) {
          ElMessage.warning("搜索结果为空")
        }

        if (seasons.length) {
          data.value.seasons = seasons
        }
        data.value.weeks = weeks
        if (weeks.length) {
          activeName.value = weeks[0].weekLabel
        }
        for (let season of data.value.seasons) {
          if (season['select'] && !seasonSelect.value) {
            seasonSelect.value = season['seasonLabel']
            return
          }
        }
      })
      .finally(() => {
        loading.value = false
      });
}

let change = (v) => {
  let body = data.value.seasons.filter(item => item['seasonLabel'] === v)
  if (body.length) {
    list('', body[0])
  }
}

let selectName = ref('')
let groups = ref({})

let collapseChange = (v) => {
  if (!v) {
    return
  }
  selectName.value = v
  if (groups.value[v]) {
    return;
  }
  groupLoading.value = true
  http.mikanGroup(v)
      .then(res => {
        // 番剧已订阅时，禁用其下所有字幕组的选择，避免重复添加
        const bangumiExists = data.value.weeks
            .some(week => week.items.some(item => item.url === v && item.exists))
        groups.value[v] = (res.data ?? []).map(group => ({...group, _exists: bangumiExists}))
      })
      .finally(() => {
        groupLoading.value = false
      })
}


let matchDialogVisible = ref(false)

let addAni = ref({
  'url': '',
  'match': '',
  'group': ''
})

let regexList = ref([])
// 与 regexList 一一对应的近期命中条数 / 样例标题（末尾「全部」选项补 null 占位）
let regexCounts = ref([])
let regexSamples = ref([])

const matchMeta = (index) => ({
  count: regexCounts.value[index] ?? 0,
  sampleTitle: regexSamples.value[index] ?? ''
})

let callback = v => {
  const groupRegex = v.groupRegex || {}
  regexList.value = JSON.parse(JSON.stringify(groupRegex.regexList ?? []))
  regexCounts.value = [...(groupRegex.counts ?? [])]
  regexSamples.value = [...(groupRegex.sampleTitles ?? [])]

  addAni.value.url = v.rss
  addAni.value.bgmUrl = v.bgmUrl
  addAni.value.subgroup = v.label
  addAni.value.match = '[]'

  // 「全部」选项放在最后
  regexList.value.push([])
  regexCounts.value.push(null)
  regexSamples.value.push(null)
  matchDialogVisible.value = true
}

let open = url => {
  window.open(url);
}

defineExpose({show})

let emit = defineEmits(['callback'])


let batchAdditionNum = ref(0)
let batchTotal = ref(0)
let batchResults = ref([])
let batchAdditionDialogVisible = ref(false)

const batchFinished = computed(() =>
    batchTotal.value > 0 && batchAdditionNum.value >= batchTotal.value
)
const batchSuccessCount = computed(() => batchResults.value.filter(item => item.ok).length)
const batchFailCount = computed(() => batchResults.value.filter(item => !item.ok).length)

let batchAddition = async () => {
  if (!rssList.value.length) {
    return
  }

  const getBangumiId = (url) => new URL(url).searchParams.get('bangumiId')

  // 按番剧聚合，第一部作为主 RSS，其余作为备用 RSS
  const groupMap = rssList.value.reduce((acc, raw) => {
    const item = JSON.parse(raw)
    const bangumiId = getBangumiId(item['rss'])
    if (!acc[bangumiId]) {
      acc[bangumiId] = []
    }
    acc[bangumiId].push(item)
    return acc
  }, {})
  const bangumiGroups = Object.values(groupMap)

  batchAdditionNum.value = 0
  batchTotal.value = bangumiGroups.length
  batchResults.value = []
  batchAdditionDialogVisible.value = true

  let hasSuccess = false
  for (const items of bangumiGroups) {
    const name = items.map(item => item.label).filter(Boolean).join(' / ') || items[0]['rss']
    try {
      // 直接带上 subgroup/bgmUrl，后端无需再次抓取 Mikan 页面
      let ani = {
        "url": items[0]['rss'],
        "bgmUrl": items[0]['bgmUrl'],
        "subgroup": items[0]['label'],
        "season": 1,
        "offset": 0,
        "title": "",
        "exclude": [],
        "totalEpisodeNumber": 0,
        "match": [],
        "type": "mikan"
      }

      ani = (await http.rssToAni(ani)).data
      // rssToAni 的返回值可能缺省这两个字段，再次确保与所选字幕组一致
      ani.subgroup = items[0]['label']
      ani.bgmUrl = items[0]['bgmUrl']
      if (items.length > 1) {
        ani.standbyRssList = items.slice(1).map(o => ({
          label: o.label,
          url: o['rss'],
          offset: 0
        }))
      }
      const res = await http.addAni(ani)
      batchResults.value.push({name, ok: true, message: res.message})
      hasSuccess = true
    } catch (e) {
      // 单个失败不影响其余订阅继续添加
      batchResults.value.push({name, ok: false, message: e?.message || String(e)})
    } finally {
      batchAdditionNum.value += 1
    }
  }

  if (hasSuccess) {
    ElMessage.success(`成功添加 ${batchSuccessCount.value}/${batchTotal.value} 部`)
    // 已订阅状态刷新与字幕组缓存失效，避免继续重复选择
    window.$reLoadList?.()
    rssList.value = []
    groups.value = {}
  }
}

let copy = (v) => {
  const input = document.createElement('input');
  input.value = v;
  document.body.appendChild(input);
  input.select();
  document.execCommand('copy');
  document.body.removeChild(input);
  ElMessage.success('已复制')
}

let openUrl = (url) => window.open(url)

</script>

<style scoped>
.el-collapse {
  --el-collapse-header-height: 55px;
}

.tag-margin {
  margin-right: 4px;
}

.dialog-footer {
  display: flex;
  width: 100%;
  justify-content: end;
}

.content-wrapper {
  min-height: 300px;
}

.search-section {
  margin: 4px;
}

.search-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.season-selector {
  margin-top: 8px;
  width: 100%;
  justify-content: space-between;
}

.season-select {
  max-width: 140px;
}

.scroll-container {
  margin: 8px 0 4px 0;
  height: 600px;
}

.week-tabs {
  margin: 0 4px;
}

.collapse-content {
  margin-left: 15px;
}

.collapse-title {
  align-items: center;
}

.title-text {
  margin-left: 6px;
  line-height: 1.6;
  font-weight: bold;
}

.score-margin {
  margin-left: 4px;
}

.score-color {
  color: #E800A4;
}

.badge-margin {
  margin-left: 4px;
}

.group-content {
  margin-left: 15px;
  min-height: 50px;
}

.group-title-wrapper {
  width: 100%;
  display: flex;
  justify-content: space-between;
}

.group-checkbox-wrapper {
  height: 100%;
}

.checkbox-margin {
  margin-right: 8px;
}

.group-label {
  display: flex;
  align-items: center;
  flex: 1;
  text-align: start;
}

.group-action {
  display: flex;
  align-items: center;
  margin-right: 14px;
  margin-left: 4px;
}

.group-items {
  margin-left: 15px;
}

.item-margin {
  margin-bottom: 4px;
}

.item-footer {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.cover {
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  width: 45px;
  height: 45px;
  object-fit: cover;
  flex-shrink: 0;
}

.match-content {
  max-width: 560px;
  min-width: 240px;
  margin-bottom: 4px;
}

.match-hint {
  max-width: 560px;
  margin-bottom: 10px;
}

.match-item {
  display: block;
  margin: 0 0 8px 0;
}

.match-option {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 2px;
}

.match-count {
  cursor: default;
}

.group-tags {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.batch-result {
  margin-top: 12px;
}

.batch-result-scroll {
  margin-top: 6px;
}

.batch-result-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 0;
}

.batch-result-name {
  flex: 0 1 180px;
}

.batch-result-message {
  flex: 1;
  min-width: 0;
}
</style>

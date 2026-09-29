<template>
  <el-dialog v-model="dialogVisible" align-center center title="剧集组" width="440">
    <el-scrollbar v-loading="loading" class="tmdb-scrollbar">
      <el-card v-for="group in groupList" :key="group.id" shadow="never" class="tmdb-card">
        <template #header>
          <div class="flex tmdb-header">
            <div class="tmdb-title">
              <el-link :href="`https://www.themoviedb.org/tv/${props.ani.tmdb['id']}/episode_group/${group.id}`"
                       target="_blank">
                {{ group.name }}
              </el-link>
              <el-badge v-if="props.ani.tmdb['tmdbGroupId'] === group.id" class="item tmdb-badge"
                        type="primary" value="已选择"/>
            </div>
            <div>
              <el-button :icon="expandedId === group.id ? 'ArrowUp' : 'ArrowDown'"
                         text circle
                         @click="toggleDetail(group)"/>
              <el-button icon="Select" text @click="select(group)"/>
            </div>
          </div>
        </template>
        <template #default>
          <div class="flex tmdb-content">
            <el-tag type="success">
              {{ group['typeName'] }}
            </el-tag>
            <div>
              <el-tag class="tmdb-tag-spacer">
                {{ group['groupCount'] }} 组
              </el-tag>
              <el-tag>
                {{ group['episodeCount'] }} 集
              </el-tag>
            </div>
          </div>
          <div v-if="expandedId === group.id"
               v-loading="loadingId === group.id"
               class="tmdb-seasons">
            <div v-for="season in detailMap[group.id] || []"
                 :key="season.order"
                 class="tmdb-season"
                 :class="{'is-active': season.order === props.ani.season}"
                 @click="selectSeason(group, season)">
              <div class="tmdb-season-main">
                <el-tag size="small"
                        :type="season.order === props.ani.season ? 'primary' : 'info'">
                  第 {{ season.order }} 季
                </el-tag>
                <span class="tmdb-season-name">{{ season.name }}</span>
                <el-tag v-if="season.order === props.ani.season" size="small" type="success">
                  当前季
                </el-tag>
              </div>
              <div class="tmdb-season-meta">
                <span>{{ season.episodeCount }} 集</span>
                <span v-if="season.startAirDate">
                  · {{ season.startAirDate }}<template
                    v-if="season.endAirDate && season.endAirDate !== season.startAirDate">
                  ~ {{ season.endAirDate }}
                </template>
                </span>
                <span v-if="season.originSeasonNumber">
                  · 原 S{{ pad(season.originSeasonNumber) }}E{{ pad(season.originEpisodeStart) }}<template
                    v-if="season.originEpisodeEnd !== season.originEpisodeStart">
                  -{{ pad(season.originEpisodeEnd) }}
                </template>
                </span>
              </div>
            </div>
            <el-empty v-if="loadingId !== group.id && detailMap[group.id]?.length === 0"
                      description="无分段数据"
                      :image-size="40"/>
          </div>
        </template>
      </el-card>
    </el-scrollbar>
  </el-dialog>
</template>

<script setup>
import {ref} from "vue";
import {ElMessage, ElMessageBox} from "element-plus";
import * as http from "@/js/http.js";

let dialogVisible = ref(false)

let groupList = ref([])

let loading = ref(false)
let loadingId = ref(null)

// groupId -> 分段列表
let detailMap = ref({})
// 当前展开的 groupId
let expandedId = ref(null)

let pad = (number) => number === null || number === undefined
    ? ''
    : String(number).padStart(2, '0')

let show = () => {
  loading.value = true
  dialogVisible.value = true
  detailMap.value = {}
  expandedId.value = null
  http.getThemoviedbGroup(props.ani)
      .then(res => {
        groupList.value = res.data
      })
      .finally(() => {
        loading.value = false
      })
}

let loadDetail = (groupId) => {
  loadingId.value = groupId
  http.getThemoviedbGroupDetail(groupId)
      .then(res => {
        detailMap.value[groupId] = res.data || []
      })
      .finally(() => {
        loadingId.value = null
      })
}

let toggleDetail = (group) => {
  if (expandedId.value === group.id) {
    expandedId.value = null
    return
  }
  expandedId.value = group.id
  if (!detailMap.value[group.id]) {
    loadDetail(group.id)
  }
}

let applyGroup = (groupId) => {
  props.ani.tmdb['tmdbGroupId'] = groupId
  ElMessage.success('已选择剧集组')
  dialogVisible.value = false
}

let select = async (group) => {
  let seasons = detailMap.value[group.id]
  if (!seasons) {
    try {
      const res = await http.getThemoviedbGroupDetail(group.id)
      seasons = res.data || []
      detailMap.value[group.id] = seasons
    } catch (e) {
      // 详情获取失败时不阻断剧集组选择
      applyGroup(group.id)
      return
    }
  }

  const hit = seasons.some(it => it.order === props.ani.season)
  if (seasons.length > 0 && !hit) {
    const first = seasons[0]
    try {
      await ElMessageBox.confirm(
          `当前季 ${props.ani.season ?? '未设置'} 在「${group.name}」中不存在，是否将季切换为 ${first.order}（${first.name}）？`,
          '季不匹配',
          {
            confirmButtonText: '切换',
            cancelButtonText: '取消',
            type: 'warning',
            confirmButtonClass: 'is-text is-has-bg el-button--primary',
            cancelButtonClass: 'is-text is-has-bg'
          }
      )
      props.ani.season = first.order
    } catch (e) {
      return
    }
  }

  applyGroup(group.id)
}

// 点选具体分段：同时确定剧集组与季
let selectSeason = (group, season) => {
  props.ani.tmdb['tmdbGroupId'] = group.id
  props.ani.season = season.order
  ElMessage.success(`已选择剧集组，季切换为 ${season.order}（${season.name}）`)
  dialogVisible.value = false
}

defineExpose({show})
let props = defineProps(['ani'])
</script>

<style scoped>
.tmdb-scrollbar {
  height: 400px;
}

.tmdb-card {
  margin-bottom: 4px;
}

.tmdb-header {
  width: 100%;
  justify-content: space-between;
}

.tmdb-title {
  display: flex;
  align-items: center;
}

.tmdb-badge {
  margin-left: 4px;
}

.tmdb-content {
  width: 100%;
  justify-content: space-between;
}

.tmdb-tag-spacer {
  margin-right: 4px;
}

.tmdb-seasons {
  margin-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 6px;
}

.tmdb-season {
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
}

.tmdb-season:hover {
  background: var(--el-fill-color-light);
}

.tmdb-season.is-active {
  background: var(--el-color-primary-light-9);
}

.tmdb-season-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tmdb-season-name {
  font-size: var(--el-font-size-small);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tmdb-season-meta {
  margin-top: 4px;
  margin-left: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-secondary);
}
</style>

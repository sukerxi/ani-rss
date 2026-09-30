<template>
  <el-card shadow="never">
    <div class="list-card-content">
      <div class="list-card-image-container">
        <img :src="toApiFile(item['cover'])"
             :alt="item.title"
             class="list-card-image"
             @click="handleCoverClick"/>
      </div>
      <div class="list-card-info">
        <div class="list-card-info-inner">
          <div class="flex">
            <el-tooltip :content="item.title" placement="top">
              <el-text :line-clamp="1"
                       @click="openBgmUrl(item)"
                       class="list-card-title"
                       truncated>
                {{ item.title }}
              </el-text>
            </el-tooltip>
          </div>
          <div class="list-card-score-container" v-if="showScore">
            <h4 class="list-card-score" @click="emit('rate', item)">
              {{ item['score'].toFixed(1) }}
            </h4>
          </div>
          <el-text v-else
                   line-clamp="2"
                   size="small"
                   class="list-card-url">
            {{ decodeURLComponentSafe(item.url) }}
          </el-text>
          <div class="list-card-tags">
            <el-tag>
              第 {{ item.season }} 季
            </el-tag>
            <el-tag type="success" v-if="item.enable">
              已启用
            </el-tag>
            <el-tag type="info" v-else>
              未启用
            </el-tag>
            <el-tag type="info">
              <el-tooltip :content="item['subgroup']">
                <el-text line-clamp="1" size="small" class="list-card-subgroup">
                  {{ item['subgroup'] ? item['subgroup'] : '未知字幕组' }}
                </el-text>
              </el-tooltip>
            </el-tag>
            <el-tag type="warning">
              {{ item['currentEpisodeNumber'] }} /
              {{ item['totalEpisodeNumber'] ? item['totalEpisodeNumber'] : '*' }}
            </el-tag>
            <el-tag type="danger" v-if="item.ova">
              ova
            </el-tag>
            <el-tag type="danger" v-else>
              tv
            </el-tag>
            <el-tag v-if="item.standbyRssList.length > 0">
              备用RSS
            </el-tag>
          </div>
          <el-text v-if="showLastDownloadTime && item.lastDownloadTime > 0" size="small"
                   type="info">
            {{ item.lastDownloadFormat }}
          </el-text>
        </div>
        <div class="list-card-actions">
          <el-tooltip v-if="showPlaylist" content="播放列表" placement="top">
            <el-button text bg aria-label="播放列表" @click="emit('playlist', item)">
              <el-icon>
                <Files/>
              </el-icon>
            </el-button>
          </el-tooltip>
          <div class="list-card-spacer" v-if="showPlaylist"></div>
          <el-tooltip content="更换封面" placement="top">
            <el-button bg text aria-label="更换封面" @click="emit('cover', item)">
              <el-icon>
                <Picture/>
              </el-icon>
            </el-button>
          </el-tooltip>
          <div class="list-card-spacer"></div>
          <el-tooltip content="编辑" placement="top">
            <el-button bg text aria-label="编辑" @click="emit('edit', item)">
              <el-icon>
                <EditIcon/>
              </el-icon>
            </el-button>
          </el-tooltip>
          <div class="list-card-spacer"></div>
          <el-tooltip content="删除" placement="top">
            <el-button type="danger" text bg aria-label="删除" @click="emit('del', [item])">
              <el-icon>
                <Delete/>
              </el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import {coverClickAction, showLastDownloadTime, showPlaylist, showScore, toApiFile} from "@/js/global.js";
import {Delete, Edit as EditIcon, Files, Picture} from "@element-plus/icons-vue";

let openBgmUrl = (it) => {
  if (it.bgmUrl?.length) {
    window.open(it.bgmUrl, '_blank', 'noopener')
    return
  }
  if (it.title?.length) {
    let title = it.title.replace(/ ?\((19|20)\d{2}\)/g, "").trim()
    title = title.replace(/ ?\[tmdbid=(\d+)]/g, "").trim()
    window.open(`https://bgm.tv/subject_search/${encodeURIComponent(title)}?cat=2`, '_blank', 'noopener')
  }
}

let decodeURLComponentSafe = (str) => {
  return decodeURIComponent(str.replace('+', ' '));
}

const emit = defineEmits(['edit', 'playlist', 'cover', 'del', 'rate'])
let props = defineProps(["item"])

const handleCoverClick = () => {
  const action = ['edit', 'playlist', 'cover'].includes(coverClickAction.value)
      ? coverClickAction.value
      : 'cover'
  emit(action, props.item)
}
</script>

<style scoped>
.list-card-content {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
}

.list-card-image-container {
  height: 100%;
  flex-shrink: 0;
}

.list-card-image {
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  height: 130px;
  width: 92px;
}

.list-card-info {
  flex: 1 1 auto;
  min-width: 0;
  position: relative;
}

.list-card-info-inner {
  margin-left: 10px;
  padding-right: 34px;
  min-width: 0;
}

.list-card-info-inner .flex {
  min-width: 0;
}

.list-card-title {
  width: 100%;
  line-height: 1.6;
  letter-spacing: 0.0125em;
  font-weight: 500;
  font-size: 0.97em;
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.list-card-score-container {
  margin-bottom: 8px;
}

.list-card-score {
  font-weight: 700;
  cursor: pointer;
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
}

.list-card-url {
  width: 100%;
}

.list-card-tags {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-gap: 4px;
}

.list-card-subgroup {
  width: 100%;
  color: var(--el-color-info);
}

.list-card-actions {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  flex-direction: column;
  position: absolute;
  right: 0;
  bottom: 0;
}

.list-card-spacer {
  height: 5px;
}

@media (max-width: 800px) {
  .list-card-tags {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

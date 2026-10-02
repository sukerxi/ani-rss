<template>
  <el-dialog v-model="jsDialogVisible" align-center center title="自定义JS" width="800">
    <el-input v-model:model-value="props.config['customJs']" :rows="6"
              type="textarea"/>
    <div class="flex justify-end full-width mt-8">
      <el-link type="primary" href="https://github.com/wushuo894/ani-rss-css"
               target="_blank">更多CSS
      </el-link>
    </div>
    <div class="flex justify-end full-width mt-8">
      <el-button bg icon="Close" text @click="jsDialogVisible = false">关闭</el-button>
    </div>
  </el-dialog>
  <el-dialog v-model="cssDialogVisible" align-center center title="自定义CSS" width="800">
    <el-input v-model:model-value="props.config['customCss']" :rows="6"
              type="textarea"/>
    <div class="flex justify-end full-width mt-8">
      <el-button bg icon="Close" text @click="cssDialogVisible = false">关闭</el-button>
    </div>
  </el-dialog>
  <SettingsItem label="外观">
    <el-radio-group v-model="store">
      <el-radio-button label="自动" value="auto">
        <template #default>
          <el-icon>
            <Adjust/>
          </el-icon>
        </template>
      </el-radio-button>
      <el-radio-button label="浅色" value="light">
        <el-icon>
          <Sun/>
        </el-icon>
      </el-radio-button>
      <el-radio-button label="深色" value="dark">
        <el-icon>
          <Moon/>
        </el-icon>
      </el-radio-button>
    </el-radio-group>
  </SettingsItem>
  <SettingsItem label="主题">
    <div class="theme-preset-list">
      <button
          v-for="item in themePresets"
          :key="item.name"
          type="button"
          class="theme-preset"
          :class="{'is-active': appTheme === item.name}"
          @click="applyTheme(item.name)">
        <span class="theme-preset-swatch" :style="{background: item.gradient}">
          <el-icon v-if="appTheme === item.name" class="theme-preset-check">
            <Check/>
          </el-icon>
        </span>
        <span class="theme-preset-name">{{ item.label }}</span>
      </button>
    </div>
  </SettingsItem>
  <SettingsItem label="强调色">
    <el-color-picker v-model="color" :predefine="predefineColors"
                     @blur="applyCustomColor(color)"
                     @change="applyCustomColor(color)"
                     @active-change="applyCustomColor"/>
  </SettingsItem>
  <SettingsItem label="排序">
    <el-select v-model="props.config['sortType']" class="width-150">
      <el-option value="SCORE" label="评分"/>
      <el-option value="PINYIN" label="拼音"/>
      <el-option value="DOWNLOAD_TIME" label="更新时间"/>
    </el-select>
  </SettingsItem>
  <SettingsItem label="订阅布局">
    <el-select v-model="subscriptionViewMode" class="width-150">
      <el-option label="自动（大屏卡片 / 手机列表）" :value="null"/>
      <el-option label="卡片" value="cover"/>
      <el-option label="列表" value="card"/>
    </el-select>
  </SettingsItem>
  <SettingsItem label="点击封面">
    <el-select v-model="coverClickAction" class="width-150">
      <el-option label="编辑订阅" value="edit"/>
      <el-option label="视频列表" value="playlist"/>
      <el-option label="编辑封面" value="cover"/>
    </el-select>
  </SettingsItem>
  <SettingsItem label="启动页">
    <el-select v-model="startupPage" class="width-150">
      <el-option label="首页" value="/home"/>
      <el-option label="订阅页" value="/subscriptions"/>
    </el-select>
  </SettingsItem>
  <SettingsItem label="最大内容宽度">
    <el-input-number v-model="maxContentWidth"
                     :min="1200">
      <template #suffix>
        <span>px</span>
      </template>
    </el-input-number>
  </SettingsItem>
  <SettingsItem label="其他">
    <el-checkbox v-model="showScore" label="显示评分"/>
    <el-checkbox v-model="showWeek" label="按星期展示"/>
    <el-checkbox v-model="showPlaylist" label="显示视频列表"/>
    <el-checkbox v-model="showLastDownloadTime" label="显示更新时间"/>
  </SettingsItem>
  <SettingsItem label="自定义">
    <el-button bg @click="jsDialogVisible = true">
      <template #icon>
        <Js/>
      </template>
      JavaScript
    </el-button>
    <el-button bg @click="cssDialogVisible = true">
      <template #icon>
        <Css3Alt/>
      </template>
      CSS
    </el-button>
  </SettingsItem>
  <SettingsItem label="WebUI">
    <UploadView url="api/webui/upload" :callback="callback" :extensions="['zip']">
      <el-button bg icon="Upload">选择文件并上传</el-button>
    </UploadView>
  </SettingsItem>
</template>

<script setup>
import {ref} from "vue";
import {Adjust, Css3Alt, Js, Moon, Sun} from "@vicons/fa";
import {Check} from "@element-plus/icons-vue";
import {
  appTheme,
  applyCustomColor,
  applyTheme,
  color,
  coverClickAction,
  maxContentWidth,
  showLastDownloadTime,
  showPlaylist,
  showScore,
  showWeek,
  startupPage,
  store,
  subscriptionViewMode,
  themePresets
} from "@/js/global.js";
import {ElMessage} from "element-plus";
import UploadView from "@/view/custom/UploadView.vue";
import SettingsItem from "@/view/custom/SettingsItem.vue";

let predefineColors = ref([
  '#ff7e5f', '#ff5f8d', '#ffa45c', '#b06bff', '#35b6a8',
  '#409eff', '#109D58', '#BF3545', '#CB7574',
  '#9AAEC7', '#2EC5B6', '#1C1C1C', '#F7B1A9',
  '#B18874', '#E9BA86', '#F68F6C', '#F0458B',
  '#C35653', '#40494E', '#6F0000', '#8D3647',
  '#E6C5D0', '#2377B3', '#49312D', '#7C9AB6',
  '#A5B18D', '#E8662A', '#AB5D50'
])

let jsDialogVisible = ref(false)
let cssDialogVisible = ref(false)

let callback = res => {
  let {code, message} = res
  if (code === 200) {
    ElMessage.success(message)
    return
  }
  ElMessage.error(message)
}

let props = defineProps(['config'])
</script>

<style scoped>
.justify-end {
  justify-content: end;
}

.mt-8 {
  margin-top: 8px;
}

/* 主题预设色卡 */
.theme-preset-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.theme-preset {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
}

.theme-preset-swatch {
  position: relative;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  box-shadow: 0 6px 14px -6px rgba(0, 0, 0, 0.35);
  border: 2px solid transparent;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.theme-preset:hover .theme-preset-swatch {
  transform: translateY(-2px);
}

.theme-preset.is-active .theme-preset-swatch {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--brand-soft), 0 8px 16px -8px rgba(0, 0, 0, 0.35);
}

.theme-preset-check {
  position: absolute;
  inset: 0;
  margin: auto;
  font-size: 20px;
  color: #ffffff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.35));
}

.theme-preset-name {
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}

.theme-preset.is-active .theme-preset-name {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>

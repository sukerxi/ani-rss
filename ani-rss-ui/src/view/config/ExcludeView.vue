<template>
  <el-dialog title="添加正则" v-if="add" v-model:model-value="add" center align-center width="300">
    <div>
      <SettingsItem label="字幕组">
        <el-input placeholder="留空匹配所有字幕组" v-model="subgroup"></el-input>
      </SettingsItem>
      <div class="exclude-spacer"></div>
      <SettingsItem label="正则">
        <el-input placeholder="如 720、简、\d-\d" v-model="exclude"></el-input>
      </SettingsItem>
    </div>
    <div class="flex exclude-dialog-footer">
      <el-button bg text @click="addExclude" icon="Plus">添加</el-button>
    </div>
  </el-dialog>
  <div class="full-width">
    <div class="gap-2">
      <el-tag v-if="!props.exclude.length"
              type="info"
              class="exclude-tag">
        无
      </el-tag>
      <el-tag
          v-for="tag in props.exclude"
          :key="tag"
          closable
          :disable-transitions="false"
          @close="handleClose(tag)"
          class="exclude-tag"
      >
        <el-tooltip :content="tag">
          <el-text line-clamp="1" size="small" class="exclude-tag-text">
            {{ tag }}
          </el-text>
        </el-tooltip>
      </el-tag>
      <el-button bg
                 icon="Plus"
                 size="small"
                 class="exclude-tag"
                 text
                 @click="()=> add = true"
      />
      <el-button
          v-if="props.exclude.length"
          bg
          icon="Delete"
          size="small"
          class="exclude-delete-button"
          text
          type="danger"
          @click="() => props.exclude.length = 0"
      />
    </div>
    <div class="flex exclude-footer">
      <el-button bg text size="small" @click="importExclude" v-if="props.importExclude"
                 :disabled="disabledImportExclude" :loading="importExcludeLoading" icon="Download">
        导入全局排除
      </el-button>
      <el-text class="mx-1" size="small" v-if="props.showText">
        支持&nbsp;
        <el-link
            class="exclude-link"
            type="primary"
            href="https://www.runoob.com/regexp/regexp-syntax.html"
            target="_blank">
          正则表达式
        </el-link>
      </el-text>
    </div>
  </div>
</template>

<script setup>
import SettingsItem from "@/view/custom/SettingsItem.vue";
import {ref} from "vue";
import {ElMessage} from "element-plus";
import {config} from "@/js/http.js";

const handleClose = (tag) => {
  props.exclude.splice(props.exclude.indexOf(tag), 1)
}

const add = ref(false)

let importExcludeLoading = ref(false)
let disabledImportExclude = ref(false)

let importExclude = () => {
  importExcludeLoading.value = true
  config()
      .then(res => {
        disabledImportExclude.value = true
        for (let it of res.data.exclude) {
          if (props.exclude.indexOf(it) > -1) {
            continue
          }
          props.exclude.push(it)
        }
      })
      .finally(() => {
        importExcludeLoading.value = false
      })

}

let subgroup = ref('')
let exclude = ref('')

let addExclude = () => {
  const regexText = exclude.value.trim()
  if (!regexText) {
    ElMessage.error('正则为空')
    return
  }
  // 提交前在浏览器侧先校验，避免非法规则保存后在轮询期静默失效
  try {
    // eslint-disable-next-line no-new
    new RegExp(regexText)
  } catch (e) {
    ElMessage.error(`正则表达式不合法: ${e.message}`)
    return
  }
  const rule = subgroup.value.trim() ? `{{${subgroup.value.trim()}}}:${regexText}` : regexText
  if (props.exclude.includes(rule)) {
    ElMessage.warning('该规则已存在')
    subgroup.value = ''
    exclude.value = ''
    add.value = false
    return
  }
  props.exclude.push(rule)
  subgroup.value = ''
  exclude.value = ''
  add.value = false
}

let props = defineProps({
  exclude: Array,
  importExclude: Boolean,
  showText: Boolean
})
</script>

<style scoped>
.exclude-spacer {
  margin: 4px;
}

.exclude-dialog-footer {
  width: 100%;
  justify-content: end;
  margin-top: 8px;
}

.exclude-tag {
  margin-right: 4px;
  margin-bottom: 4px;
}

.exclude-tag-text {
  max-width: 300px;
  color: var(--el-color-primary);
}

.exclude-delete-button {
  margin-left: 0;
  margin-bottom: 4px;
}

.exclude-footer {
  margin-top: 4px;
  width: 100%;
  justify-content: space-between;
}

.exclude-link {
  font-size: var(--el-font-size-extra-small);
}
</style>

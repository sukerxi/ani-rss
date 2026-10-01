<template>
  <div class="app-shell" :class="{'is-nav-collapsed': collapsed}">
    <aside class="app-nav">
      <div class="app-brand" @click="goHome" title="ANI-RSS">
        <img class="app-logo" src="/icon.svg" alt="ANI-RSS">
        <span class="app-brand-name">ANI-RSS</span>
      </div>
      <el-menu
          :default-active="route.path"
          :ellipsis="false"
          :collapse-transition="false"
          class="app-menu"
          router>
        <div v-for="group in navGroups" :key="group.label" class="app-menu-group">
          <div class="app-menu-group-title">{{ group.label }}</div>
          <el-menu-item
              v-for="item in group.items"
              :key="item.index"
              :index="item.index"
              :title="collapsed ? item.label : ''">
            <el-icon>
              <component :is="item.icon"/>
            </el-icon>
            <span class="app-menu-label">{{ item.label }}</span>
          </el-menu-item>
        </div>
      </el-menu>
      <div class="app-nav-footer">
        <el-tooltip :content="isDark ? '切换浅色' : '切换深色'" placement="right" :show-after="300">
          <el-button class="app-nav-action" circle bg text @click="isDark = !isDark">
            <el-icon>
              <Sunny v-if="isDark"/>
              <Moon v-else/>
            </el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip class="app-nav-collapse-btn"
                   :content="collapsed ? '展开侧栏' : '收起侧栏'"
                   placement="right"
                   :show-after="300">
          <el-button class="app-nav-action" circle bg text @click="collapsed = !collapsed">
            <el-icon>
              <DArrowRight v-if="collapsed"/>
              <DArrowLeft v-else/>
            </el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </aside>
    <main class="app-main">
      <RouterView v-slot="{ Component }">
        <KeepAlive>
          <component :is="Component"/>
        </KeepAlive>
      </RouterView>
    </main>
  </div>
</template>

<script setup>
import {onMounted} from "vue";
import {RouterView, useRoute, useRouter} from "vue-router";
import {useDark, useLocalStorage} from "@vueuse/core";
import {
  Collection,
  DArrowLeft,
  DArrowRight,
  Download,
  House,
  Moon,
  Setting,
  Sunny,
  Tickets
} from "@element-plus/icons-vue";
import {initLayout} from "@/js/global.js";

const route = useRoute()
const router = useRouter()
const isDark = useDark()

/**
 * 侧栏折叠状态（桌面端持久化）
 */
const collapsed = useLocalStorage('app-nav-collapsed', false)

const navGroups = [
  {
    label: '追番',
    items: [
      {index: '/home', label: '首页', icon: House},
      {index: '/subscriptions', label: '订阅', icon: Collection},
      {index: '/downloads', label: '下载', icon: Download}
    ]
  },
  {
    label: '系统',
    items: [
      {index: '/logs', label: '日志', icon: Tickets},
      {index: '/settings', label: '设置', icon: Setting}
    ]
  }
]

const goHome = () => {
  if (route.path !== '/home') {
    router.push('/home')
  }
}

onMounted(() => {
  initLayout()
})
</script>

<style scoped>
.app-shell {
  /* 侧栏宽度在外壳上统一定义，主内容区的最大宽度计算通过继承同一变量保持同步 */
  --app-nav-width: 132px;
  width: 100%;
  height: 100%;
  display: flex;
}

.app-shell.is-nav-collapsed {
  --app-nav-width: 72px;
}

.app-nav {
  width: var(--app-nav-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--app-nav-bg);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-right: 1px solid var(--app-nav-border);
  transition: width 0.22s ease;
}

.app-brand {
  height: 86px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  cursor: pointer;
  user-select: none;
}

.app-logo {
  width: 40px;
  height: 40px;
  padding: 7px;
  box-sizing: border-box;
  border-radius: 13px;
  background: var(--brand-gradient);
  box-shadow: 0 8px 18px -8px var(--brand-glow);
}

.app-brand-name {
  white-space: nowrap;
  background: var(--brand-gradient);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  -webkit-text-fill-color: transparent;
}

.app-menu {
  flex: 1;
  min-height: 0;
  border: 0;
  background: none;
  --el-menu-hover-bg-color: transparent;
}

.app-menu-group {
  display: flex;
  flex-direction: column;
}

.app-menu-group + .app-menu-group {
  margin-top: 6px;
}

.app-menu-group-title {
  padding: 8px 22px 2px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.app-menu :deep(.el-menu-item) {
  height: 44px;
  margin: 3px 12px;
  padding: 0 16px !important;
  border-radius: 12px;
  color: var(--el-text-color-regular);
  border: none !important;
  white-space: nowrap;
  transition: color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.app-menu :deep(.el-menu-item .el-icon) {
  transition: color 0.2s ease;
}

.app-menu :deep(.el-menu-item:hover) {
  color: var(--el-color-primary);
  background: var(--brand-soft);
}

.app-menu :deep(.el-menu-item.is-active),
.app-menu :deep(.el-menu-item.is-active:hover) {
  color: #ffffff;
  background: var(--brand-gradient);
  font-weight: 600;
  box-shadow: 0 8px 18px -8px var(--brand-glow);
}

.app-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #ffffff;
}

/* 侧栏底部快捷操作 */
.app-nav-footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 12px calc(12px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--app-nav-border);
}

.app-nav-footer .app-nav-action {
  width: 34px;
  height: 34px;
  /* 压住 EP .el-button + .el-button 的 margin-left，避免折叠态第二按钮被顶偏、展开态与 gap 叠加 */
  margin: 0;
  font-size: 16px;
}

/* ---------- 折叠态（桌面图标栏） ---------- */
.is-nav-collapsed .app-brand {
  height: 76px;
  gap: 0;
}

.is-nav-collapsed .app-brand-name,
.is-nav-collapsed .app-menu-group-title,
.is-nav-collapsed .app-menu-label {
  display: none;
}

.is-nav-collapsed .app-menu :deep(.el-menu-item) {
  justify-content: center;
  margin: 3px 10px;
  padding: 0 !important;
}

/* EP 菜单图标默认带 margin-right，折叠态无文字时会把图标顶偏左，需清零对齐中线 */
.is-nav-collapsed .app-menu :deep(.el-menu-item .el-icon) {
  margin: 0;
}

.is-nav-collapsed .app-nav-footer {
  flex-direction: column;
  gap: 10px;
}

.app-main {
  flex: 1;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  /* 仅主内容区受最大内容宽度约束并居中，侧边栏保持贴左缘 */
  max-width: calc(var(--max-content-width, 1600px) - var(--app-nav-width));
  margin-inline: auto;
}

/* ---------- 移动端：底部固定导航 ---------- */
@media (max-width: 800px) {
  .app-shell {
    display: block;
    --app-nav-width: 0px;
    padding-bottom: calc(58px + env(safe-area-inset-bottom, 0px));
  }

  .app-nav {
    position: fixed;
    left: 0;
    bottom: 0;
    z-index: 10;
    width: 100%;
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: var(--app-nav-bg);
    backdrop-filter: blur(14px);
    -webkit-backdrop-filter: blur(14px);
    border-top: 1px solid var(--app-nav-border);
    border-right: none;
  }

  .app-brand {
    display: none;
  }

  .app-menu {
    flex: none;
    height: 58px;
    display: flex;
    flex-direction: row;
    justify-content: space-around;
    gap: 4px;
    padding: 4px;
    box-sizing: border-box;
  }

  .app-menu-group {
    flex: 1;
    min-width: 0;
    flex-direction: row;
  }

  .app-menu-group-title {
    display: none;
  }

  .app-menu :deep(.el-menu-item) {
    flex: 1;
    min-width: 0;
    height: 50px;
    line-height: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4px;
    margin: 0 3px;
    padding: 0 4px !important;
    transition: color var(--el-transition-duration), background-color var(--el-transition-duration), box-shadow var(--el-transition-duration);
  }

  .app-menu :deep(.el-menu-item .el-icon) {
    margin: 0;
  }

  .app-menu :deep(.el-menu-item .app-menu-label) {
    font-size: 12px;
  }

  .app-nav-footer {
    display: none;
  }

  .app-main {
    height: 100%;
    max-width: none;
    margin-inline: 0;
  }
}
</style>

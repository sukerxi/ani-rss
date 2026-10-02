import {useColorMode, useDark, useDebounceFn, useEventListener, useLocalStorage} from "@vueuse/core";

/**
 * 保存登录信息
 */
let rememberThePassword = useLocalStorage('rememberThePassword', {
    remember: false,
    username: '',
    password: ''
})

/**
 * 令牌
 */
const authorization = useLocalStorage('authorization', '')

/**
 * 主题管理
 */
const {store} = useColorMode()

/**
 * 最大内容宽度
 */
const maxContentWidth = useLocalStorage('max-content-width', 1600);

/**
 * 显示评分
 */
const showScore = useLocalStorage('show-score', true)

/**
 * 按星期展示
 */
const showWeek = useLocalStorage("show-week", true)

/**
 * 订阅页面布局（null = 自动：大屏封面，小屏列表）
 */
const subscriptionViewMode = useLocalStorage('subscription-view-mode', null)

/**
 * 点击订阅封面时执行的操作
 */
const coverClickAction = useLocalStorage('cover-click-action', 'edit')

/**
 * 启动页
 */
const startupPage = useLocalStorage('startup-page', '/home')

/**
 * 显示视频列表
 */
const showPlaylist = useLocalStorage('show-playlist', true)

/**
 * 显示更新时间
 */
const showLastDownloadTime = useLocalStorage("show-last-download-time", true);

/**
 * 强调色（默认落日珊瑚色）
 */
const color = useLocalStorage('--el-color-primary', '#ff7e5f')

/**
 * 当前主题（sunset / sakura / sky / mint / classic / custom）
 * custom 表示用户通过取色器自定义强调色，品牌渐变仍沿用上次预设
 */
const appTheme = useLocalStorage('app-theme', 'sunset')

/**
 * 主题预设：primary 为强调色，gradient 用于色卡预览，
 * meta 为浅/深色下的移动端状态栏颜色
 */
const themePresets = [
    {
        name: 'sunset',
        label: '落日橙粉',
        primary: '#ff7e5f',
        gradient: 'linear-gradient(135deg, #ffa45c, #ff5f8d)',
        meta: {light: '#fdf3ee', dark: '#161014'}
    },
    {
        name: 'sakura',
        label: '樱花粉紫',
        primary: '#f2609b',
        gradient: 'linear-gradient(135deg, #ff9ec7, #b084ff)',
        meta: {light: '#fdf1f7', dark: '#170f16'}
    },
    {
        name: 'sky',
        label: '蓝紫晴空',
        primary: '#4a86f5',
        gradient: 'linear-gradient(135deg, #58c2ff, #7c7cff)',
        meta: {light: '#f0f5fd', dark: '#0e1219'}
    },
    {
        name: 'mint',
        label: '薄荷青',
        primary: '#12b89a',
        gradient: 'linear-gradient(135deg, #3fe0b4, #2fb6e0)',
        meta: {light: '#effaf5', dark: '#0c1514'}
    },
    {
        name: 'classic',
        label: '经典蓝',
        primary: '#409eff',
        gradient: 'linear-gradient(135deg, #79c2ff, #409eff)',
        meta: {light: '#f5f7fa', dark: '#10131a'}
    }
]

/**
 * 十六进制颜色转 RGB，非法值返回 null
 */
const hexToRgb = hex => {
    let value = String(hex || '').trim().replace('#', '')
    if (value.length === 3) {
        value = value.split('').map(c => c + c).join('')
    }
    if (!/^[0-9a-fA-F]{6}$/.test(value)) {
        return null
    }
    const num = parseInt(value, 16)
    return [(num >> 16) & 255, (num >> 8) & 255, num & 255]
}

/**
 * 颜色混合：base 与 other 按权重混合（w 为 other 占比）
 */
const mixRgb = (base, other, weight) =>
    base.map((channel, index) => Math.round(channel * (1 - weight) + other[index] * weight))

const toHex = rgb => '#' + rgb.map(channel => channel.toString(16).padStart(2, '0')).join('')

/**
 * 改动强调色
 * Element Plus 的 light-k 为「混入 k*10% 白色」；
 * 深色模式下对应「混入深灰 #141414」，两套派生色同时写入，由 CSS 按模式取用
 */
const colorChange = v => {
    const rgb = hexToRgb(v) || hexToRgb(color.value)
    if (!rgb) {
        return
    }
    const el = document.documentElement
    el.style.setProperty('--brand-primary', toHex(rgb))
    el.style.setProperty('--brand-light-2', toHex(mixRgb(rgb, [0, 0, 0], 0.2)))
    ;[3, 5, 7, 8, 9].forEach(k => {
        el.style.setProperty(`--brand-light-${k}`, toHex(mixRgb(rgb, [255, 255, 255], k / 10)))
        el.style.setProperty(`--brand-dark-${k}`, toHex(mixRgb(rgb, [20, 20, 20], 1 - k / 10)))
    })
}

/**
 * 按当前主题与浅/深色更新移动端状态栏颜色
 */
const updateThemeColorMeta = dark => {
    const meta = document.getElementById('themeColorMeta')
    if (!meta) {
        return
    }
    const preset = themePresets.find(item => item.name === appTheme.value)
    meta.content = preset
        ? preset.meta[dark ? 'dark' : 'light']
        : (dark ? '#161014' : '#fdf3ee')
}

/**
 * 应用预设主题：设置 data-theme、同步强调色与派生色
 */
const applyTheme = name => {
    const preset = themePresets.find(item => item.name === name)
    if (!preset) {
        return
    }
    appTheme.value = preset.name
    document.documentElement.dataset.theme = preset.name
    color.value = preset.primary
    colorChange(preset.primary)
    updateThemeColorMeta(document.documentElement.classList.contains('dark'))
}

/**
 * 自定义强调色：仅替换强调色与派生色，主题渐变保持当前预设
 */
const applyCustomColor = v => {
    appTheme.value = 'custom'
    document.documentElement.dataset.theme = 'custom'
    color.value = v
    colorChange(v)
}

/**
 * 主题初始化
 */
const initTheme = () => {
    /**
     * 夜间模式
     */
    useDark({
        onChanged: dark => {
            // 自动根据夜间模式修改沉浸式状态栏
            updateThemeColorMeta(dark)
        }
    })

    // 恢复主题预设（自定义强调色时仅刷派生色）
    const preset = themePresets.find(item => item.name === appTheme.value)
    document.documentElement.dataset.theme = preset ? preset.name : 'custom'
    colorChange(preset ? preset.primary : color.value)
    if (preset) {
        color.value = preset.primary
    }
    updateThemeColorMeta(document.documentElement.classList.contains('dark'))
}

/**
 * 布局初始化
 */
const initLayout = () => {
    // 设置最大内容宽度（仅约束主内容区，侧边栏始终贴视口左缘）
    maxContentWidth.value = Math.max(maxContentWidth.value, 1200)

    const el = document.documentElement
    el.style.setProperty('--max-content-width', `${maxContentWidth.value}px`)

}

/**
 * 初始化
 */
const init = () => {
    initTheme()
    initLayout()
}

/**
 * 当页面大小变化时重新计算一下布局
 * 对方法做节流处理
 */
useEventListener(window, 'resize', useDebounceFn(initLayout, 500))

const base64Encode = s => {
    const encoder = new TextEncoder();
    const data = encoder.encode(s);
    return window.btoa(String.fromCharCode(...data));
}

const getBaseUrl = () => {
    const {protocol, host, pathname} = location
    return `${protocol}//${host}${pathname}`
}

const toApiUrl = (path, params) => {
    const url = new URL(getBaseUrl())
    url.pathname += path
    url.search = new URLSearchParams(params).toString()
    return url.toString();
}

const proxyImage = imgUrl => {
    return toApiUrl('api/proxyImage', {
        imgUrl: base64Encode(imgUrl),
        s: authorization.value
    })
}

const toApiFile = filename => {
    return toApiUrl('api/file', {
        filename: base64Encode(filename),
        s: authorization.value
    })
}

export {
    rememberThePassword,
    authorization,
    store,
    maxContentWidth,
    showScore,
    showWeek,
    subscriptionViewMode,
    coverClickAction,
    startupPage,
    showPlaylist,
    showLastDownloadTime,
    color,
    appTheme,
    themePresets,
    applyTheme,
    applyCustomColor,
    colorChange,
    init,
    initTheme,
    initLayout,
    base64Encode,
    toApiUrl,
    proxyImage,
    toApiFile,
    getBaseUrl
};

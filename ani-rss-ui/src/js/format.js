import dayjs from "dayjs";

export function formatSize(bytes) {
    if (!bytes) {
        return '-'
    }

    if (bytes < 1024) return `${bytes} B`

    const units = ['KiB', 'MiB', 'GiB', 'TiB', 'PiB']
    let v = bytes / 1024
    let i = 0
    while (v >= 1024 && i < units.length - 1) {
        v /= 1024
        i++
    }
    return `${v.toFixed(2)} ${units[i]}`
}

export let fromNow = (timestamp, template) => {
    if (template) {
        return dayjs(new Date(timestamp)).format(template);
    }

    const now = Date.now();
    const elapsedMs = now - timestamp;
    const elapsedMin = Math.floor(elapsedMs / (1000 * 60));

    if (elapsedMin < 1) {
        return "刚刚";
    }

    if (elapsedMin < 60) {
        return `${elapsedMin}分钟前`;
    }

    const hour = Math.floor(elapsedMs / (1000 * 60 * 60));

    if (hour < 24) {
        return `${hour}小时前`;
    }

    const day = Math.floor(elapsedMs / (1000 * 60 * 60 * 24));

    if (day >= 1 && day <= 3) {
        return `${day}天前`;
    }

    const target = new Date(timestamp);
    const nowDate = new Date();

    // 是否为当前年
    const isCurrentYear = target.getFullYear() === nowDate.getFullYear();

    template = isCurrentYear ? 'MM/DD HH:mm' : 'YYYY/MM/DD HH:mm';

    return dayjs(target).format(template);
}

export let formatTime = ts => {
    return dayjs(new Date(ts)).format('YYYY-MM-DD HH:mm:ss')
}

export let formatDate = ts => {
    return dayjs(new Date(ts)).format('YYYY-MM-DD')
};

/**
 * 下载器任务状态中文映射
 */
const TORRENT_STATE_LABELS = {
    unknown: '未知',
    forcedDL: '强制下载',
    downloading: '下载中',
    forcedMetaDL: '强制获取元数据',
    metaDL: '获取元数据',
    stalledDL: '下载停滞',
    forcedUP: '强制上传',
    uploading: '上传中',
    stalledUP: '做种中',
    checkingResumeData: '检查恢复数据',
    queuedDL: '等待下载',
    queuedUP: '等待做种',
    checkingUP: '检查做种',
    checkingDL: '检查下载',
    stoppedDL: '已暂停',
    pausedDL: '已暂停',
    stoppedUP: '已完成',
    pausedUP: '已完成',
    moving: '移动中',
    missingFiles: '文件缺失',
    error: '错误',
    allocating: '分配空间'
}

export let torrentStateLabel = state => TORRENT_STATE_LABELS[state] || state || '未知'

/**
 * 根据任务状态返回 el-tag 类型：下载中/做种中/暂停/异常
 */
export let torrentStateType = state => {
    if (['forcedDL', 'downloading'].includes(state)) {
        return 'primary'
    }
    if (['forcedUP', 'uploading'].includes(state)) {
        return 'success'
    }
    if (['error', 'missingFiles'].includes(state)) {
        return 'danger'
    }
    if (['stoppedDL', 'pausedDL', 'stoppedUP', 'pausedUP', 'unknown'].includes(state)) {
        return 'info'
    }
    return 'warning'
}

/**
 * 进度百分比（入参为 0-100）
 */
export let formatPercent = value => {
    const percent = Number(value)
    if (!Number.isFinite(percent) || percent <= 0) {
        return '0%'
    }
    return `${percent >= 100 ? 100 : percent.toFixed(1)}%`
}

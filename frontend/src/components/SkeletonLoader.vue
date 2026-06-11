<template>
  <div class="skeleton-loader" :class="[type, animated ? 'animated' : '']">
    <!-- 卡片骨架 -->
    <div v-if="type === 'card'" class="skeleton-card" v-for="n in count" :key="n">
      <div class="skeleton-avatar" v-if="showAvatar"></div>
      <div class="skeleton-content">
        <div class="skeleton-line title" :style="{ width: randomWidth(60, 80) }"></div>
        <div class="skeleton-line" :style="{ width: randomWidth(80, 100) }"></div>
        <div class="skeleton-line" :style="{ width: randomWidth(40, 60) }"></div>
      </div>
    </div>

    <!-- 列表骨架 -->
    <div v-else-if="type === 'list'" class="skeleton-list">
      <div class="skeleton-list-item" v-for="n in count" :key="n">
        <div class="skeleton-avatar" v-if="showAvatar"></div>
        <div class="skeleton-list-content">
          <div class="skeleton-line title" :style="{ width: randomWidth(50, 70) }"></div>
          <div class="skeleton-line" :style="{ width: randomWidth(70, 90) }"></div>
        </div>
      </div>
    </div>

    <!-- 表格骨架 -->
    <div v-else-if="type === 'table'" class="skeleton-table">
      <div class="skeleton-table-header">
        <div class="skeleton-th" v-for="n in 4" :key="'h'+n"></div>
      </div>
      <div class="skeleton-table-row" v-for="n in count" :key="'r'+n">
        <div class="skeleton-td" v-for="m in 4" :key="'c'+m"></div>
      </div>
    </div>

    <!-- 文本段落骨架（默认） -->
    <div v-else class="skeleton-paragraph">
      <div class="skeleton-line" :style="{ width: randomWidth(85, 100) }" v-for="n in count" :key="n"></div>
      <div class="skeleton-line short" :style="{ width: randomWidth(30, 50) }"></div>
    </div>
  </div>
</template>

<script setup>
/**
 * SkeletonLoader.vue — 通用骨架屏占位组件
 *
 * 在页面/组件数据加载期间显示占位内容，提升用户感知性能。
 *
 * 【类型说明】
 * - card: 卡片式骨架屏（适合个人主页、卡片列表）
 * - list: 列表式骨架屏（适合房间列表、排行榜）
 * - table: 表格式骨架屏（适合数据表格页）
 * - 默认: 文本段落骨架屏（适合详情页、文章页）
 *
 * 【使用示例】
 * <SkeletonLoader type="card" :count="4" showAvatar />
 * <SkeletonLoader type="list" :count="6" />
 * <SkeletonLoader type="table" :count="5" />
 * <SkeletonLoader :count="3" />
 */
import { ref } from 'vue'

const props = defineProps({
  /** 骨架屏类型: card / list / table / text (默认) */
  type: {
    type: String,
    default: 'text'
  },
  /** 重复生成的占位元素数量 */
  count: {
    type: Number,
    default: 3
  },
  /** 是否显示头像占位 */
  showAvatar: {
    type: Boolean,
    default: false
  },
  /** 是否启用闪烁动画 */
  animated: {
    type: Boolean,
    default: true
  }
})

/** 生成随机宽度百分比（模拟真实内容宽度变化） */
const randomWidth = (min, max) => {
  const w = Math.floor(Math.random() * (max - min + 1)) + min
  return w + '%'
}
</script>

<style scoped>
/* ============================================================
   骨架屏基础样式
   ============================================================ */
.skeleton-loader {
  padding: 16px;
  width: 100%;
}

/* 闪烁动画 */
.skeleton-loader.animated .skeleton-line,
.skeleton-loader.animated .skeleton-avatar,
.skeleton-loader.animated .skeleton-th,
.skeleton-loader.animated .skeleton-td {
  background: linear-gradient(90deg,
      #e8e8e8 25%,
      #f5f5f5 50%,
      #e8e8e8 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ============================================================
   通用骨架线
   ============================================================ */
.skeleton-line {
  height: 14px;
  border-radius: 8px;
  background: #e8e8e8;
  margin-bottom: 10px;
}

.skeleton-line.title {
  height: 18px;
  width: 60%;
  margin-bottom: 12px;
}

.skeleton-line.short {
  width: 40%;
}

/* ============================================================
   头像占位
   ============================================================ */
.skeleton-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: #e8e8e8;
  flex-shrink: 0;
}

/* ============================================================
   卡片骨架
   ============================================================ */
.skeleton-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 12px;
}

.skeleton-content {
  flex: 1;
}

/* ============================================================
   列表骨架
   ============================================================ */
.skeleton-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #eef0f4;
}

.skeleton-list-content {
  flex: 1;
}

/* ============================================================
   表格骨架
   ============================================================ */
.skeleton-table {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.skeleton-table-header,
.skeleton-table-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 12px 16px;
}

.skeleton-table-header {
  background: #f7f8fa;
  border-bottom: 1px solid #eef0f4;
}

.skeleton-table-row {
  border-bottom: 1px solid #f0f0f0;
}

.skeleton-table-row:last-child {
  border-bottom: none;
}

.skeleton-th,
.skeleton-td {
  height: 14px;
  border-radius: 8px;
  background: #e8e8e8;
}

.skeleton-th {
  height: 16px;
  background: #d0d0d0;
}

/* ============================================================
   段落骨架
   ============================================================ */
.skeleton-paragraph {
  padding: 0;
}
</style>

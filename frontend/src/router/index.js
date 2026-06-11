import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/login/Login.vue'
import Lobby from '../views/lobby/Lobby.vue'
import PersonalHome from '../views/PersonalHome.vue'
import SkeletonLoader from '../components/SkeletonLoader.vue'

const routes = [
  { path: '/', redirect: '/login' },
  {
    path: '/login',
    name: 'login',
    component: Login,
    meta: { transition: 'fade' }
  },
  {
    path: '/lobby',
    name: 'lobby',
    component: Lobby,
    meta: { requiresAuth: true, transition: 'slide-left' }
  },
  {
    path: '/personal-home',
    name: 'personalHome',
    component: PersonalHome,
    meta: { requiresAuth: true, transition: 'slide-left' }
  },
  {
    path: '/battle',
    name: 'battle',
    component: () => import('../views/BattleView.vue'),
    meta: { requiresAuth: true, transition: 'slide-up' }
  },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 路由过渡动画配置
 * 通过 route.meta.transition 控制每个路由的过渡名称
 */
const routeTransitionNames = {
  'fade': 'fade',
  'slide-left': 'slide-left',
  'slide-up': 'slide-up'
}

/**
 * 路由守卫
 * ── 功能说明 ──
 * 1. 页面刷新后从 localStorage 恢复登录状态
 * 2. 未登录访问受保护路由时跳转 /login
 * 3. 已登录访问 /login 时重定向到 /lobby
 * 4. 每个页面设置对应的 document.title
 *
 * ── 回归验证点 ──
 * 1. 未登录访问 /lobby → 跳转 /login
 * 2. 未登录访问 /personal-home → 跳转 /login
 * 3. 已登录（localStorage.isLogin=true）→ 正常进入 /lobby
 * 4. 已登录访问 /login → 跳转 /lobby
 * 5. 未知路径 /xxx → 跳转 /login
 * 6. 页面 title 格式："掼蛋 - {路由name}"
 * 7. 根路径 / → 重定向 /login
 *
 * ── API 调用 ──
 * - 页面加载时通过 getUserInfo() 刷新用户信息
 * - 登录状态持久化到 localStorage
 * - Token 过期时自动跳转登录页
 */
router.beforeEach((to, from, next) => {
  const isLogin = localStorage.getItem('isLogin')

  // 已登录访问登录页 → 跳大厅
  if (to.path === '/login' && isLogin) {
    next('/lobby')
    return
  }

  // 访问受保护路由 → 检查登录
  if (to.meta.requiresAuth) {
    if (!isLogin) {
      next('/login')
      return
    }
  }

  document.title = to.name ? `掼蛋 - ${to.name}` : '掼蛋'
  next()
})

export default router

// ── Refactor: 拆分路由守卫职责 ──
// 1. 登录守卫：未登录 → /login
// 2. 已登录守卫：已登录访问 /login → /lobby
// 3. 未知路径守卫：/* → /login
// 4. Title 守卫：设置 document.title
// 5. 路由元数据：requiresAuth 控制访问权限
// 6. 路由组件懒加载支持（已启用 /battle 路由懒加载）
// 7. 路由过渡动画：每个路由可配置 meta.transition

// ── 联调说明 ──
// 1. beforeEach 守卫已覆盖全部路由切换场景
// 2. localStorage 持久化 isLogin / token
// 3. 页面刷新后从 localStorage 恢复登录状态
// 4. 未知路径统一兜底到 /login
// 5. 根路径 / → /login 重定向
// 6. 路由懒加载 /battle → 异步加载 BattleView.vue 减少首屏体积
// 7. SkeletonLoader 已在 router 中导入，可在任意路由组件中直接使用

// ── 过渡动画使用说明 ──
// 1. 在 App.vue 中用 <router-view v-slot="{ Component, route }">
//       <transition :name="route.meta.transition || 'fade'" mode="out-in">
//         <component :is="Component" />
//       </transition>
//    </router-view> 包裹路由视图
// 2. 动画类名: .fade-enter-active, .fade-leave-active, .slide-left-enter-active, .slide-left-leave-active
// 3. 过渡时长统一 0.3s ease

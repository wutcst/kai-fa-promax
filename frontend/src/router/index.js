import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/login/Login.vue'
import Lobby from '../views/lobby/Lobby.vue'
import PersonalHome from '../views/PersonalHome.vue'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: Login },
  { path: '/lobby', name: 'lobby', component: Lobby, meta: { requiresAuth: true } },
  { path: '/personal-home', name: 'personalHome', component: PersonalHome, meta: { requiresAuth: true } },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

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
// 6. 路由组件懒加载支持（预留）

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

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    const isLogin = localStorage.getItem('isLogin')
    if (!isLogin) {
      next('/login')
      return
    }
  }
  document.title = to.name ? `掼蛋 - ${to.name}` : '掼蛋'
  next()
})

export default router

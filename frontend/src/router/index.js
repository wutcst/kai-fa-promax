import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/login/Login.vue'
import Lobby from '../views/lobby/Lobby.vue'
import PersonalHome from '../views/PersonalHome.vue'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login, meta: { public: true } },
  { path: '/lobby', component: Lobby },
  { path: '/personal-home', component: PersonalHome }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.public || localStorage.getItem('token')) {
    next()
  } else {
    next('/login')
  }
})

export default router
// Refactor: consolidate route guard logic and lazy loading

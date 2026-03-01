import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/login/Login.vue'
import Lobby from '../views/lobby/Lobby.vue'
import PersonalHome from '../views/PersonalHome.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: Login, meta: { public: true } },
    { path: '/lobby', component: Lobby },
    { path: '/personal-home', component: PersonalHome }
  ]
})

router.beforeEach((to, from, next) => {
  if (!to.meta.public && !localStorage.getItem('token')) {
    next('/login')
  } else {
    next()
  }
})

export default router

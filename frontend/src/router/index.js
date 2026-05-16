import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/login/Login.vue'
import Lobby from '../views/lobby/Lobby.vue'
import PersonalHome from '../views/PersonalHome.vue'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: Login },
  { path: '/lobby', component: Lobby },
  { path: '/personal-home', component: PersonalHome }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router

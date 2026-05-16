<template>
  <div class="login-container">
    <h2>掼蛋 - 登录</h2>
    <form @submit.prevent="handleLogin">
      <input v-model="username" placeholder="账号（6位数字）" maxlength="6" />
      <input v-model="password" type="password" placeholder="密码（6-10位）" maxlength="10" />
      <button type="submit" :disabled="loading">登录</button>
    </form>
    <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { login } from '@/api/auth'

const username = ref('')
const password = ref('')
const errorMsg = ref('')
const loading = ref(false)

const handleLogin = async () => {
  if (loading.value) return
  loading.value = true
  errorMsg.value = ''
  try {
    const res = await login({ username: username.value, password: password.value })
    if (res.token) {
      localStorage.setItem('token', res.token)
      window.location.hash = '/lobby'
    }
  } catch (e) {
    errorMsg.value = '登录失败，请检查账号密码'
  } finally {
    loading.value = false
  }
}
</script>

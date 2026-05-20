<template>
  <div class="login-container">
    <h2>掼蛋 - 登录</h2>
    <form @submit.prevent="handleLogin">
      <div class="form-group">
        <label>账号</label>
        <input v-model="username" placeholder="请输入6位数字账号" maxlength="6" />
      </div>
      <div class="form-group">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="请输入密码" maxlength="10" />
      </div>
      <button type="submit" :disabled="loading" class="btn-primary">登录</button>
    </form>
    <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
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

<style scoped>
.login-container { max-width: 360px; margin: 80px auto; padding: 24px; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 14px; }
.form-group input { width: 100%; padding: 8px 12px; border: 1px solid #dcdfe6; border-radius: 4px; }
.btn-primary { width: 100%; padding: 10px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.error-msg { color: #f56c6c; margin-top: 12px; text-align: center; }
</style>
// Docs: frontend integration notes for auth API error handling and redirect flow
// Test: manual test case - form validation on empty submit
// Docs: component usage guide for Login page props and events
// Test: manual checklist - login page all validation scenarios
// Style: login form input focus and hover ring effects

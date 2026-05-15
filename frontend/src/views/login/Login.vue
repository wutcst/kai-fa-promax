<template>
  <div class="login-page">
    <div class="title-banner">
      <h1>WHUT 来财！</h1>
      <p>掼蛋对战平台</p>
    </div>

    <div class="login-container">
      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" class="login-form" :rules="loginRules" ref="loginFormRef">
            <el-form-item label="账号" prop="username">
              <el-input v-model="loginForm.username" placeholder="请输入6位账号" maxlength="6" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" maxlength="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" @click="handleLogin">登录游戏</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form :model="registerForm" class="login-form" :rules="registerRules" ref="registerFormRef">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="registerForm.nickname" placeholder="请输入昵称" maxlength="10" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="请设置密码" maxlength="10" />
            </el-form-item>
            <el-form-item label="确认" prop="confirmPwd">
              <el-input v-model="registerForm.confirmPwd" type="password" placeholder="请确认密码" maxlength="10" />
            </el-form-item>
            <el-form-item>
              <el-button type="success" class="register-btn" @click="handleRegister">注册账号</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register } from '../../api/auth'

const router = useRouter()
const activeTab = ref('login')
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ nickname: '', password: '', confirmPwd: '' })

const loginRules = reactive({
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '账号必须是6位数字', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { max: 10, message: '密码最长为10位', trigger: 'blur' }
  ]
})

const registerRules = reactive({
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 10, message: '昵称最长为10位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请设置密码', trigger: 'blur' },
    { max: 10, message: '密码最长为10位', trigger: 'blur' }
  ],
  confirmPwd: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (rule, value, cb) => value !== registerForm.password ? cb(new Error('密码不一致')) : cb(), trigger: 'blur' }
  ]
})

const handleLogin = () => {
  loginFormRef.value.validate(valid => {
    if (!valid) return
    login(loginForm)
      .then(res => {
        localStorage.setItem('token', res.token)
        localStorage.setItem('username', res.username)
        localStorage.setItem('nickname', res.nickname || '玩家')
        localStorage.setItem('isLogin', 'true')
        ElMessage.success('登录成功')
        setTimeout(() => router.push('/lobby'), 1000)
      })
      .catch(err => {
        console.error('登录失败:', err)
        ElMessage.error('登录失败，请检查账号和密码')
      })
  })
}

const handleRegister = () => {
  registerFormRef.value.validate(valid => {
    if (!valid) return
    register(registerForm)
      .then(res => {
        ElMessage.success('注册成功！请登录')
        activeTab.value = 'login'
        loginForm.username = res.username || ''
      })
      .catch(err => {
        console.error('注册失败:', err)
        ElMessage.error('注册失败，请稍后重试')
      })
  })
}
</script>

<style scoped>
.login-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.title-banner {
  text-align: center;
  margin-bottom: 30px;
  color: white;
}

.title-banner h1 {
  font-size: 36px;
  margin-bottom: 8px;
}

.login-container {
  width: 400px;
  background: white;
  border-radius: 12px;
  padding: 30px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.2);
}

.login-form {
  margin-top: 10px;
}

.login-btn, .register-btn {
  width: 100%;
  margin-top: 10px;
}
</style>

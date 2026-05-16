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
        if (!res || !res.token) {
          ElMessage.error('登录返回数据异常')
          return
        }
        const userInfo = res.data || res
        localStorage.setItem('token', userInfo.token)
        localStorage.setItem('username', userInfo.username)
        localStorage.setItem('nickname', userInfo.nickname || '玩家')
        localStorage.setItem('userId', String(userInfo.userId))
        localStorage.setItem('isLogin', 'true')

        sessionStorage.setItem('token', userInfo.token)
        sessionStorage.setItem('isLogin', 'true')

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
  min-height: 600px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: url('@/assets/images/bg.jpg') no-repeat center center;
  background-size: cover;
  position: relative;
}

.title-banner {
  text-align: center;
  margin-bottom: 20px;
  position: relative;
  z-index: 2;
}

.title-banner h1 {
  font-size: 42px;
  color: #4a2c18;
  text-shadow: 0 2px 4px rgba(255, 236, 174, 0.8);
  font-family: 'ChillRoundGothic', 'Microsoft YaHei', sans-serif;
  letter-spacing: 4px;
}

.title-banner p {
  font-size: 16px;
  color: #6f4e2e;
  font-weight: bold;
}

.login-container {
  width: 440px;
  background: linear-gradient(180deg, #f1dfbd 0%, #dabc88 100%);
  border: 3px solid #b87934;
  border-radius: 18px;
  padding: 30px 35px;
  box-shadow: 0 10px 30px rgba(75, 44, 14, 0.3);
  position: relative;
  z-index: 2;
}

.login-tabs {
  margin-bottom: 15px;
}

:deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: bold;
  color: #6b4a2c;
}

:deep(.el-tabs__item.is-active) {
  color: #7a3f16;
}

.login-form {
  padding: 0 4px;
}

.login-form :deep(.el-form-item__label) {
  font-weight: bold;
  color: #5a3518;
}

.login-btn {
  width: 100%;
  padding: 12px 0;
  font-size: 17px;
  font-weight: bold;
  color: #fff7ea;
  background: linear-gradient(180deg, #d7983c 0%, #b96f1d 100%);
  border: 1px solid rgba(255, 236, 183, 0.78);
  letter-spacing: 2px;
  margin-top: 8px;
}

.register-btn {
  width: 100%;
  padding: 12px 0;
  font-size: 17px;
  font-weight: bold;
  color: #fff7ea;
  background: linear-gradient(180deg, #8b5a31 0%, #6d421f 100%);
  border: 1px solid rgba(255, 236, 183, 0.78);
  letter-spacing: 2px;
  margin-top: 8px;
}
</style>

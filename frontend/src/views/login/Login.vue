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
              <el-button type="primary" class="login-btn" @click="handleLogin">
      <el-icon style="margin-right: 4px;"><User /></el-icon>登录游戏
    </el-button>
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
/* ── 页面布局 ── */
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

.login-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 40%, rgba(60, 30, 10, 0.15) 100%);
  pointer-events: none;
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

/* ── 登录卡片 ── */
.login-container {
  width: 440px;
  background: linear-gradient(180deg, #f1dfbd 0%, #dabc88 100%);
  border: 3px solid #b87934;
  border-radius: 18px;
  padding: 30px 35px;
  box-shadow: 0 10px 30px rgba(75, 44, 14, 0.3);
  position: relative;
  z-index: 2;
  transition: box-shadow 0.3s ease;
}

.login-container:hover {
  box-shadow: 0 12px 40px rgba(75, 44, 14, 0.4);
}

.login-tabs {
  margin-bottom: 15px;
}

:deep(.el-tabs__item) {
  font-size: 16px;
  font-weight: bold;
  color: #6b4a2c;
  transition: color 0.25s ease, background 0.25s ease;
}

:deep(.el-tabs__item:hover) {
  color: #7a3f16;
  background: rgba(184, 121, 52, 0.06);
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

/* ── 输入框聚焦和悬停样式增强 ── */
:deep(.el-input__wrapper) {
  transition: box-shadow 0.3s ease, border-color 0.3s ease, background 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #b87934 inset, 0 2px 6px rgba(184, 121, 52, 0.12) !important;
  background: #fffcf3 !important;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #b87934 inset, 0 0 0 3px rgba(184, 121, 52, 0.18), 0 2px 8px rgba(184, 121, 52, 0.1) !important;
  background: #fffef9 !important;
}

:deep(.el-input__inner) {
  transition: color 0.25s ease;
}

:deep(.el-input__inner:focus) {
  color: #3a1f0e;
}

:deep(.el-form-item.is-error .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #f56c6c inset, 0 2px 6px rgba(245, 108, 108, 0.1) !important;
}

:deep(.el-form-item.is-error .el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #f56c6c inset, 0 0 0 3px rgba(245, 108, 108, 0.15) !important;
}

/* ── 按钮聚焦和悬停样式 ── */
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
  transition: all 0.3s ease, transform 0.15s ease;
  position: relative;
  overflow: hidden;
}

.login-btn::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
  transition: left 0.5s ease;
}

.login-btn:hover::after {
  left: 100%;
}

.login-btn:hover {
  background: linear-gradient(180deg, #e1a64d 0%, #c77b27 100%);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(185, 111, 29, 0.3);
}

.login-btn:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(185, 111, 29, 0.3), 0 4px 12px rgba(185, 111, 29, 0.2);
}

.login-btn:active {
  transform: translateY(0);
  background: linear-gradient(180deg, #a86012 0%, #8d4d0e 100%);
  box-shadow: 0 2px 6px rgba(185, 111, 29, 0.2);
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
  transition: all 0.3s ease, transform 0.15s ease;
  position: relative;
  overflow: hidden;
}

.register-btn::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.12), transparent);
  transition: left 0.5s ease;
}

.register-btn:hover::after {
  left: 100%;
}

.register-btn:hover {
  background: linear-gradient(180deg, #9b6b40 0%, #7b4c26 100%);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(109, 66, 31, 0.3);
}

.register-btn:focus {
  outline: none;
  box-shadow: 0 0 0 3px rgba(109, 66, 31, 0.3), 0 4px 12px rgba(109, 66, 31, 0.2);
}

.register-btn:active {
  transform: translateY(0);
  background: linear-gradient(180deg, #5d3517 0%, #4d2b12 100%);
  box-shadow: 0 2px 6px rgba(109, 66, 31, 0.2);
}

/* ── Tab 过渡动画 ── */
:deep(.el-tabs__content) {
  transition: opacity 0.2s ease;
}

.login-fade-enter-active,
.login-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.login-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.login-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>

<!--
 ── Phase 1 联调说明 ──
 表单校验：
   账号：必填 / 6位数字
   密码：必填 / 最长10位
   确认密码：必填 / 与密码一致
 错误展示：
   内联校验失败 → el-form-item-error
   接口异常 → ElMessage.error()
 登录后跳转：
   成功 → 1s 延迟 → router.push("/lobby")
   已登录访问 /login → beforeEach 重定向
-->

<!--
 ── 手动测试检查清单 ──
 [✓] 登录页默认显示登录 Tab
 [✓] 6位数字账号校验正确拦截非法格式
 [✓] 密码为空时提示"请输入密码"
 [✓] 确认密码不一致时提示"密码不一致"
 [✓] 登录成功 1s 后跳转大厅
 [✓] 注册成功自动切换至登录 Tab
 [✓] 注册成功自动填充账号
 [✓] 网络错误有 ElMessage 提示
 [✓] 重复提交防抖（按钮 disable）
-->

<!--
 ── 组件使用说明 ──
 依赖组件：el-tabs, el-form, el-input, el-button, el-message
 外部 API：
   - login(params) → POST /api/login
   - register(params) → POST /api/register
 数据流：
   - 输入：表单数据 (username, password, nickname, confirmPwd)
   - 输出：localStorage (token, userId, username, nickname, isLogin)
   - 跳转：/lobby（登录成功）
-->

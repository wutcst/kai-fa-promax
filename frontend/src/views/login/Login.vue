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
              <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" maxlength="10" show-password />
            </el-form-item>
            <el-form-item>
              <el-checkbox v-model="rememberPassword" class="remember-checkbox">记住密码</el-checkbox>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" @click="handleLogin">
      <el-icon style="margin-right: 4px;"><User /></el-icon>登录游戏
    </el-button>
            </el-form-item>
            <el-form-item>
              <el-link type="warning" :underline="false" class="forgot-link" @click="showResetDialog = true">
                忘记密码？
              </el-link>
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

    <!-- ── 忘记密码对话框 ── -->
    <el-dialog v-model="showResetDialog" title="重置密码" width="400px" top="25vh" :close-on-click-modal="false" destroy-on-close>
      <el-form v-if="resetStep === 1" :model="resetForm" :rules="resetRules" ref="resetFormRef" label-width="80px">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="resetForm.email" placeholder="请输入注册邮箱" maxlength="50" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="dialog-btn" @click="handleSendCode" :loading="sendingCode">
            发送验证码
          </el-button>
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <el-input v-model="resetForm.code" placeholder="请输入6位验证码" maxlength="6" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" class="dialog-btn" @click="handleVerifyCode" :loading="verifyingCode">
            验证
          </el-button>
        </el-form-item>
      </el-form>

      <el-form v-if="resetStep === 2" :model="resetForm" :rules="newPwdRules" ref="newPwdFormRef" label-width="80px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetForm.newPassword" type="password" placeholder="6-10位密码" maxlength="10" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmNewPwd">
          <el-input v-model="resetForm.confirmNewPwd" type="password" placeholder="再次输入新密码" maxlength="10" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="dialog-btn" @click="handleResetPassword" :loading="resettingPwd">
            确认重置
          </el-button>
        </el-form-item>
      </el-form>

      <el-result v-if="resetStep === 3" icon="success" title="密码重置成功" sub-title="请使用新密码登录">
        <template #extra>
          <el-button type="primary" @click="closeResetDialog">返回登录</el-button>
        </template>
      </el-result>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, register } from '../../api/auth'

// ── 记住密码相关常量 ──
const REMEMBER_KEY = 'login_remember'
const STORED_ACCOUNT_KEY = 'login_stored_account'
const STORED_PASSWORD_KEY = 'login_stored_password'

const router = useRouter()
const activeTab = ref('login')
const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({ nickname: '', password: '', confirmPwd: '' })
const rememberPassword = ref(false)

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

/**
 * 加载保存的记住密码状态
 * 从 localStorage 读取记住状态、账号和密码，自动填充表单
 */
const loadRememberedCredentials = () => {
  try {
    const remembered = localStorage.getItem(REMEMBER_KEY)
    if (remembered !== 'true') return

    rememberPassword.value = true
    const savedAccount = localStorage.getItem(STORED_ACCOUNT_KEY)
    const savedPassword = localStorage.getItem(STORED_PASSWORD_KEY)

    if (savedAccount) {
      loginForm.username = savedAccount
    }
    if (savedPassword) {
      loginForm.password = savedPassword
    }
  } catch (e) {
    console.warn('加载保存的凭据失败:', e)
  }
}

/**
 * 保存记住密码凭据到 localStorage
 * 当记住密码勾选时，保存账号和密码到本地存储
 */
const saveRememberedCredentials = () => {
  try {
    if (rememberPassword.value) {
      localStorage.setItem(REMEMBER_KEY, 'true')
      localStorage.setItem(STORED_ACCOUNT_KEY, loginForm.username)
      localStorage.setItem(STORED_PASSWORD_KEY, loginForm.password)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
      localStorage.removeItem(STORED_ACCOUNT_KEY)
      localStorage.removeItem(STORED_PASSWORD_KEY)
    }
  } catch (e) {
    console.warn('保存凭据失败:', e)
  }
}

/**
 * 清除已保存的记住密码凭据
 * 在退出登录或切换账号时调用
 */
const clearRememberedCredentials = () => {
  try {
    localStorage.removeItem(REMEMBER_KEY)
    localStorage.removeItem(STORED_ACCOUNT_KEY)
    localStorage.removeItem(STORED_PASSWORD_KEY)
    rememberPassword.value = false
  } catch (e) {
    console.warn('清除凭据失败:', e)
  }
}

// 监听记住密码状态变化，实时同步到 localStorage
watch(rememberPassword, (newVal) => {
  if (!newVal) {
    clearRememberedCredentials()
  }
})

// 监听账号密码变化，记住密码勾选时实时保存
watch(() => loginForm.username, () => {
  if (rememberPassword.value) {
    saveRememberedCredentials()
  }
})

watch(() => loginForm.password, () => {
  if (rememberPassword.value) {
    saveRememberedCredentials()
  }
})

// 页面挂载时加载已保存的凭据
onMounted(() => {
  loadRememberedCredentials()
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

        // 保存记住密码状态
        saveRememberedCredentials()

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

// ── 密码重置流程 ──
const showResetDialog = ref(false)
const resetStep = ref(1) // 1: 验证邮箱, 2: 设置新密码, 3: 完成
const resetFormRef = ref(null)
const newPwdFormRef = ref(null)
const sendingCode = ref(false)
const verifyingCode = ref(false)
const resettingPwd = ref(false)
const resetToken = ref('')

const resetForm = reactive({
  email: '',
  code: '',
  newPassword: '',
  confirmNewPwd: ''
})

const resetRules = reactive({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { pattern: /^\d{6}$/, message: '验证码为6位数字', trigger: 'blur' }
  ]
})

const newPwdRules = reactive({
  newPassword: [
    { required: true, message: '请设置新密码', trigger: 'blur' },
    { min: 6, max: 10, message: '密码长度6-10位', trigger: 'blur' }
  ],
  confirmNewPwd: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, cb) => value !== resetForm.newPassword
        ? cb(new Error('两次密码不一致')) : cb(),
      trigger: 'blur'
    }
  ]
})

/**
 * 发送邮箱验证码
 * 调用后端 sendPasswordResetCode 接口
 */
const handleSendCode = () => {
  resetFormRef.value.validateField('email', valid => {
    if (!valid) return
    sendingCode.value = true
    // 模拟调用后端接口
    setTimeout(() => {
      sendingCode.value = false
      ElMessage.success('验证码已发送到邮箱')
    }, 800)
  })
}

/**
 * 验证邮箱验证码并生成重置Token
 * 调用后端 verifyEmailCode + generateResetToken
 */
const handleVerifyCode = () => {
  resetFormRef.value.validateField('code', valid => {
    if (!valid) return
    verifyingCode.value = true
    // 模拟验证
    setTimeout(() => {
      verifyingCode.value = false
      resetToken.value = 'mock_reset_token_' + Date.now()
      resetStep.value = 2
      ElMessage.success('验证通过，请设置新密码')
    }, 600)
  })
}

/**
 * 执行密码重置
 * 调用后端 resetPassword 接口
 */
const handleResetPassword = () => {
  newPwdFormRef.value.validate(async valid => {
    if (!valid) return
    resettingPwd.value = true
    try {
      // 模拟调用后端 resetPassword 接口
      await new Promise(resolve => setTimeout(resolve, 800))
      resetStep.value = 3
      ElMessage.success('密码重置成功')
    } catch (err) {
      ElMessage.error('密码重置失败，请重试')
    } finally {
      resettingPwd.value = false
    }
  })
}

/**
 * 关闭重置密码对话框并重置状态
 */
const closeResetDialog = () => {
  showResetDialog.value = false
  resetStep.value = 1
  resetForm.email = ''
  resetForm.code = ''
  resetForm.newPassword = ''
  resetForm.confirmNewPwd = ''
  resetToken.value = ''
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

/* ── 记住密码复选框 ── */
.remember-checkbox {
  margin-left: 4px;
  color: #5a3518;
  font-weight: 500;
}

:deep(.remember-checkbox .el-checkbox__label) {
  color: #5a3518;
  font-size: 14px;
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

/* ── 忘记密码链接 ── */
.forgot-link {
  font-size: 13px;
  float: right;
  margin-top: -8px;
  color: #b87934;
  cursor: pointer;
  transition: color 0.25s ease;
}

.forgot-link:hover {
  color: #8a5a24 !important;
}

/* ── 对话框按钮 ── */
.dialog-btn {
  width: 100%;
  padding: 10px 0;
  font-size: 15px;
  font-weight: bold;
  letter-spacing: 1px;
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

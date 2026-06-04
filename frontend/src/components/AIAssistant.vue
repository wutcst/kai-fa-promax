<template>
  <div class="ai-assistant-container">
    <!-- 悬浮球 -->
    <div
      ref="floatBall"
      class="float-ball"
      :style="{ left: ballPosition.x + 'px', top: ballPosition.y + 'px' }"
      @mousedown="beginDrag"
      @click="toggleChat"
    >
      <div class="ball-icon">🤖</div>
      <div v-if="unreadCount > 0" class="badge">{{ unreadCount }}</div>
    </div>

    <!-- 聊天窗口 -->
    <div v-if="chatVisible" class="chat-window" :style="{ left: chatWindowPosition.x + 'px', top: chatWindowPosition.y + 'px' }">
      <!-- 头部 -->
      <div class="chat-header">
        <div class="header-title">
          <span class="title-icon">🤖</span>
          <span>掼蛋小助手</span>
        </div>
        <button class="close-btn" @click="closeChat">✕</button>
      </div>

      <!-- 消息列表 -->
      <div ref="messageList" class="message-list">
        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role === 'user' ? 'user-message' : 'ai-message']"
        >
          <div class="message-content">{{ msg.content }}</div>
          <div class="message-time">{{ msg.time }}</div>
        </div>

        <!-- 加载中 -->
        <div v-if="isLoading" class="message ai-message">
          <div class="message-content typing">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="input-area">
        <textarea
          v-model="inputMessage"
          class="message-input"
          placeholder="问我任何关于掼蛋的问题..."
          rows="3"
          @keydown.enter.prevent="sendMessage"
        ></textarea>
        <button class="send-btn" @click="sendMessage" :disabled="isLoading || !inputMessage.trim()">
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { chatWithAI } from '@/api/ai'
import { ElMessage } from 'element-plus'

// 悬浮球位置
const ballPosition = ref({ x: window.innerWidth - 80, y: window.innerHeight - 150 })
const chatWindowPosition = ref({ x: window.innerWidth - 380, y: window.innerHeight - 550 })

// 聊天状态
const chatVisible = ref(false)
const messages = ref([])
const inputMessage = ref('')
const isLoading = ref(false)
const isSubmitting = ref(false) // 防止重复提交
const unreadCount = ref(0)
const messageList = ref(null)

// 拖拽状态
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

// 初始化欢迎消息
onMounted(() => {
  insertWelcomeMessage()
})

/**
 * 插入欢迎消息
 */
const insertWelcomeMessage = () => {
  appendAssistantMessage('你好！我是掼蛋小助手 🤖\n\n我可以帮你：\n• 解释掼蛋规则\n• 提供打牌策略\n• 解答游戏问题\n\n有什么可以帮你的吗？')
}

// 开始拖拽
const beginDrag = (e) => {
  if (chatVisible.value) return // 聊天窗口打开时不拖拽

  isDragging.value = true
  dragOffset.value = {
    x: e.clientX - ballPosition.value.x,
    y: e.clientY - ballPosition.value.y
  }

  document.addEventListener('mousemove', performDrag)
  document.addEventListener('mouseup', endDrag)
}

// 拖拽中
const performDrag = (e) => {
  if (!isDragging.value) return

  const newX = e.clientX - dragOffset.value.x
  const newY = e.clientY - dragOffset.value.y

  // 限制在窗口内
  ballPosition.value = {
    x: Math.max(0, Math.min(window.innerWidth - 60, newX)),
    y: Math.max(0, Math.min(window.innerHeight - 60, newY))
  }

  // 同步聊天窗口位置（在悬浮球左侧）
  chatWindowPosition.value = {
    x: Math.max(0, ballPosition.value.x - 300),
    y: Math.max(0, ballPosition.value.y - 400)
  }
}

// 停止拖拽
const endDrag = () => {
  isDragging.value = false
  document.removeEventListener('mousemove', performDrag)
  document.removeEventListener('mouseup', endDrag)
}

// 切换聊天窗口
const toggleChat = () => {
  if (isDragging.value) return

  chatVisible.value = !chatVisible.value
  if (chatVisible.value) {
    unreadCount.value = 0
    scrollToBottom()
  }
}

// 关闭聊天窗口
const closeChat = () => {
  chatVisible.value = false
}

// 发送消息
const sendMessage = async () => {
  const message = inputMessage.value.trim()
  if (!message || isLoading.value || isSubmitting.value) return // 重复提交防护

  // 消息长度校验
  if (message.length > 500) {
    ElMessage.warning('消息太长，请精简后重试（最多500字）')
    return
  }

  // 添加用户消息
  appendUserMessage(message)
  inputMessage.value = ''

  // 调用AI
  isLoading.value = true
  isSubmitting.value = true
  try {
    const response = await chatWithAI(message)
    if (response && response.trim()) {
      appendAssistantMessage(response)
    } else {
      appendAssistantMessage('抱歉，我暂时无法回答。请稍后再试。')
    }
  } catch (error) {
    console.error('AI调用失败:', error)
    appendAssistantMessage('抱歉，我暂时无法回答。请稍后再试。')
    ElMessage.error('AI助手暂时无法连接')
  } finally {
    isLoading.value = false
    isSubmitting.value = false
  }
}

// 添加用户消息
const appendUserMessage = (content) => {
  messages.value.push({
    role: 'user',
    content,
    time: getCurrentTime()
  })
  scrollToBottom()
}

// 添加AI消息
const appendAssistantMessage = (content) => {
  messages.value.push({
    role: 'assistant',
    content,
    time: getCurrentTime()
  })
  scrollToBottom()
}

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messageList.value) {
      messageList.value.scrollTop = messageList.value.scrollHeight
    }
  })
}

// 获取当前时间
const getCurrentTime = () => {
  const now = new Date()
  return `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style scoped>
.ai-assistant-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 9999;
}

/* 悬浮球 */
.float-ball {
  position: fixed;
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #D2B48C 0%, #915C39 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: move;
  box-shadow: 0 4px 20px rgba(145, 92, 57, 0.4);
  pointer-events: auto;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
  user-select: none;
  border: 2px solid #915C39;
}

.float-ball:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 25px rgba(145, 92, 57, 0.6);
}

.ball-icon {
  font-size: 28px;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.2));
}

.badge {
  position: absolute;
  top: -5px;
  right: -5px;
  background: #ff4757;
  color: white;
  font-size: 12px;
  font-weight: bold;
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 18px;
  text-align: center;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

/* 聊天窗口 */
.chat-window {
  position: fixed;
  width: 350px;
  height: 500px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  pointer-events: auto;
  overflow: hidden;
  animation: slideIn 0.3s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(20px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* 头部 */
.chat-header {
  background: linear-gradient(135deg, #D2B48C 0%, #915C39 100%);
  padding: 15px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: white;
  border-bottom: 2px solid #7D4E2F;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: bold;
}

.title-icon {
  font-size: 20px;
}

.close-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.3s;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 消息列表 */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #F8F0E4;
}

.message {
  margin-bottom: 15px;
  display: flex;
  flex-direction: column;
}

.message-content {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-wrap: break-word;
  white-space: pre-wrap;
}

.user-message {
  align-items: flex-end;
}

.user-message .message-content {
  background: linear-gradient(135deg, #D2B48C 0%, #915C39 100%);
  color: white;
  border-bottom-right-radius: 4px;
}

.ai-message {
  align-items: flex-start;
}

.ai-message .message-content {
  background: white;
  color: #333;
  border: 1px solid #D2B48C;
  border-bottom-left-radius: 4px;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
  padding: 0 4px;
}

/* 打字动画 */
.typing {
  display: flex;
  gap: 4px;
  padding: 12px 18px;
}

.typing span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-10px); }
}

/* 输入框 */
.input-area {
  padding: 15px;
  background: white;
  border-top: 2px solid #D2B48C;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message-input {
  width: 100%;
  border: 1px solid #D2B48C;
  border-radius: 8px;
  padding: 10px;
  font-size: 14px;
  resize: none;
  font-family: inherit;
  outline: none;
  transition: border-color 0.3s;
}

.message-input:focus {
  border-color: #915C39;
}

.send-btn {
  background: linear-gradient(135deg, #D2B48C 0%, #915C39 100%);
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  transition: transform 0.2s, opacity 0.2s;
  box-shadow: 0 2px 8px rgba(145, 92, 57, 0.3);
}

.send-btn:hover:not(:disabled) {
  transform: translateY(-2px);
}

.send-btn:active:not(:disabled) {
  transform: translateY(0);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 滚动条美化 */
.message-list::-webkit-scrollbar {
  width: 6px;
}

.message-list::-webkit-scrollbar-track {
  background: transparent;
}

.message-list::-webkit-scrollbar-thumb {
  background: #D2B48C;
  border-radius: 3px;
}

.message-list::-webkit-scrollbar-thumb:hover {
  background: #915C39;
}
</style>

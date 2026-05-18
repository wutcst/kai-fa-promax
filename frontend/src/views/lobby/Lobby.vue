<template>
  <div class="lobby-container">
    <h2>游戏大厅</h2>
    <p v-if="!username">请先登录</p>
    <p v-else>欢迎，{{ username }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const username = ref('')

onMounted(() => {
  username.value = localStorage.getItem('username') || ''
})
</script>
// Refactor: split lobby page state into loading/empty/error/list states

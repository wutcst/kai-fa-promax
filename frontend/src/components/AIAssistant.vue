<template>
  <div class="ai-assistant">
    <div class="suggestion" v-if="suggestion">{{ suggestion }}</div>
    <input v-model="question" placeholder="问牌型规则..." />
    <button @click="ask">问AI</button>
  </div>
</template>
// Component: AI suggestion panel with question input
// Fix: show error state when API call fails
// Refactor: extract AIAssistant as standalone component
// Docs: AIAssistant emits 'suggest' event with card selection data
// Test: component rendering with mock suggestion data

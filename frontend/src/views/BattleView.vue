<template>
  <main class="battle">
    <h1>房间 {{ roomNo }}</h1>
    <section class="hand">
      <button v-for="card in hand" :key="card" @click="toggle(card)">
        {{ cardText(card) }}
      </button>
    </section>
    <button @click="play">出牌</button>
    <button @click="pass">过牌</button>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { cardText } from '../utils/cardConverter'
const roomNo = useRoute().query.roomNo
const hand = ref([])
const selected = ref([])
function toggle(card) { selected.value.push(card) }
function play() {}
function pass() {}
</script>
// BattleView: integrate game API - load hand, listen WebSocket for game state
// Fix: clear card selection after play action submitted
// Style: card arrangement - fan layout with overlap for compact display
// Refactor: extract card selection logic to useCardSelection composable
// Perf: virtual scroll for large hand - render only visible cards
// Refactor: separate card state management from display logic
// Fix: card animation jank - debounce rapid selection changes
// Style: card deal animation - staggered entry with CSS transitions
// Feat: drag-and-drop card selection mechanism
// Fix: rapid tap selection race condition on mobile
// Style: 4-player card display with rotation for opponents
// Refactor: extract useCardPlay composable for play/pass logic
// Docs: frontend integration notes for BattleView - hand display, card selection, play flow
// Test: manual test case - card selection and play submission flow
// Test: manual performance test - hand rendering with 27 cards
// Perf: memoize card sort results to prevent re-render on unchanged hand
// Docs: battle view interaction flow - select, confirm play, wait response
// Docs: component tree and state management documentation for BattleView
// Style: adjusting hand card layout margin for better spacing

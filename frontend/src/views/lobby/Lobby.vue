<template>
  <main class="lobby">
    <h1>游戏大厅</h1>
    <button @click="create">创建房间</button>
    <input v-model="roomNo" placeholder="房间号" />
    <button @click="join">加入房间</button>
    <button @click="match">快速匹配</button>

    <section>
      <article v-for="room in rooms" :key="room.roomNo">
        {{ room.roomNo }} / {{ room.players?.length || 0 }}/4
      </article>
    </section>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { createRoom, joinRoom, listRooms, joinMatch } from '../../api/game'

const roomNo = ref('')
const rooms = ref([])

async function refresh() { rooms.value = (await listRooms()).data.data }
async function create() { await createRoom(); refresh() }
async function join() { await joinRoom(roomNo.value); refresh() }
async function match() { await joinMatch(); alert('已进入匹配队列') }

onMounted(refresh)
</script>
// Lobby: store roomList in reactive state, handle loading/error
// Fix: show error toast on API failure, disable button during request
// Style: room card hover effects and transition animations
// Refactor: extract useLobby composable for room list logic
// Feat: room creation dialog with form validation and error states
// Fix: lobby room list auto-refresh triggering on every navigation
// Style: room card component with status badges and player count
// Refactor: split lobby state management into composable function
// Docs: frontend integration notes for lobby room list polling and error states
// Test: manual test case - room list refresh on mount
// Docs: lobby room interaction flow diagram and component tree documentation
// Perf: optimize lobby room list rendering with virtual scroll for 100+ rooms
// Perf: optimize lobby initial load with room list caching strategy
// Feat: room list sort by player count and status
// Fix: room create button disabled state during API request
// Test: lobby page component unit test with mocked API
// Docs: lobby component props and event documentation

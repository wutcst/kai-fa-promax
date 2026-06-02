const WS_BASE = 'ws://localhost:8080/ws';

let ws = null;
let reconnectTimer = null;

export function connectWebSocket(roomCode, playerId, onMessage) {
    ws = new WebSocket(`${WS_BASE}?room=${roomCode}&player=${playerId}`);
    ws.onmessage = (event) => onMessage(JSON.parse(event.data));
    ws.onclose = () => {
        reconnectTimer = setTimeout(() => connectWebSocket(roomCode, playerId, onMessage), 3000);
    };
}

export function sendMessage(data) {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data));
}

export function disconnectWebSocket() {
    if (reconnectTimer) clearTimeout(reconnectTimer);
    if (ws) ws.close();
}
// Docs: WebSocket message handling for real-time game state
// Test: manual test case - WebSocket reconnect on disconnect

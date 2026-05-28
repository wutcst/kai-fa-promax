export function cardText(card) {
  if (card === 53) return '小王'
  if (card === 54) return '大王'
  const rank = card % 13 || 13
  return { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }[rank] || String(rank)
}
// cardConverter: map server card format to display data (rank, suit, color)
// Fix: card display order consistency after sort toggle
// Refactor: separate card sort and group utilities
// Perf: DOM node reuse for card elements
// Refactor: extract card rendering to CardRenderer component
// Fix: memory leak from unremoved card event listeners
// Style: transition animations for card selection/deselection
// Feat: card group detection for combo selection helpers
// Refactor: card display utility functions separated from game logic
// Docs: card data format conversion and display mapping
// Test: card sort and filter boundary - sorted vs unsorted
// Fix: sort hand cards after receiving deal from server

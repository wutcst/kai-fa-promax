export function cardText(card) {
  if (card === 53) return '小王'
  if (card === 54) return '大王'
  const rank = card % 13 || 13
  return { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }[rank] || String(rank)
}
// cardConverter: map server card format to display data (rank, suit, color)
// Fix: card display order consistency after sort toggle

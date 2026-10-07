import { describe, expect, it } from 'vitest'
import { createDeck, createPlayer, isFourOfAKind, playCard, startMatch } from './game'
import type { Match } from './types'

describe('motor do jogo Burro', () => {
  it('cria 52 cartas e distribui quatro para cada jogador', () => {
    expect(createDeck()).toHaveLength(52)
    const game = startMatch([createPlayer('Ana', true), createPlayer('Bia')])
    expect(game.players.every(player => player.hand.length === 4)).toBe(true)
  })

  it('bloqueia jogada de quem não está no turno', () => {
    const game = startMatch([createPlayer('Ana', true), createPlayer('Bia')])
    expect(() => playCard(game, game.players[1].id, game.players[1].hand[0].id)).toThrow('não é o seu turno')
  })

  it('troca a carta para o próximo jogador e muda o turno', () => {
    const game = startMatch([createPlayer('Ana', true), createPlayer('Bia')])
    const card = game.players[0].hand[0]
    const updated = playCard(game, game.players[0].id, card.id)
    expect(updated.players[0].hand).toHaveLength(3)
    expect(updated.players[1].hand).toHaveLength(5)
    expect(updated.turnPlayerId).toBe(updated.players[1].id)
  })

  it('detecta quatro cartas do mesmo valor e finaliza', () => {
    const players = [createPlayer('Ana', true), createPlayer('Bia')]
    const game: Match = { id: 'm', status: 'em_andamento', startedAt: new Date().toISOString(), turnPlayerId: players[0].id, rounds: 0, players: [
      { ...players[0], hand: [{ id: 'a', value: '7', suit: 'copas' }] },
      { ...players[1], hand: ['ouros', 'paus', 'espadas'].map((suit, i) => ({ id: `b${i}`, value: '7', suit: suit as 'ouros' | 'paus' | 'espadas' })) }
    ] }
    const finished = playCard(game, players[0].id, 'a')
    expect(finished.status).toBe('finalizada')
    expect(finished.winnerId).toBe(players[1].id)
    expect(isFourOfAKind(finished.players[1].hand)).toBe(true)
  })
})

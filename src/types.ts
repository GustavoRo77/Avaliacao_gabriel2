export type Suit = 'copas' | 'ouros' | 'paus' | 'espadas'
export type GameStatus = 'aguardando' | 'em_andamento' | 'finalizada' | 'cancelada' | 'interrompida'
export type MessageType = 'SOLICITACAO_ENTRADA' | 'JOGADOR_ENTROU' | 'PARTIDA_INICIADA' | 'JOGADA' | 'TROCA_REALIZADA' | 'JOGADOR_COMPLETOU' | 'PARTIDA_FINALIZADA' | 'JOGADOR_DESCONECTADO' | 'RECONEXAO'

export interface Card { id: string; value: string; suit: Suit }
export interface Player { id: string; name: string; isHost: boolean; connected: boolean; hand: Card[]; burro: string; deviceId?: string }
export interface BluetoothMessage { type: MessageType; matchId: string; playerId: string; payload: Record<string, unknown>; sentAt: string }
export interface Match { id: string; status: GameStatus; startedAt: string; endedAt?: string; players: Player[]; turnPlayerId: string; rounds: number; winnerId?: string; penalizedId?: string; endReason?: string }
export interface HistoryItem extends Match { localResult: 'vitória' | 'penalizado' | 'participou' }

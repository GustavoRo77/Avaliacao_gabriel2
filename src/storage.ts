import { Preferences } from '@capacitor/preferences'
import type { HistoryItem } from './types'
const KEY = 'burro-history-v1'
export async function getHistory(): Promise<HistoryItem[]> { const { value } = await Preferences.get({ key: KEY }); return value ? JSON.parse(value) : [] }
export async function saveHistory(item: HistoryItem) { const all = await getHistory(); await Preferences.set({ key: KEY, value: JSON.stringify([item, ...all]) }) }
export async function deleteHistory(id: string) { await Preferences.set({ key: KEY, value: JSON.stringify((await getHistory()).filter(x => x.id !== id)) }) }
export async function clearHistory() { await Preferences.remove({ key: KEY }) }

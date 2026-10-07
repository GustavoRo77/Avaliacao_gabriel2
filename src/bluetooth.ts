import { Capacitor, registerPlugin } from '@capacitor/core'
import type { BluetoothMessage } from './types'
type Device = { deviceId: string; name: string }
type NativeBle = { host(): Promise<void>; scan(): Promise<void>; connect(options: { deviceId: string }): Promise<void>; send(options: { message: string }): Promise<void>; sendTo(options: { deviceId: string; message: string }): Promise<void>; stop(): Promise<void>; addListener(event: string, listener: (data: Record<string, string>) => void): Promise<{ remove(): void }> }
const native = registerPlugin<NativeBle>('BurroBluetooth')
export const GAME_SERVICE = '9e1b0001-7e55-4c75-8e20-000000000001'
export const isNativeBluetooth = () => Capacitor.getPlatform() === 'android'
export async function enableBluetooth() { if (!isNativeBluetooth()) throw new Error('Use o modo demonstração no navegador.'); await native.scan(); await native.stop() }
export function validateMessage(value: unknown): value is BluetoothMessage { if (!value || typeof value !== 'object') return false; const m = value as Partial<BluetoothMessage>; return typeof m.type === 'string' && typeof m.matchId === 'string' && typeof m.playerId === 'string' && !!m.payload && typeof m.sentAt === 'string' }
export function message(type: BluetoothMessage['type'], matchId: string, playerId: string, payload: Record<string, unknown>): BluetoothMessage { return { type, matchId, playerId, payload, sentAt: new Date().toISOString() } }
export async function startHost() { if (!isNativeBluetooth()) throw new Error('Bluetooth real exige o aplicativo Android instalado em um celular.'); await native.host() }
export async function searchRooms(onDevice: (device: Device) => void) { if (!isNativeBluetooth()) throw new Error('Bluetooth real exige o aplicativo Android instalado em um celular.'); await native.addListener('deviceFound', data => onDevice({ deviceId: data.deviceId, name: data.name })); await native.scan() }
export async function joinRoom(deviceId: string) { await native.connect({ deviceId }) }
export async function send(value: BluetoothMessage, deviceId?: string) { const encoded = JSON.stringify(value); if (deviceId) return native.sendTo({ deviceId, message: encoded }); return native.send({ message: encoded }) }
export async function listen(onMessage: (value: BluetoothMessage, deviceId: string) => void, onConnection: (state: string, deviceId: string) => void, onError: (error: string) => void) { if (!isNativeBluetooth()) return; await native.addListener('message', data => { try { const value = JSON.parse(data.message); if (validateMessage(value)) onMessage(value, data.deviceId) } catch { onError('Mensagem Bluetooth inválida foi ignorada.') } }); await native.addListener('connection', data => onConnection(data.message, data.deviceId)); await native.addListener('error', data => onError(data.message)) }
export async function stopBluetooth() { if (isNativeBluetooth()) await native.stop() }

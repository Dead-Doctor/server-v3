import {openSocket} from '../ws';

interface PacketTypeMap {
    'playerJoined': Player
    'playerConnectionChange': { player: PlayerId, connected: boolean }
}

interface Packet<K extends keyof PacketTypeMap> {
    type: K
    data: PacketTypeMap[K]
}

export type PlayerId = string

export interface Player {
    id: PlayerId
    name: string
    verified: boolean
    avatar: string | null
    connected: boolean
}

export const socket = openSocket<Packet<keyof PacketTypeMap>>("/music-guesser")
export const isPacket = <T extends keyof PacketTypeMap>(packet: Packet<any>, type: T): packet is Packet<T> => packet.type === type

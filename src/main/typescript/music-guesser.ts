import { openSocket } from './ws.js';

interface PacketTypeMap {
    'playerJoined': Player
    'playerConnectionChange': { player: PlayerId, connected: boolean }
}

interface Packet<K extends keyof PacketTypeMap> {
    type: K
    data: PacketTypeMap[K]
}

type PlayerId = string

interface Player {
    id: PlayerId
    name: String
    verified: Boolean
    avatar: String | null
    connected: Boolean
}

const socket = openSocket<Packet<keyof PacketTypeMap>>("/music-guesser")
const dataScript = document.querySelector('script[type="application/json"]')!
const leaderboard = document.querySelector('.leaderboard')!

let players: Player[]
const renderPlayers = () => {
    const entries: Node[] = []
    for (const player of players) {
        //TODO: use templates
        const row = document.createElement('p')
        row.innerHTML = `[${player.avatar}] ${player.name} (${player.verified}): ${player.connected}`
        entries.push(row)
    }
    leaderboard.replaceChildren(...entries)
}

const isPacket = <T extends keyof PacketTypeMap>(packet: Packet<any>, type: T): packet is Packet<T> => packet.type === type

socket.receive(packet => {
    if (isPacket(packet, 'playerJoined')) {
        players.push(packet.data)
        renderPlayers()
    } else if (isPacket(packet, 'playerConnectionChange')) {
        const player = players.find(p => p.id === packet.data.player)
        if (player == null) return
        player.connected = packet.data.connected
        renderPlayers()
    }
})

const defaultData: Player[] = JSON.parse(dataScript.textContent!)
players = defaultData
renderPlayers()
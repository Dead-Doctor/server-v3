import { bcs } from "@iota/bcs";
import { connectChannel, type Channel } from "../channel"
import type { You, Lobby, PlayerId } from "../lobby";
import { getData } from "../routing";

const GAME_PORT = 100;

export let you: You = $state(getData('youInfo'));
export let lobby: Lobby = $state(getData('lobbyInfo'));

export const connectGameChannel = (): Channel => {
    const path = location.pathname.split('/')
    const id = path[path.length - 1]
    const channel = connectChannel(`/lobby/${id}`, GAME_PORT)
    channel.receiverWith(onFinish, bcs.string())
    return channel
}

const onFinish = (pathname: string) => {
    location.pathname = pathname;
}

export let isOperator = () => you.id === lobby.host || you.admin
export const playerById = (id: PlayerId) => lobby.players.find(p => p.id === id)!
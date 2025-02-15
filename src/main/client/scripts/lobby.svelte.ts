import { getData } from "./routing";

export type PlayerId = string;

export interface You {
    id: PlayerId;
    admin: boolean;
}

export interface Player {
    id: PlayerId;
    name: string;
    verified: boolean;
    avatar: string | null | undefined;
    active: boolean;
    score: number;
}

export interface Lobby {
    players: Player[];
    host: PlayerId;
}

export let you: You = $state(getData('youInfo'));
export let lobby: Lobby = $state(getData('lobbyInfo'));

export let isOperator = () => you.id === lobby.host || you.admin
export const playerById = (id: PlayerId) => lobby.players.find(p => p.id === id)
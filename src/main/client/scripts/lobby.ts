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
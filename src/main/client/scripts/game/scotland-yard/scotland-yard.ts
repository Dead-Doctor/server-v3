export type Enum<T extends {}> = T[keyof T];

export interface MapInfo {
    id: string;
    name: string;
    version: number;
}

export interface MapData {
    boundary: Shape;
    minZoom: number;
    intersectionRadius: number;
    connectionWidth: number;
    intersections: IntersectionData[];
    connections: ConnectionData[];
}

export interface IntersectionData {
    id: number;
    pos: Point;
}

export interface ConnectionData {
    id: number;
    from: number;
    to: number;
    type: Transport;
    shape: Shape;
}

export const transport = {
    TAXI: 'taxi',
    BUS: 'bus',
    TRAM: 'tram',
    TRAIN: 'train',
} as const;
export type Transport = Enum<typeof transport>;

export interface Shape {
    from: Point;
    to: Point;
}

export interface Point {
    lat: number;
    lon: number;
}

export const Points = {
    add: (a: Point, b: Point): Point => ({
        lat: a.lat + b.lat,
        lon: a.lon + b.lon,
    }),
};

export const team = {
    NONE: 'none',
    MISTER_X: 'misterX',
    DETECTIVES: 'detectives',
} as const;
export type Team = Enum<typeof team>;

export const teamNames: { [_ in Team]: string } = {
    [team.NONE]: 'Spectators',
    [team.MISTER_X]: 'Mister X',
    [team.DETECTIVES]: 'Detectives',
};

export const role = {
    MISTER_X: 'misterX',
    DETECTIVE1: 'detective1',
    DETECTIVE2: 'detective2',
    DETECTIVE3: 'detective3',
    DETECTIVE4: 'detective4',
    DETECTIVE5: 'detective5',
    DETECTIVE6: 'detective6',
} as const;
export type Role = Enum<typeof role>;

export const roleNames: { [_ in Role]: string } = {
    [role.MISTER_X]: 'Mister X',
    [role.DETECTIVE1]: 'Detective 1',
    [role.DETECTIVE2]: 'Detective 2',
    [role.DETECTIVE3]: 'Detective 3',
    [role.DETECTIVE4]: 'Detective 4',
    [role.DETECTIVE5]: 'Detective 5',
    [role.DETECTIVE6]: 'Detective 6',
};
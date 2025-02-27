export interface MapData {
    boundary: Shape
    minZoom: number
    intersectionRadius: number
    connectionWidth: number
    intersections: IntersectionData[],
    connections: ConnectionData[],
}

export interface IntersectionData {
    id: number
    pos: Point
}

export interface ConnectionData {
    id: number
    from: number
    to: number
    type: Transport
    shape: Shape
}

export const transport = {
    TAXI: 'taxi',
    BUS: 'bus',
    TRAM: 'tram',
    TRAIN: 'train'
} as const
export type Transport = typeof transport[keyof typeof transport]

export interface Shape {
    from: Point
    to: Point
}

export interface Point {
    lat: number
    lon: number
}

export const Points = {
    add: (a: Point, b: Point): Point => ({
        lat: a.lat + b.lat,
        lon: a.lon + b.lon
    })
}
import { connectChannel, type Channel } from "../channel"

const LOBBY_RECEIVER_COUNT = 8;
const LOBBY_DESTINATION_COUNT = 6;

export const connectGameChannel = (): Channel => {
    const path = location.pathname.split('/')
    const id = path[path.length - 1]
    return connectChannel(`/lobby/${id}`, LOBBY_DESTINATION_COUNT, LOBBY_RECEIVER_COUNT)
}
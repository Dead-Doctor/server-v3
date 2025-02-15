import { bcs } from "@iota/bcs";
import { connectChannel, type Channel } from "../channel"

const GAME_PORT = 100;

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
import { bcs } from "./bcs";
import { connectChannel } from "./channel";

const channel = connectChannel();
channel.receiverWith(onMessage, bcs.int)

function onMessage(value: number) {
    console.log(value)
}
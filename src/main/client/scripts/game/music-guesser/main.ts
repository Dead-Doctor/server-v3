import { bcs } from '@iota/bcs';
import { connectGameChannel } from '../game';

const channel = connectGameChannel()
const sendHello = channel.destinationWith(bcs.struct('SomePacket', {
    a: bcs.string(),
    value: bcs.u32()
}))
channel.receiverWith(onAnswer, bcs.u32())

setTimeout(() => {
    sendHello({
        a: 'abc',
        value: 69
    })
}, 1000)

function onAnswer(value: number) {
    console.log(`Received answer: ${value}`)
}
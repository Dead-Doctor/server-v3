import { bcs } from '@iota/bcs';
import { connectChannel } from '../../channel';

const channel = connectChannel()
const sendHello = channel.destinationWith(bcs.struct('SomePacket', {
    a: bcs.string(),
    value: bcs.u32()
}))

setTimeout(() => {
    sendHello({
        a: 'abc',
        value: 69
    })
}, 1000)
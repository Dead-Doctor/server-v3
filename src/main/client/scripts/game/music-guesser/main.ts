import { bcs } from '@iota/bcs';
import { openSocketBinary } from '../../ws';

const wsBin = openSocketBinary()
const sendHello = wsBin.destinationWith(bcs.struct('SomePacket', {
    a: bcs.string(),
    value: bcs.u32()
}))

//TODO: test
sendHello({
    a: 'abc',
    value: 69
})
import { bcs } from '@iota/bcs';

// const segments = location.pathname.split('/')
// const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + '/lobby/' + segments[segments.length - 1] + '/ws');
const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + location.pathname + '/ws');

const SomePacketStructure = bcs.struct('SomePacket', {
    a: bcs.string(),
    value: bcs.u32()
})

enum Destination {
    HELLO_DESTINATION
}


const dataPacket = SomePacketStructure.serialize({
    a: "Test",
    value: 123
}).toBytes()

const data = new Uint8Array(dataPacket.length + 1)

data[0] = Destination.HELLO_DESTINATION
data.set(dataPacket, 1)

socket.addEventListener('open', () => {
    console.log(`[Websocket] Connected: ${socket.url}`);

    socket.send(data)
});

socket.addEventListener('close', (e) => {
    console.log(`[Websocket] Closed (${e.code}): ${e.reason}`);
});

socket.addEventListener('error', (e) => {
    console.log(`[Websocket] Error:`, e);
});

socket.addEventListener('message', (e) => {
    console.log(`[Websocket] Message:`, e);
})
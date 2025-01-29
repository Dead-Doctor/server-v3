import type { BcsType } from "@iota/bcs";

export interface Channel {
    destination(): () => void
    destinationWith<T>(dataType: BcsType<T>): (data: T) => void
    receiver(handler: () => void): void
    receiverWith<T>(handler: (data: T) => void, dataType: BcsType<T>): void
    onDisconnect(handler: (e: CloseEvent) => void): void
}

export const connectChannel = (pathname: string = location.pathname): Channel => {
    const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + pathname + '/ws');

    let destinationCount = 0;
    let receivers: ((data: Uint8Array) => void)[] = []

    socket.addEventListener('open', () => {
        console.log(`[Channel] Connected: ${socket.url}`);
    });

    socket.addEventListener('close', (e) => {
        console.log(`[Channel] Closed (${e.code}): ${e.reason}`);
    });

    socket.addEventListener('error', (e) => {
        console.log(`[Channel] Error: ${e}`);
    });

    socket.addEventListener('message', async (e: MessageEvent<Blob>) => {
        const binaryData = await e.data.bytes()
        const receiver = receivers[binaryData[0]]
        console.log(`[Channel] Received: ${binaryData}`);
        receiver(binaryData.slice(1))
    })

    return {
        destination() {
            const i = destinationCount++;

            return () => {
                const packet = new Uint8Array(1)
                packet[0] = i

                console.log(`[Channel] Sent: ${packet}`);
                socket.send(packet)
            }
        },
        destinationWith<T>(dataType: BcsType<T>) {
            const i = destinationCount++;

            return (data: T) => {
                const binary = dataType.serialize(data).toBytes()

                const packet = new Uint8Array(binary.length + 1)
                packet[0] = i
                packet.set(binary, 1)

                console.log(`[Channel] Sent: ${packet}`);
                socket.send(packet)
            }
        },
        receiver(handler: () => void) {
            receivers.push(handler)
        },
        receiverWith<T>(handler: (data: T) => void, dataType: BcsType<T>) {
            receivers.push((data: Uint8Array) => {
                handler(dataType.parse(data))
            })
        },
        onDisconnect(handler: (e: CloseEvent) => void) {
            socket.addEventListener('close', handler)
        }
    }
}
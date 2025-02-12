import type { BcsType } from "@iota/bcs";

export interface Channel {
    destination(): () => void
    destinationWith<T>(dataType: BcsType<T>): (data: T) => void
    receiver(handler: () => void): void
    receiverWith<T>(handler: (data: T) => void, dataType: BcsType<T>): void
    disconnection(handler: (e: CloseEvent) => void): void
}

export const connectChannel = (pathname: string = location.pathname, port: number | null = null): Channel => {
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
        if (port != null && binaryData[0] != port) return

        const offset = port == null ? 0 : 1
        const id = binaryData[offset]
        if (id < 0 || id >= receivers.length) return
        const receiver = receivers[id]
        
        console.log(`[Channel] Received: ${binaryData}`);
        receiver(binaryData.slice(offset + 1))
    })

    return {
        destination() {
            const i = destinationCount++;

            return () => {
                const offset = port == null ? 0 : 1
                const packet = new Uint8Array(offset + 1)
                if (port != null)
                    packet[0] = port
                packet[offset] = i

                console.log(`[Channel] Sent: ${packet}`);
                socket.send(packet)
            }
        },
        destinationWith<T>(dataType: BcsType<T>) {
            const i = destinationCount++;

            return (data: T) => {
                const binary = dataType.serialize(data).toBytes()

                const offset = port == null ? 0 : 1
                const packet = new Uint8Array(offset + 1 + binary.length)
                if (port != null)
                    packet[0] = port
                packet[offset] = i
                packet.set(binary, offset + 1)

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
        disconnection(handler: (e: CloseEvent) => void) {
            socket.addEventListener('close', handler)
        }
    }
}
import type { BcsType } from "@iota/bcs";

export interface SocketAddressable<T> {
    send(destination: string, content?: any): void
    receive(callback: (data: T) => any): void
    raw: WebSocket
}

export const openSocket = <T>(pathname: string = location.pathname): SocketAddressable<T> => {
    const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + pathname + '/ws');

    socket.addEventListener('open', () => {
        console.log(`[Websocket] Connected: ${socket.url}`);
    });

    socket.addEventListener('close', (e) => {
        console.log(`[Websocket] Closed (${e.code}): ${e.reason}`);
    });

    socket.addEventListener('error', (e) => {
        console.log(`[Websocket] Error:`, e);
    });

    return {
        send(destination: string, content: any = "") {
            console.log(`[Websocket] Send:`, content);
            socket.send(`${destination}\n${JSON.stringify(content)}`)
        },
        receive(callback: (data: T) => any) {
            socket.addEventListener('message', (e) => {
                try {
                    const data: T = JSON.parse(e.data)
                    console.log(`[Websocket] Received:`, data);
                    callback(data)
                } catch (e) {
                    if (e instanceof SyntaxError) {
                        console.log(`[Websocket] Invalid message:`, e);
                        return
                    }
                    throw e
                }
            })
        },
        raw: socket
    }
}

export interface SocketBinary {
    destination(): () => void
    destinationWith<T>(dataType: BcsType<T>): (data: T) => void
    receiver(handler: () => void): void
    receiverWith<T>(handler: (data: T) => void, dataType: BcsType<T>): void
    onDisconnect(handler: (e: CloseEvent) => void): void
}

export const openSocketBinary = (pathname: string = location.pathname): SocketBinary => {
    const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + pathname + '/ws');

    let destinationCount = 0;
    let receivers: ((data: Uint8Array) => void)[] = []

    socket.addEventListener('open', () => {
        console.log(`[Websocket] Connected: ${socket.url}`);
    });

    socket.addEventListener('close', (e) => {
        console.log(`[Websocket] Closed (${e.code}): ${e.reason}`);
    });

    socket.addEventListener('error', (e) => {
        console.log(`[Websocket] Error: ${e}`);
    });

    socket.addEventListener('message', e => {
        const binaryData = e.data as Uint8Array
        console.log(`[Websocket] Received: ${binaryData}`);
        const receiver = receivers[binaryData[0]]
        receiver(binaryData.subarray(1))
    })

    return {
        destination() {
            const i = destinationCount++;

            return () => {
                const packet = new Uint8Array(1)
                packet[0] = i

                console.log(`[Websocket] Sent: ${packet}`);
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

                console.log(`[Websocket] Sent: ${packet}`);
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
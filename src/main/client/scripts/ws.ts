export const openSocket = <T>(pathname: string = location.pathname) => {
    const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + pathname + '/ws');

    socket.addEventListener('open', () => {
        console.log(`[Websocket] Connected: ${socket.url}`);
    });

    socket.addEventListener('message', (e) => {
        console.log(`[Websocket] Message:`, JSON.parse(e.data));
    });

    socket.addEventListener('close', (e) => {
        console.log(`[Websocket] Closed (${e.code}): ${e.reason}`);
    });

    socket.addEventListener('error', (e) => {
        console.log(`[Websocket] Error:`, e);
    });

    return {
        send(destination: string, content: any = "") {
            socket.send(`${destination}\n${JSON.stringify(content)}`)
        },
        receive(callback: (data: T) => any) {
            socket.addEventListener('message', (e) => {
                try {
                    const data: T = JSON.parse(e.data)
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
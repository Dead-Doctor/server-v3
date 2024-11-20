export const openSocket = <T>(pathname: string = location.pathname) => {
    const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + pathname + '/ws');

    socket.addEventListener('open', () => {
        console.log('connected');
    });

    socket.addEventListener('message', (e) => {
        console.log('message: ', e.data);
        if (e.data === "")
            console.log(e)
    });

    socket.addEventListener('close', (e) => {
        console.log('closed: ', e);
    });

    socket.addEventListener('error', (e) => {
        console.log('error: ', e);
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
                        console.error("Invalid websocket message:", e)
                        return
                    }
                    throw e
                }
            })
        }
    }
}
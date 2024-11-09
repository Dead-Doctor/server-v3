export const socket = new WebSocket((location.protocol === "https:" ? 'wss:' : 'ws:') + '//' + location.host + location.pathname + '/ws');


window.addEventListener('load', () => {
    console.log('loaded');
});

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

export const sendAddressed = (destination: string, content: any = "") => {
    socket.send(`${destination}\n${JSON.stringify(content)}`)
}
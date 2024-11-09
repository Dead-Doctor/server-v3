import { socket, sendAddressed } from './ws.js'

const messages = document.querySelector('.messages')!
const msgInput = document.querySelector('input')!

msgInput.addEventListener('keyup', (e) => {
    if (e.key === 'Enter') {
        sendAddressed("sendMessage", msgInput.value)
        msgInput.value = ''
    }
})

socket.addEventListener('message', (e) => {
    let content
    try {
        content = JSON.parse(e.data)
    } catch (e) {
        if (e instanceof SyntaxError) {
            console.error("Invalid websocket message:", e)
            console.log(e)
            return
        }
        console.log(typeof e)
        throw e
    }

    const span = document.createElement('span')
    span.textContent = content
    messages.append(span)
});

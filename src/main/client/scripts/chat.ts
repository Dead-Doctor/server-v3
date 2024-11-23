import { openSocket } from './ws'

const socket = openSocket<string>()
const messages = document.querySelector('.messages')!
const msgInput = document.querySelector('input')!

msgInput.addEventListener('keyup', (e) => {
    if (e.key === 'Enter') {
        socket.send("sendMessage", msgInput.value)
        msgInput.value = ''
    }
})

socket.receive(content => {
    const span = document.createElement('span')
    span.textContent = content
    messages.append(span)
})
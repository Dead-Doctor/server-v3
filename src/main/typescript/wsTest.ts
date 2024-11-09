import { socket } from "./ws.js";

const accountsElement = document.querySelector('.accounts')!
const data = fetch(location.pathname + '/data')

type AccountInfo = { name: string, avatar: string };
socket.addEventListener('message', (e) => {
    const accounts: AccountInfo[] = JSON.parse(e.data)
    const images = accounts.map((value, i, arr) => {
        const image = document.createElement('img')
        image.src = value.avatar
        image.alt = `Avatar of ${value.name}`
        return image
    })
    accountsElement.replaceChildren(...images)
});

console.log(await (await data).json());
console.log('done')
import { openSocket } from "./ws";

const socket = openSocket<AccountInfo[]>()
const accountsElement = document.querySelector('.accounts')!
const data = fetch(location.pathname + '/data')

type AccountInfo = { name: string, avatar: string };
socket.receive((accounts) => {
    const images = accounts.map(value => {
        const image = document.createElement('img')
        image.src = value.avatar
        image.alt = `Avatar of ${value.name}`
        return image
    })
    accountsElement.replaceChildren(...images)
});

console.log(await (await data).json());
console.log('done')
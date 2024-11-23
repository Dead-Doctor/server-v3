import {mount} from 'svelte'
import LobbyApp from './lobbyApp.svelte'

mount(LobbyApp, {
    target: document.querySelector('.content')!,
})
<script lang="ts">
    import {openSocket} from '../ws'

    interface PacketTypeMap {
        'playerJoined': Player
        'playerStateChanged': { player: PlayerId, playing: boolean }
        'hostChanged': PlayerId
        'kicked': true
    }

    interface Packet<K extends keyof PacketTypeMap> {
        type: K
        data: PacketTypeMap[K]
    }

    type PlayerId = string

    interface Player {
        id: PlayerId
        name: string
        verified: boolean
        avatar: string | null
        playing: boolean
    }

    interface Game {
        you: PlayerId
        host: PlayerId
        admin: boolean
    }

    interface Popup {
        message: string
        buttonText: string
        buttonAction: () => void
    }

    const getData = <T>(id: string): T => {
        let scriptElement = document.querySelector(`script[type="application/json"]#data-${id}`)!
        return JSON.parse(scriptElement.textContent!)
    }
    let players: Player[] = $state(getData('playerInfo'))
    let game: Game = $state(getData('gameInfo'))
    let popup: Popup | null = $state(null)

    const socket = openSocket<Packet<keyof PacketTypeMap>>('/music-guesser')
    const isPacket = <T extends keyof PacketTypeMap>(packet: Packet<any>, type: T): packet is Packet<T> => packet.type === type


    socket.receive(packet => {
        if (isPacket(packet, 'playerJoined')) {
            players.push(packet.data)
        } else if (isPacket(packet, 'playerStateChanged')) {
            const player = players.find(p => p.id === packet.data.player)!
            player.playing = packet.data.playing
        } else if (isPacket(packet, 'hostChanged')) {
            game.host = packet.data
        } else if (isPacket(packet, 'kicked')) {
            popup = {
                message: 'You got kicked',
                buttonText: 'Close',
                buttonAction() {
                    location.pathname = '/'
                }
            }
        }
    })

    socket.raw.addEventListener('close', e => {
        if (e.code == 1001) return
        popup = {
            message: 'Lost connection',
            buttonText: 'Reload',
            buttonAction() {
                location.reload()
            }
        }
    })
</script>

<section>
    <div class="leaderboard">
        {#each players as player, i}
            <div class="row">
                <div class="rank">#{i + 1}</div>
                <div class="player" class:connected={player.playing}>
                    {#if player.verified}
                        <img src={player.avatar} alt="Profile">
                    {/if}
                    <span>{player.name}</span>
                    {#if player.verified}
                        &#x2714;
                    {/if}
                    {#if player.playing}
                        {#if player.id === game.you}
                            <span class="placard">You</span>
                        {/if}
                        {#if player.id === game.host}
                            <span class="placard">Host</span>
                        {/if}
                        {#if game.you === game.host || game.admin}
                            <button onclick={() => socket.send("promote", player.id)}>Promote</button>
                            <button onclick={() => socket.send("kick", player.id)}>Kick</button>
                        {/if}
                    {/if}
                </div>
                <div class="score">0</div>
            </div>
        {/each}
    </div>
    <div class="overlay" class:visible={popup != null}>
        <div class="popup">
            <h3>{popup?.message}</h3>
            <button onclick={() => popup?.buttonAction()}>{popup?.buttonText}</button>
        </div>
    </div>
</section>

<style>
    .leaderboard {
        display: grid;
        grid-template-columns: auto 1fr auto;
        background-color: var(--secondary);
        border: var(--border);
        border-radius: 1.5rem;
        font-size: 1.5em;

        .row {
            display: grid;
            grid-column: span 3;
            grid-template-columns: subgrid;
            padding: 0.8em 1.4em;
            column-gap: 1.4em;
            border-bottom: var(--border);
            justify-content: center;
            align-items: center;

            &:last-child {
                border-bottom: none;
            }

            > * {
                display: flex;
                align-items: center;
            }

            .rank {
                color: var(--muted);
                justify-content: center;
            }

            .player {
                gap: 0.5em;
                filter: brightness(50%);

                &.connected {
                    filter: none;
                }

                img {
                    height: 2em;
                    border-radius: 50%;
                }

                .placard {
                    margin-left: 0.4em;
                    padding: 0.3em 0.6em;
                    color: var(--muted);
                    background-color: var(--decoration);
                    font-size: 0.6em;
                    border-radius: 0.8em;
                }

                button {
                    margin-left: 0.4em;
                    padding: 0.3em 1em;
                    color: var(--muted);
                    font-size: 0.6em;

                    &:first-of-type {
                        margin-left: 1.4em;
                    }
                }
            }

            .score {
                justify-content: center;
                font-size: 1.5em;
                font-weight: bold;
            }
        }
    }

    .overlay {
        display: none;
        position: fixed;
        left: 0;
        top: 0;
        right: 0;
        bottom: 0;
        justify-content: center;
        align-items: center;
        backdrop-filter: brightness(80%) blur(4px);

        &.visible {
            display: flex;
        }

        .popup {
            display: flex;
            flex-direction: column;
            padding: 1.5rem;
            gap: 1rem;
            background-color: var(--background);
            border: var(--border);
            border-radius: 1rem;

            button {
                align-self: end;
            }
        }
    }

    :global(body):has(.visible) {
        overflow: hidden;
    }
</style>

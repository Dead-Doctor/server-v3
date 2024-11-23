<script lang="ts">
    import {isPacket, type Player, socket} from './api';

    const dataScript = document.querySelector('script[type="application/json"]')!
    const defaultData: Player[] = JSON.parse(dataScript.textContent!)

    let players: Player[] = $state(defaultData)

    socket.receive(packet => {
        if (isPacket(packet, 'playerJoined')) {
            players.push(packet.data)
        } else if (isPacket(packet, 'playerConnectionChange')) {
            const player = players.find(p => p.id === packet.data.player)!
            player.connected = packet.data.connected
        }
    })
</script>

<main>
    <ul class="leaderboard">
        {#each players as player}
            <li class="player">
                <img class:connected={player.connected} src={player.avatar} alt="Profile">
                <span>{player.name}</span>
                {#if player.verified}
                    &#x2714;
                {/if}
            </li>
        {/each}
    </ul>
</main>

<style>
    .leaderboard {
        display: flex;
        flex-direction: column;
        gap: 1rem;
    }
    .player {
        display: flex;
        align-items: center;
        gap: 1rem;

        img {
            height: 64px;
            border-radius: 50%;
            filter: brightness(30%);

            &.connected {
                filter: none;
            }
        }
    }
</style>

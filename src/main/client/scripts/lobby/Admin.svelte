<script lang="ts">
    import { type Lobby } from '../lobby.svelte';
    import { getData } from '../routing';
    import PlayerIcon from './PlayerIcon.svelte';

    const lobbies: Lobby[] = getData('lobbies');
</script>

<h1>Lobby Admin</h1>
<section class="grid">
    {#each lobbies as lobby}
        <div>
            <h3>{lobby.id}</h3>
            <h4>
                Host:
                <PlayerIcon player={lobby.players.find((p) => p.id === lobby.host)!} size={'2rem'} />
            </h4>
            <h4>
                Players:
                {#each lobby.players.filter((p) => p.id !== lobby.host) as player}
                    <PlayerIcon {player} size={'2rem'} />
                {/each}
            </h4>
        </div>
    {/each}
</section>

<style>
    h4 {
        display: flex;
        gap: 0.5rem;
        align-items: center;
    }
</style>
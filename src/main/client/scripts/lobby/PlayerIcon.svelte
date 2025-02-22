<script lang="ts">
    import type { Player } from "../lobby.svelte";

    interface Params {
        player: Player
        size?: string
    }

    let { player, size = '1rem' }: Params = $props()
</script>

{#if player?.verified}
    <img class="icon" src={player.avatar} alt={player.name} style={`--size: ${size};`}>
{:else}
    <div class="icon" style={`--size: ${size};`}>
        <span>{player?.name}</span>
    </div>
{/if}

<style>
    .icon {
        height: var(--size);
        border: var(--border);
        border-radius: calc(var(--size) / 2);
    }

    div {
        display: inline-flex;
        align-items: center;
        padding: 0 0.6rem;
        font-size: var(--size);
        background-color: var(--secondary);

        span {
            font-size: 0.6em;
        }
    }
</style>
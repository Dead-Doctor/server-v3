<script lang="ts">
    import type { Player } from "../lobby";

    interface Params {
        player: Player
        size?: string
    }

    let { player, size = '1rem' }: Params = $props()
</script>

{#if player?.verified}
    <img src={player.avatar} alt={player.name} style={`--size: ${size};`}>
{:else}
    <span>{player?.name}</span>
{/if}

<style>
    img {
        height: var(--size);
        border-radius: 50%;
    }
</style>
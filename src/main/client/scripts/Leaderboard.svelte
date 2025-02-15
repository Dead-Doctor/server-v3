<script lang="ts">
    import {slide} from 'svelte/transition'

    type PlayerId = string

    interface Player {
        id: PlayerId
        name: string
        verified: boolean
        avatar: string | null | undefined
        inactive?: boolean
        value: number
    }

    interface Lobby {
        you: PlayerId | null
        host: PlayerId | null
        admin: boolean
        onPromote: (id: PlayerId) => void
        onKick: (id: PlayerId) => void
    }

    interface Props {
        players: Player[]
        lobby?: Lobby
        transition?: {
            delay?: (i: number) => number
            duration?: (i: number) => number
        }
    }

    let { players, lobby, transition = {} }: Props = $props();
</script>

<div class="leaderboard">
    {#each players as player, i}
        <div class="row" transition:slide|global={Object.fromEntries(Object.entries(transition).map(entry => [entry[0], entry[1](i)]))}>
            <div class="rank">#{i + 1}</div>
            <div class="player" class:inactive={player.inactive ?? false}>
                {#if player.verified}
                    <img src={player.avatar} alt="Profile">
                {/if}
                <span>{player.name}</span>
                {#if player.verified}
                    &#x2714;
                {/if}
                {#if lobby != null && !player.inactive}
                    {#if player.id === lobby.you}
                        <span class="placard">You</span>
                    {/if}
                    {#if player.id === lobby.host}
                        <span class="placard">Host</span>
                    {/if}
                    {#if lobby.you !== null && ((lobby.you === lobby.host && player.id !== lobby.you) || lobby.admin)}
                        <button onclick={() => lobby.onPromote(player.id)}>Promote</button>
                        <button onclick={() => lobby.onKick(player.id)}>Kick</button>
                    {/if}
                {/if}
            </div>
            <div class="score">{player.value}</div>
        </div>
    {/each}
</div>

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

                &.inactive {
                    filter: brightness(50%);
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
</style>
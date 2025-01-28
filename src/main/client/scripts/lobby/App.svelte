<script lang="ts">
    import { getData } from '../routing';
    import { openSocketBinary } from '../ws';
    import Leaderboard from '../Leaderboard.svelte';
    import { fade, fly, slide } from 'svelte/transition';
    import { bcs } from '@iota/bcs';

    type PlayerId = string;
    interface You {
        id: PlayerId;
        admin: boolean;
    }
    interface Player {
        id: PlayerId;
        name: string;
        verified: boolean;
        avatar: string | null | undefined;
        active: boolean;
        score: number;
    }
    interface Lobby {
        players: Player[];
        host: PlayerId;
    }
    interface GameType {
        id: string;
        name: string;
    }
    interface Popup {
        message: string;
        buttonText: string;
        buttonDisabled: boolean;
        buttonAction: () => void;
        closable: boolean;
    }

    const socket = openSocketBinary();
    const sendCheckName = socket.destinationWith(bcs.string())
    const sendJoin = socket.destinationWith(bcs.string())
    const sendPromote = socket.destinationWith(bcs.string())
    const sendKick = socket.destinationWith(bcs.string())
    const sendGameSelected = socket.destinationWith(bcs.string())
    const sendBeginGame = socket.destination()

    socket.receiverWith(onCheckedName, bcs.vector(bcs.string()))
    socket.receiverWith(onJoin, bcs.string())
    socket.receiverWith(onPlayerJoined, bcs.struct('Player', {
        id: bcs.string(),
        name: bcs.string(),
        verified: bcs.bool(),
        avatar: bcs.option(bcs.string()),
        active: bcs.bool(),
        score: bcs.u32(),
    }))
    socket.receiverWith(onPlayerActiveChanged, bcs.struct('PlayerActiveChanged', {
        player: bcs.string(),
        active: bcs.bool()
    }))
    socket.receiverWith(onHostChanged, bcs.string())
    socket.receiverWith(onKicked, bcs.string())
    socket.receiverWith(onGameSelected, bcs.string())
    socket.receiverWith(onGameStarted, bcs.string())

    function onCheckedName(errors: Iterable<string>) {
        usernameInputErrors = errors as string[];
        console.log(usernameInputErrors)
        popup!.buttonDisabled = usernameInputErrors.length != 0;
    }

    function onJoin(id: PlayerId) {
        popup = null;
        you.id = id;
    }

    function onPlayerJoined(player: Player) {
        lobby.players.push(player);
    }

    function onPlayerActiveChanged(data: { player: PlayerId; active: boolean }) {
        const player = lobby.players.find((p) => p.id === data.player)!;
        player.active = data.active;
    }

    function onHostChanged(id: PlayerId) {
        lobby.host = id;
    }

    function onKicked(message: string) {
        popup = {
            message,
            buttonText: 'Close',
            buttonDisabled: false,
            buttonAction() {
                location.pathname = '/';
            },
            closable: false,
        };
    }

    function onGameSelected(game: string) {
        gameSelected = game;
    }

    function onGameStarted(pathname: string) {
        location.pathname = pathname;
    }

    let you: You = $state(getData('youInfo'));
    let lobby: Lobby = $state(getData('lobbyInfo'));
    let gameTypes: GameType[] = getData('gameTypes');
    let gameSelected: string = $state(getData('gameSelected'));

    let isOperator = $derived(lobby.host === you.id || you.admin);

    let sortedPlayers = $derived(
        lobby.players
            .toSorted((a, b) => b.score - a.score)
            .map((player) => ({
                id: player.id,
                name: player.name,
                verified: player.verified,
                avatar: player.avatar,
                inactive: !player.active,
                value: player.score,
            }))
    );

    let popup: Popup | null = $state(null);
    let usernameInputValue: string = $state('');
    let usernameInputErrors: string[] = $state([]);
    const youPlayer = lobby.players.find((p) => p.id === you.id);
    if (youPlayer === undefined) {
        popup = {
            message: 'Select a username:',
            buttonText: 'Join',
            buttonDisabled: true,
            buttonAction() {
                sendJoin(usernameInputValue)
            },
            closable: false,
        };
    }

    socket.onDisconnect(e => {
        if (e.code === 1001) return;
        popup = {
            message: 'Lost connection',
            buttonText: 'Reload',
            buttonDisabled: false,
            buttonAction() {
                location.reload();
            },
            closable: true,
        };
    })
</script>

<section>
    <div class="lobby">
        <h2>Lobby</h2>
        <Leaderboard
            players={sortedPlayers}
            lobby={{
                you: you.id,
                host: lobby.host,
                admin: you.admin,
                onPromote(id) { sendPromote(id) },
                onKick(id) { sendKick(id) }
            }}
        />
        <select
            name="gameSelect"
            id="gameSelect"
            disabled={!isOperator}
            bind:value={gameSelected}
            onchange={() => sendGameSelected(gameSelected)}
        >
            {#each gameTypes as type}
                <option value={type.id}>{type.name}</option>
            {/each}
        </select>
        <button disabled={!isOperator} onclick={() => sendBeginGame()}>Begin</button>
    </div>
    {#if popup !== null}
        <div class="overlay" in:fade={{ duration: 200 }} out:fade={{ delay: 300, duration: 200 }}>
            <div class="popup" in:fly={{ delay: 200, duration: 300, y: 200 }} out:fly={{ duration: 300, y: 200 }}>
                <h3>{popup.message}</h3>
                {#if popup.message === 'Select a username:'}
                    <input
                        class="username"
                        class:error={usernameInputErrors.length !== 0}
                        type="text"
                        name="username"
                        id="usernameInput"
                        placeholder="Username"
                        size="20"
                        maxlength="20"
                        bind:value={usernameInputValue}
                        oninput={() => sendCheckName(usernameInputValue)}
                    />
                    <div>
                        {#each usernameInputErrors as error (error)}
                            <p class="error" transition:slide>{@html error}</p>
                        {/each}
                        <!-- suppress css warning from samp styling -->
                        <span class="error" style="display: none"><samp></samp></span>
                    </div>
                    <span class="login"
                        >Or <a href={`/login?redirectUrl=${encodeURIComponent(location.pathname)}`}>Login</a></span
                    >
                {/if}
                <div class="actions">
                    <button onclick={() => popup?.buttonAction()} disabled={popup?.buttonDisabled}
                        >{popup.buttonText}</button
                    >
                    {#if popup.closable}
                        <button onclick={() => (popup = null)}>Close</button>
                    {/if}
                </div>
            </div>
        </div>
    {/if}
</section>

<style>
    .lobby {
        display: flex;
        flex-direction: column;
        gap: 3rem;

        button {
            align-self: end;
        }
    }

    .overlay {
        display: flex;
        position: fixed;
        inset: 0;
        justify-content: center;
        align-items: center;
        backdrop-filter: brightness(80%) blur(4px);
        z-index: 9999;

        .popup {
            display: flex;
            flex-direction: column;
            padding: 1.5rem;
            gap: 1rem;
            background-color: var(--background);
            border: var(--border);
            border-radius: 1rem;

            input.username {
                width: 35rem;
                font-size: 1.5em;
                font-weight: bold;
                outline: var(--decoration-thickness) solid transparent;
                transition: 150ms outline-color ease-in-out;

                &.error {
                    outline-color: var(--primary);
                }
            }

            div .error {
                width: 35rem;
                padding: 0.4em;
                background-color: var(--secondary);

                :global(samp) {
                    color: var(--primary);
                    font-size: 1.2em;
                    font-weight: bold;
                }
            }

            .login {
                align-self: center;
                margin-bottom: -3rem;
                color: var(--muted);
            }

            .actions {
                align-self: end;
            }
        }
    }
</style>

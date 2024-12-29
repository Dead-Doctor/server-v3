<script lang="ts">
    import { getData } from '../routing';
    import { openSocket } from '../ws';
    import Leaderboard from '../Leaderboard.svelte';
    import { fade, fly, slide } from 'svelte/transition';
    import { sineInOut } from 'svelte/easing';

    interface PacketTypeMap {
        checkedName: string[];
        join: PlayerId;
        playerJoined: Player;
        playerActiveChanged: { player: PlayerId; active: boolean };
        hostChanged: PlayerId;
        kicked: string;
    }

    interface Packet<K extends keyof PacketTypeMap> {
        type: K;
        data: PacketTypeMap[K];
    }

    type PlayerId = string;

    interface You {
        id: PlayerId;
        admin: boolean;
    }

    interface Player {
        id: PlayerId;
        name: string;
        verified: boolean;
        avatar: string | null;
        active: boolean;
        score: number;
    }

    interface Lobby {
        players: Player[];
        host: PlayerId;
    }

    interface Popup {
        message: string;
        buttonText: string;
        buttonDisabled: boolean;
        buttonAction: () => void;
        closable: boolean;
    }

    const socket = openSocket<Packet<keyof PacketTypeMap>>();
    const isPacket = <T extends keyof PacketTypeMap>(packet: Packet<any>, type: T): packet is Packet<T> =>
        packet.type === type;

    let you: You = $state(getData('youInfo'));
    let lobby: Lobby = $state(getData('lobbyInfo'));

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
                socket.send('join', usernameInputValue);
            },
            closable: false,
        };
    }

    socket.receive((packet) => {
        if (isPacket(packet, 'checkedName')) {
            usernameInputErrors = packet.data;
            popup!.buttonDisabled = usernameInputErrors.length != 0;
        } else if (isPacket(packet, 'join')) {
            popup = null;
            you.id = packet.data;
        } else if (isPacket(packet, 'playerJoined')) {
            lobby.players.push(packet.data);
        } else if (isPacket(packet, 'playerActiveChanged')) {
            const player = lobby.players.find((p) => p.id === packet.data.player)!;
            player.active = packet.data.active;
        } else if (isPacket(packet, 'hostChanged')) {
            lobby.host = packet.data;
        } else if (isPacket(packet, 'kicked')) {
            popup = {
                message: packet.data,
                buttonText: 'Close',
                buttonDisabled: false,
                buttonAction() {
                    location.pathname = '/';
                },
                closable: false,
            };
        }
    });

    socket.raw.addEventListener('close', (e) => {
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
    });
</script>

<section>
    <h2>Lobby</h2>
    <Leaderboard
        players={sortedPlayers}
        lobby={{
            you: you.id,
            host: lobby.host,
            admin: you.admin,
            socket,
        }}
    />
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
                        oninput={() => socket.send('checkName', usernameInputValue)}
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

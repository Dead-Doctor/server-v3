<script lang="ts">
    import { getData } from '../routing';
    import { connectChannel } from '../channel';
    import Leaderboard from '../Leaderboard.svelte';
    import Popup from '../Popup.svelte';
    import { type PlayerId, type Player, type GameType, lobby, you, isOperator, playerById } from '../lobby.svelte';
    import type { Component } from 'svelte';
    import { bcs, type BcsType } from '../bcs';
    type Props<T> = T extends Component<infer P, any, any> ? P : never;

    type PopupProps = Props<typeof Popup>

    //TODO: popup with invite instructions

    let gameTypes: GameType[] = getData('gameTypes');
    let gameSelected: string = $state(getData('gameSelected'));
    let gameRunning: boolean = $state(getData('gameRunning'))

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

    let popup: PopupProps = $state({
        visible: false,
        message: '',
        closable: false,
        buttonText: '',
        buttonDisabled: false,
        buttonAction() {},
        input: false,
        inputPlaceholder: 'Username:',
        inputValue: '',
        inputAction() {
            sendCheckName(popup.inputValue!)
        },
        inputErrors: [],
        login: false
    });

    const channel = connectChannel();
    const sendCheckName = channel.destinationWith(bcs.string)
    const sendJoin = channel.destinationWith(bcs.string)
    const sendPromote = channel.destinationWith(bcs.string)
    const sendKick = channel.destinationWith(bcs.string)
    const sendGameSelected = channel.destinationWith(bcs.string)
    const sendBeginGame = channel.destination()

    const bcsPlayer = bcs.struct({
        id: bcs.string,
        name: bcs.string,
        verified: bcs.boolean,
        avatar: bcs.nullable(bcs.string),
        active: bcs.boolean,
        score: bcs.int,
    })
    channel.receiverWith(onCheckedName, bcs.list(bcs.string))
    channel.receiverWith(onJoin, bcs.string)
    channel.receiverWith(onPlayerJoined, bcsPlayer)
    channel.receiverWith(onPlayerActiveChanged, bcs.struct({
        player: bcs.string,
        active: bcs.boolean
    }))
    channel.receiverWith(onPlayerScoreChanged, bcs.tuple<[BcsType<string>, BcsType<number>]>([bcs.string, bcs.int]))
    channel.receiverWith(onHostChanged, bcs.string)
    channel.receiverWith(onKicked, bcs.string)
    channel.receiverWith(onGameSelected, bcs.string)
    channel.receiverWith(onGameStarted, bcs.string)
    channel.receiver(onGameEnded)

    function onCheckedName(errors: string[]) {
        popup.inputErrors = errors;
        popup.buttonDisabled = popup.inputErrors.length != 0;
    }

    function onJoin(id: PlayerId) {
        popup.visible = false
        you.id = id;
    }

    function onPlayerJoined(player: Player) {
        lobby.players.push(player);
    }

    function onPlayerActiveChanged(data: { player: PlayerId; active: boolean }) {
        const player = playerById(data.player)!;
        player.active = data.active;
    }

    function onPlayerScoreChanged(data: [PlayerId, number]) {
        const player = playerById(data[0])!;
        player.score = data[1];
    }

    function onHostChanged(id: PlayerId) {
        lobby.host = id;
    }

    function onKicked(message: string) {
        popup.visible = true
        popup.message = message
        popup.closable = false
        popup.buttonText = 'Close'
        popup.buttonDisabled = false
        popup.buttonAction = () => {
            location.pathname = '/';
        }
        popup.input = false,
        popup.login = false
    }

    function onGameSelected(game: string) {
        gameSelected = game;
    }

    function onGameStarted(pathname: string) {
        location.pathname = pathname;
    }

    function onGameEnded() {
        gameRunning = false
    }

    const youPlayer = playerById(you.id);
    if (youPlayer === undefined) {
        popup.visible = true
        popup.message = 'Select a username:'
        popup.closable = false,
        popup.buttonText = 'Join',
        popup.buttonDisabled = false,
        popup.buttonAction = () => {
            sendJoin(popup.inputValue!)
        }
        popup.input = true,
        popup.login = true
    }

    channel.disconnection(e => {
        if (e.code === 1001) return;
        popup.visible = true
        popup.message = 'Lost connection'
        popup.closable = true
        popup.buttonText = 'Reload'
        popup.buttonDisabled = false
        popup.buttonAction = () => {
            location.reload()
        }
        popup.input = false,
        popup.login = false
    })
</script>

<section class="title">
    <h2>Lobby</h2>
    <h3><b>{lobby.id}</b></h3>
</section>
<section class="content">
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
        disabled={!isOperator() || gameRunning}
        bind:value={gameSelected}
        onchange={() => sendGameSelected(gameSelected)}
    >
        {#each gameTypes as type}
            <option value={type.id}>{type.name}</option>
        {/each}
    </select>
    <button disabled={!isOperator() || gameRunning} onclick={() => sendBeginGame()}>{gameRunning ? 'Running' : 'Begin'}</button>
</section>
<Popup
    bind:visible={popup.visible}
    message={popup.message}
    closable={popup.closable}
    buttonText={popup.buttonText}
    buttonDisabled={popup.buttonDisabled}
    buttonAction={popup.buttonAction}
    input={popup.input}
    inputPlaceholder={popup.inputPlaceholder}
    bind:inputValue={popup.inputValue}
    inputAction={popup.inputAction}
    inputErrors={popup.inputErrors}
    login={popup.login}
/>

<style>
    .title {
        display: flex;
        align-items: end;
        gap: 1rem;
        border-bottom: var(--border);
    }

    .content {
        display: flex;
        flex-direction: column;
        gap: 3rem;

        button {
            align-self: end;
        }
    }
</style>

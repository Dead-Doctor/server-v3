<script lang="ts">
    import {openSocket} from '../ws'
    import {fade, fly, slide} from 'svelte/transition'
    import {sineInOut} from 'svelte/easing'

    interface PacketTypeMap {
        checkedName: string[]
        join: PlayerId
        playerJoined: Player
        playerStateChanged: { player: PlayerId, playing: boolean }
        playerScoreChanged: { player: PlayerId, score: number }
        hostChanged: PlayerId
        kicked: string
        round: Round | null
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
        playing: boolean,
        score: number
    }

    interface Game {
        you: PlayerId | null
        host: PlayerId | null
        admin: boolean
    }

    interface Round {
        players: PlayerId[]
        question: Question
        results: { [player: PlayerId]: number } | null
    }

    interface Question {
        i: number
        song: {
            previewUrl: string
            trackName: string | null
            artistName: string | null
            artworkUrl: string | null
            releaseYear: number | null
        }
        showResult: boolean
        guesses: { [player: PlayerId]: Guess } | null
    }

    interface Guess {
        year: number
        points: number
    }

    interface Popup {
        message: string
        buttonText: string
        buttonDisabled: boolean
        buttonAction: () => void
    }

    const getData = <T>(id: string): T => {
        let scriptElement = document.querySelector(`script[type="application/json"]#data-${id}`)!
        return JSON.parse(scriptElement.textContent!)
    }
    let players: Player[] = $state(getData('playerInfo'))
    let game: Game = $state(getData('gameInfo'))
    let round: Round | null = $state(getData('round'))
    let popup: Popup | null = $state(null)

    let sortedPlayers = $derived(players.toSorted((a, b) => b.score - a.score))
    let guesses = $derived(Object.entries(round?.question.guesses ?? {}))
    let sortedGuesses = $derived(guesses.toSorted((a, b) => b[1].points - a[1].points))
    let sortedResults = $derived(Object.entries(round?.results ?? {}).toSorted((a, b) => b[1] - a[1]))

    const socket = openSocket<Packet<keyof PacketTypeMap>>('/music-guesser')
    const isPacket = <T extends keyof PacketTypeMap>(packet: Packet<any>, type: T): packet is Packet<T> => packet.type === type

    let usernameInputValue: string = $state('')
    let usernameInputErrors: string[] = $state([])
    if (game.you === null) {
        popup = {
            message: 'Select a username:',
            buttonText: 'Join',
            buttonDisabled: true,
            buttonAction() {
                socket.send('join', usernameInputValue)
            }
        }
    }

    let isOperator = $derived(game.you !== null && (game.you === game.host || game.admin))

    let minHeight = $state(0)
    const yearInputMin = 1950
    const yearInputMax = 2020
    const timelineBars = 7 * 2 + 1
    const timelineBarStep = (yearInputMax - yearInputMin) / (timelineBars - 1);
    let timelineWidth = $state(0)

    let yearInputValue = $state(1985)

    let canMakeGuess = $derived(game.you !== null && round !== null && round.players.includes(game.you) && !round.question.showResult)
    let guessLocked = $state(false)

    const guess = () => {
        guessLocked = !guessLocked;
        socket.send('guess', guessLocked ? yearInputValue : null)
    }

    socket.receive(packet => {
        if (isPacket(packet, 'checkedName')) {
            usernameInputErrors = packet.data
            popup!.buttonDisabled = usernameInputErrors.length != 0
        } else if (isPacket(packet, 'join')) {
            popup = null
            game.you = packet.data
        } else if (isPacket(packet, 'playerJoined')) {
            players.push(packet.data)
        } else if (isPacket(packet, 'playerStateChanged')) {
            const player = players.find(p => p.id === packet.data.player)!
            player.playing = packet.data.playing
        } else if (isPacket(packet, 'playerScoreChanged')) {
            const player = players.find(p => p.id === packet.data.player)!
            player.score = packet.data.score
        } else if (isPacket(packet, 'hostChanged')) {
            game.host = packet.data
        } else if (isPacket(packet, 'kicked')) {
            popup = {
                message: packet.data,
                buttonText: 'Close',
                buttonDisabled: false,
                buttonAction() {
                    location.pathname = '/'
                }
            }
        } else if (isPacket(packet, 'round')) {
            if ((packet.data?.question.i ?? -1) > (round?.question.i ?? -1)) guessLocked = false
            round = packet.data
        }
    })

    socket.raw.addEventListener('close', e => {
        if (e.code === 1001) return
        popup = {
            message: 'Lost connection',
            buttonText: 'Reload',
            buttonDisabled: false,
            buttonAction() {
                location.reload()
            }
        }
    })

    const volumeFadeDuration = 1000
    const volumeFadeInterval = 13
    const volumeFadeTicks = Math.floor(volumeFadeDuration / volumeFadeInterval);
    let fadedVolume = 0
    let volume = $state(0)

    const volumeFading = (_: Element) => {
        $effect(() => {
            fadeInVolume()
            return () => {
                fadeOutVolume()
            }
        })
    }

    const fadeInVolume = () => {
        const savedVolume = localStorage.getItem('volume')
        const targetVolume = savedVolume != null ? parseFloat(savedVolume) : 0.5

        let tick = 0
        const timer = setInterval(() => {
            tick++
            volume = fadedVolume = targetVolume * sineInOut(tick / volumeFadeTicks)
            if (tick === volumeFadeTicks)
                clearInterval(timer)
        }, volumeFadeInterval)
    }

    const fadeOutVolume = () => {
        const startVolume = volume;
        let tick = 0
        const timer = setInterval(() => {
            tick++
            volume = fadedVolume = startVolume * sineInOut(1 - tick / volumeFadeTicks)
            if (tick === volumeFadeTicks)
                clearInterval(timer)
        }, volumeFadeInterval)
    }

    const saveVolume = () => {
        if (volume === fadedVolume) return
        localStorage.setItem('volume', volume.toString())
    }
</script>

<section bind:clientHeight={minHeight} style={`min-height: ${minHeight}px`}>
    {#if game.you === null || round === null}
        <div class="lobby" transition:fly={{duration: 500, x: -300}}>
            <h2>Music Guesser</h2>
            <div class="leaderboard">
                {#each sortedPlayers as player, i}
                    <div class="row" transition:slide>
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
                                {#if game.you !== null && ((game.you === game.host && player.id !== game.you) || game.admin)}
                                    <button onclick={() => socket.send("promote", player.id)}>Promote</button>
                                    <button onclick={() => socket.send("kick", player.id)}>Kick</button>
                                {/if}
                            {/if}
                        </div>
                        <div class="score">{player.score}</div>
                    </div>
                {/each}
            </div>
            {#if isOperator}
                <button onclick={() => socket.send("beginRound")}>Begin Round</button>
            {/if}
        </div>
    {:else}
        <div class="round" transition:fly={{duration: 500, x: 300}}>
            <div class="title">
                <h2>Round</h2>
                {#key !round.question.showResult}
                    <h3 in:fly={{duration: 300, y: -30}}
                        out:fly={{duration: 300, y: 30}}>{ round.question.showResult ? 'Result' : 'Guess' }</h3>
                {/key}
            </div>
            {#if round.results == null}
                <div class="song" class:expanded={round.question.showResult}>
                    {#if !round.question.showResult}
                        <div style="display: none" use:volumeFading></div>
                    {:else}
                        <div class="info">
                            <img src={round.question.song.artworkUrl} alt="Album Cover"
                                 in:fade={{delay: 1000, duration: 200}}>
                            <div class="details">
                                <h4 in:fade={{delay: 1500, duration: 200}}>{round.question.song.trackName}</h4>
                                <h5 in:fade={{delay: 2000, duration: 200}}>{round.question.song.artistName}</h5>
                            </div>
                        </div>
                    {/if}
                    <audio src={round.question.song.previewUrl} autoplay bind:volume onvolumechange={saveVolume}
                           controls></audio>
                </div>
                <!--suppress JSUnusedGlobalSymbols -->
                <div class="timeline" bind:clientWidth={timelineWidth}>
                    {#each {length: timelineBars} as _, i}
                        <div class="bar" data-year={yearInputMin + i * timelineBarStep}></div>
                    {/each}

                    {#if canMakeGuess}
                        <input type="range" name="year" id="yearInput" min={yearInputMin} max={yearInputMax}
                               bind:value={yearInputValue} disabled={guessLocked}
                               transition:fly={{duration: 300, y: 200}}>
                        <div class="pin interactive-pin" transition:fly={{duration: 300, y: 30}}
                             style="left: {(yearInputValue - yearInputMin) / (yearInputMax - yearInputMin) * timelineWidth}px">{yearInputValue}</div>
                    {/if}

                    {#if round !== null && round.question.showResult}
                        {#each guesses as [id, guess], i (id)}
                            {@const player = players.find(p => p.id === id)}
                            <div class="pin" in:fly|global={{delay: 3000 + i * 1000, duration: 300, y: 30}} out:fade
                                 style="left: {(guess.year - yearInputMin) / (yearInputMax - yearInputMin) * timelineWidth}px">
                                {#if player?.verified}
                                    <img src={player.avatar} alt={player.name}>
                                {:else}
                                    {player?.name}
                                {/if}
                            </div>
                        {/each}
                        <div class="pin above"
                             in:fly={{delay: 3000 + guesses.length * 1000, duration: 300, y: -30}} out:fade
                             style="left: {((round.question.song.releaseYear ?? 0) - yearInputMin) / (yearInputMax - yearInputMin) * timelineWidth}px">{round.question.song.releaseYear}</div>
                    {/if}
                </div>
                {#if round !== null && round.question.showResult}
                    <div class="leaderboard" in:fade={{delay: 3500 + guesses.length * 1000}} out:fade>
                        {#each sortedGuesses as [id, guess], i (id)}
                            {@const player = players.find(p => p.id === id)}
                            <div class="row">
                                <div class="rank">#{i + 1}</div>
                                <div class="player">
                                    {#if player?.verified}
                                        <img src={player?.avatar} alt="Profile">
                                    {/if}
                                    <span>{player?.name}</span>
                                    {#if player?.verified}
                                        &#x2714;
                                    {/if}
                                </div>
                                <div class="score">{guess.points}</div>
                            </div>
                        {/each}
                    </div>
                {/if}
                <div class="actions">
                    {#if canMakeGuess}
                        <button onclick={guess} out:fade={{duration: 300}}>{guessLocked ? 'Edit' : 'Guess'}</button>
                    {:else if !round.question.showResult && !isOperator}
                        <button out:fade={{duration: 300}} disabled>Spectating</button>
                    {/if}
                    {#if isOperator}
                        <button onclick={() => socket.send("next")}>Next</button>
                    {/if}
                </div>
            {:else}
                <div class="leaderboard" in:fade>
                    {#each sortedResults as [id, points], i (id)}
                        {@const player = players.find(p => p.id === id)}
                        <div class="row">
                            <div class="rank">#{i + 1}</div>
                            <div class="player">
                                {#if player?.verified}
                                    <img src={player?.avatar} alt="Profile">
                                {/if}
                                <span>{player?.name}</span>
                                {#if player?.verified}
                                    &#x2714;
                                {/if}
                            </div>
                            <div class="score">{points}</div>
                        </div>
                    {/each}
                </div>
                <div class="actions">
                    {#if isOperator}
                        <button onclick={() => socket.send("finish")}>Finish</button>
                    {/if}
                </div>
            {/if}
        </div>
    {/if}
    {#if popup !== null}
        <div class="overlay" in:fade={{duration: 200}} out:fade={{delay: 300, duration: 200}}>
            <div class="popup" in:fly={{delay: 200, duration: 300, y: 200}} out:fly={{duration: 300, y: 200}}>
                <h3>{popup.message}</h3>
                {#if popup.message === 'Select a username:'}
                    <input class="username" class:error={usernameInputErrors.length !== 0} type="text" name="username"
                           id="usernameInput" placeholder="Username" size="20" maxlength="20"
                           bind:value={usernameInputValue}
                           oninput={() => socket.send('checkName', usernameInputValue)}>
                    <div>
                        {#each usernameInputErrors as error (error)}
                            <p class="error" transition:slide>{@html error}</p>
                        {/each}
                        <!-- suppress css warning from samp styling -->
                        <span class="error" style="display: none"><samp></samp></span>
                    </div>
                    <span class="login">Or <a
                            href={`/login?redirectUrl=${encodeURIComponent(location.pathname)}`}>Login</a></span>
                {/if}
                <button onclick={() => popup?.buttonAction()}
                        disabled={popup?.buttonDisabled}>{popup.buttonText}</button>
            </div>
        </div>
    {/if}
</section>
<style>
    section {
        display: grid;

        .lobby {
            grid-column: 1;
            grid-row: 1;
            display: flex;
            flex-direction: column;
            gap: 3rem;

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

                    .player {
                        filter: brightness(50%);

                        &.connected {
                            filter: none;
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

            button {
                align-self: end;
            }
        }

        .round {
            grid-column: 1;
            grid-row: 1;
            display: flex;
            flex-direction: column;
            gap: 3rem;

            .title {
                display: grid;
                grid-template-columns: auto 1fr;
                width: 100%;
                align-items: baseline;
                gap: 1rem;
                border-bottom: var(--border);

                h3 {
                    grid-column: 2;
                    grid-row: 1;
                    color: var(--primary);
                    font-weight: 800;
                    text-transform: uppercase;
                }
            }

            .song {
                align-self: center;
                display: flex;
                flex-direction: column;
                width: 50rem;
                height: 2rem;
                background-color: var(--secondary);
                border: var(--border);
                border-radius: 1rem;
                overflow: hidden;
                transition: width 500ms ease-in-out, height 500ms ease-in-out;

                .info {
                    display: flex;
                    height: calc(100% - 2rem);
                    align-items: center;

                    img {
                        width: 8rem;
                        height: 8rem;
                        margin: 0 1rem;
                        border: var(--border);
                        border-radius: 0.4rem;
                    }

                    .details {
                        display: flex;
                        flex-direction: column;
                        width: 100%;
                        padding: 0 1.5rem;
                        justify-content: center;

                        h4 {
                            font-size: 2em;
                            font-weight: bold;
                        }

                        h5 {
                            color: var(--muted);
                            font-size: 1.5em;
                            font-weight: 600;
                        }
                    }
                }

                audio {
                    width: 100%;
                    height: 2rem;
                    background-color: hsl(0, 0%, 25%);
                    filter: grayscale(1);

                    /*noinspection CssInvalidPseudoSelector*/

                    &::-webkit-media-controls-panel {
                        background-color: hsl(0, 0%, 87%);
                        filter: invert(1);
                    }
                }

                &.expanded {
                    height: 12rem;

                    audio {
                        border-top: var(--border);
                    }
                }
            }

            .timeline {
                position: relative;
                display: flex;
                margin: 3.5rem 0 4.5rem 0;
                justify-content: space-between;
                border-bottom: var(--border);

                .bar {
                    position: relative;
                    width: 0;

                    &:nth-child(odd)::before {
                        content: attr(data-year);
                        position: absolute;
                        display: block;
                        bottom: 1.4rem;
                        padding: 0.4rem;
                        transform: translateX(-50%);
                    }

                    &::after {
                        content: '';
                        position: absolute;
                        display: block;
                        bottom: 0;
                        left: 0;
                        width: var(--decoration-thickness);
                        height: 1rem;
                        background-color: var(--decoration);
                    }

                    &:nth-child(odd)::after {
                        height: 1.4rem;
                    }
                }

                input {
                    position: absolute;
                    top: 1rem;
                    left: -2.5rem;
                    right: -2.5rem;
                    margin: 0;
                    width: auto;
                    height: 3.5rem;
                    border: none;
                    opacity: 0;
                    z-index: 9;

                    &::-webkit-slider-thumb {
                        width: 5rem;
                        height: 100%;
                        border: none;
                        border-radius: 1rem;
                    }

                    &::-moz-range-thumb {
                        width: 5rem;
                        height: 100%;
                        border: none;
                        border-radius: 1rem;
                    }

                    &:disabled {
                        cursor: not-allowed;
                    }
                }

                .pin {
                    position: absolute;
                    display: flex;
                    top: 1rem;
                    min-width: 3.5rem;
                    padding: 0.5rem;
                    justify-content: center;
                    align-items: center;
                    font-size: 1.4rem;
                    font-weight: bold;
                    background-color: var(--secondary);
                    border: var(--border);
                    border-radius: 1rem;
                    transform: translateX(-50%);

                    img {
                        height: 3rem;
                        border-radius: 50%;
                    }

                    &::before {
                        content: '';
                        position: absolute;
                        bottom: 100%;
                        left: calc(50% - 1rem);
                        right: calc(50% - 1rem);
                        height: 2rem;
                        border-bottom: 1rem solid var(--decoration);
                        border-left: 1rem solid transparent;
                        border-right: 1rem solid transparent;
                    }

                    &::after {
                        content: '';
                        position: absolute;
                        bottom: 100%;
                        --thickness: calc(var(--decoration-thickness) * sqrt(2));
                        left: calc(50% - 1rem + var(--thickness));
                        right: calc(50% - 1rem + var(--thickness));
                        height: calc(2rem - 2 * var(--thickness));
                        border-bottom: calc(1rem - var(--thickness)) solid var(--secondary);
                        border-left: calc(1rem - var(--thickness)) solid transparent;
                        border-right: calc(1rem - var(--thickness)) solid transparent;
                    }

                    &.above {
                        top: auto;
                        bottom: 1rem;

                        &::before {
                            bottom: auto;
                            top: 100%;
                            border-bottom: none;
                            border-top: 1rem solid var(--decoration);
                        }

                        &::after {
                            bottom: auto;
                            top: 100%;
                            border-bottom: none;
                            border-top: calc(1rem - var(--thickness)) solid var(--secondary);
                        }
                    }
                }

                .interactive-pin {
                    width: 5rem;
                    height: 3.5rem;
                    transition: 50ms linear all;

                    &::after {
                        transition: 50ms linear all;
                    }

                    input:hover + &,
                    input:focus-visible + & {
                        background-color: var(--primary);

                        &::after {
                            border-bottom-color: var(--primary);
                        }
                    }

                    input:active + & {
                        background-color: var(--accent);

                        &::after {
                            border-bottom-color: var(--accent);
                        }
                    }

                    input:disabled + & {
                        color: var(--muted);
                        background-color: var(--background);
                        cursor: not-allowed;

                        &::after {
                            border-bottom-color: var(--background);
                        }
                    }
                }
            }

            .actions {
                align-self: end;
                display: flex;

                button {
                    font-size: 1.4em;
                    font-weight: bold;
                }
            }
        }

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

                    img {
                        height: 2em;
                        border-radius: 50%;
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

                button {
                    align-self: end;
                }
            }
        }
    }

    :global(body):has(.overlay) {
        overflow: hidden;
    }
</style>

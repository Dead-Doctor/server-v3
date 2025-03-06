<script lang="ts">
    import { connectGameChannel } from '../game.svelte';
    import { getData } from '../../routing';
    import { isOperator, playerById, you, type PlayerId } from '../../lobby.svelte';
    import Popup from '../../Popup.svelte';
    import { sineInOut } from 'svelte/easing';
    import { fade, fly } from 'svelte/transition';
    import Leaderboard from '../../Leaderboard.svelte';
    import Timeline from './Timeline.svelte';
    import Pin from './Pin.svelte';
    import PlayerIcon from '../../lobby/PlayerIcon.svelte';
    import { bcs } from '../../bcs';

    interface Round {
        players: PlayerId[]
        questions: Question[]
        results: Map<string, number> | null
    }

    interface Question {
        song: {
            previewUrl: string
            trackName: string | null
            artistName: string | null
            artworkUrl: string | null
            releaseYear: number | null
        }
        showResult: boolean
        guesses: Map<PlayerId, Guess> | null
    }

    interface Guess {
        year: number
        points: number
    }
    
    let round: Round = $state(getData('round'))

    let currentQuestion = $derived(
        round.questions.length == 0
            ? null
            : round.questions[round.questions.length - 1]
    )
    let guesses = $derived(Array.from(currentQuestion?.guesses ?? new Map<PlayerId, Guess>()))

    let sortedGuesses = $derived(
        guesses.toSorted((a, b) => b[1].points - a[1].points).map(([id, guess]) =>
            Object.assign({ value: guess.points }, playerById(id))
        )
    )
    let resultsLeaderBoard = $derived(
        Array.from(round.results ?? new Map<PlayerId, number>())
        .toSorted((a, b) => b[1] - a[1]).map(([id, points]) =>
            Object.assign({ value: points }, playerById(id))
        )
    )
    
    let popup = $state({
        visible: false,
        message: '',
        buttonText: '',
        buttonAction() {}
    })

    let minHeight = $state(0)

    const yearMin = 1950
    const yearMax = 2020
    let yearInputValue = $state((yearMin + yearMax) / 2)

    let canMakeGuess = $derived(currentQuestion !== null && round.players.includes(you.id) && !currentQuestion?.showResult)
    let guessLocked = $state(false)

    let overriding = $state(false)

    const channel = connectGameChannel()
    const sendGuess = channel.destinationWith(bcs.nullable(bcs.int))
    const sendOverride = channel.destinationWith(bcs.int)
    const sendNext = channel.destination()
    const sendFinish = channel.destination()
    const bcsSong = bcs.struct({
        previewUrl: bcs.string,
        trackName: bcs.nullable(bcs.string),
        artistName: bcs.nullable(bcs.string),
        artworkUrl: bcs.nullable(bcs.string),
        releaseYear: bcs.nullable(bcs.int)
    })
    const bcsGuess = bcs.struct({
        year: bcs.int,
        points: bcs.int
    })
    const bcsQuestion = bcs.struct({
        song: bcsSong,
        showResult: bcs.boolean,
        guesses: bcs.nullable(bcs.map(bcs.string, bcsGuess))
    })
    const bcsRound = bcs.struct({
        players: bcs.list(bcs.string),
        questions: bcs.list(bcsQuestion),
        results: bcs.nullable(bcs.map(bcs.string, bcs.int))
    })
    channel.receiverWith(onRound, bcsRound)

    function onRound(update: Round) {
        if (update.questions.length > round.questions.length) {
            guessLocked = false
            overriding = false
        }
        round = update
    }

    channel.disconnection(e => {
        if (e.code === 1001) return;
        popup.visible = true
        popup.message = 'Lost connection'
        popup.buttonText = 'Reload'
        popup.buttonAction = () => {
            location.reload()
        }
    })
    
    const guess = () => {
        guessLocked = !guessLocked;
        sendGuess(guessLocked ? yearInputValue : null)
    }

    const override = () => {
        if (overriding) sendOverride(yearInputValue)
        overriding = !overriding;
    }

    const next = () => {
        if (currentQuestion?.showResult) {
            sendNext()
            return
        }
        popup.visible = true
        popup.message = 'Confirm skip?'
        popup.buttonText = 'Yes'
        popup.buttonAction = () => {
            sendNext()
            popup.visible = false
        }
    }

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
    <div class="title">
        <h2>Music Guesser</h2>
        <!-- Subtitle -->
        {#key [round.results == null, !currentQuestion?.showResult]}
            <h3 in:fly={{duration: 300, y: -30}}
                out:fly={{duration: 300, y: 30}}>{ round.results == null ? !currentQuestion?.showResult ? 'Guess' : 'Result' : 'Results' }</h3>
        {/key}
    </div>
    <!-- Content -->
    {#key round.results}
        <div class="content" in:fly={{duration: 500, x: 300}} out:fly={{duration: 500, x: -300}}>
            {#if round.results == null}
            <!-- Question + Result -->
                <div class="song" class:expanded={currentQuestion?.showResult}>
                    <div class="info">
                        {#if !currentQuestion?.showResult}
                            <div style="display: none" use:volumeFading></div>
                        {:else}
                            <img src={currentQuestion?.song.artworkUrl} alt="Album Cover"
                                    in:fade={{delay: 1000, duration: 200}}>
                            <div class="details">
                                <h4 in:fade={{delay: 1500, duration: 200}}>{currentQuestion?.song.trackName}</h4>
                                <h5 in:fade={{delay: 2000, duration: 200}}>{currentQuestion?.song.artistName}</h5>
                            </div>
                        {/if}
                    </div>
                    <audio src={currentQuestion?.song.previewUrl} autoplay bind:volume
                            onvolumechange={saveVolume}
                            controls></audio>
                </div>
                <Timeline {yearMin} {yearMax}>
                    {#if canMakeGuess || overriding}
                        <Pin bind:year={yearInputValue} interactive disabled={!overriding && guessLocked}>{yearInputValue}</Pin>
                    {/if}
                    {#if currentQuestion?.showResult}
                        {#each guesses as [id, guess], i (id)}
                            {@const player = playerById(id)!}
                            <Pin year={guess.year} delay={3000 + i * 1000}>
                                <PlayerIcon {player} size={'3rem'}/>
                            </Pin>
                        {/each}
                        {@const year = currentQuestion?.song.releaseYear ?? 0}
                        <Pin {year} delay={3000 + guesses.length * 1000} above>{year}</Pin>
                    {/if}
                </Timeline>
                <div>
                    {#if currentQuestion?.showResult}
                        <Leaderboard players={sortedGuesses}/>
                    {/if}
                </div>
                <div class="actions">
                    {#if canMakeGuess}
                        <button onclick={guess}>{guessLocked ? 'Edit' : 'Guess'}</button>
                    {:else if !currentQuestion?.showResult && !isOperator()}
                        <button disabled>Spectating</button>
                    {/if}
                    {#if isOperator()}
                        {#if currentQuestion?.showResult}
                            <button onclick={override}>{overriding ? 'Save' : 'Override'}</button>
                        {/if}
                        <button onclick={next}>Next</button>
                    {/if}
                </div>
            {:else}
            <!-- Final Results -->
                <Timeline {yearMin} {yearMax}>
                    {#each round.questions as question, i}
                        <Pin
                            year={question.song.releaseYear ?? 0}
                            delay={500 + i * 500}
                        >{i + 1}</Pin>
                    {/each}
                </Timeline>
                <Leaderboard players={resultsLeaderBoard} transition={{
                    delay: i => 1000 + (round?.questions.length ?? 0) * 500 + (resultsLeaderBoard.length - 1 - i) * 500,
                    duration: () => 500
                }}/>
                <div class="actions">
                    {#if isOperator()}
                        <button onclick={() => sendFinish()}>Finish</button>
                    {/if}
                </div>
            {/if}
        </div>
    {/key}
    <Popup
        bind:visible={popup.visible}
        message={popup.message}
        closable
        buttonText={popup.buttonText}
        buttonAction={popup.buttonAction}
    />
</section>

<style>
    section {
        display: grid;
        grid-template-rows: repeat(2, auto) 1fr;
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

        .content {
            grid-column: 1;
            grid-row: 2;
            display: flex;
            flex-direction: column;
            gap: 3rem;

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

            .actions {
                align-self: end;
                display: flex;

                button {
                    width: 12rem;
                    font-size: 1.4em;
                    font-weight: bold;
                }
            }
        }
    }
</style>
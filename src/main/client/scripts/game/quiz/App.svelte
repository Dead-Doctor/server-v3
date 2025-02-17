<script lang="ts">
    import { bcs } from "@iota/bcs";
    import { getData } from "../../routing";
    import { connectGameChannel } from "../game.svelte";
    import PlayerIcon from "../../lobby/PlayerIcon.svelte";
    import { isOperator, playerById } from "../../lobby.svelte";

    interface Question {
        text: string
        answers: Answer[]
        tags: string[]
    }

    interface Answer {
        text: string
        correct: boolean
    }

    let question: Question = getData('question')
    let results: string[][] | null = $state(getData('results'))

    let guess: number | null = $state(null)
    let guessLocked: boolean = $state(false)

    let showResults = $derived(results != null)

    const channel = connectGameChannel()
    const sendGuess = channel.destinationWith(bcs.option(bcs.u32()))
    const sendFinish = channel.destination()
    channel.receiverWith(onResults, bcs.vector(bcs.vector(bcs.string())))

    function confirm() {
        if (!guessLocked) {
            sendGuess(guess)
            guessLocked = true
        } else {
            sendGuess(null)
            guessLocked = false
        }
    }

    function onResults(data: Iterable<Iterable<string>> | null) {
        results = data as string[][]
    }
</script>

<section>
    <h1> The great <b>Quiz</b> Game </h1>
</section>
<section class="content">
    <h4 class="question">{question.text}</h4>
    <div class="answers">
        {#each question.answers as answer, i}
            <button
                    class="answer"
                    class:selected={i === guess}
                    class:correct={showResults && answer.correct}
                    class:incorrect={showResults && !answer.correct && i === guess}
                    disabled={guessLocked || showResults}
                    onclick={() => guess = i}
            >
                {answer.text}
                {#if showResults}
                    {#each results![i] as id}
                        <PlayerIcon player={playerById(id)!} size={'1.3rem'}/>
                    {/each}
                {/if}
            </button>
        {/each}
    </div>
    {#if !showResults}
        <button class="action" onclick={confirm}>
            {guessLocked ? 'Edit' : 'Guess'}
        </button>
    {:else}
        <button class="action" disabled={!isOperator()} onclick={sendFinish}>Finish</button>
    {/if}
</section>

<style>
    .question {
        border-radius: 50px;
        text-align: center;
        padding: 10px 50px;
        border: 2px solid #244242;
    }

    .answers {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1%;
    }

    .answer {
        display: flex;
        justify-content: center;
        align-items: center;
        gap: 0.7rem;
        font-size: 1.3rem;
        border: 2px solid #244242
    }

    .selected {
        background-color: gray;
    }

    .correct {
        background-color: green;
    }

    .incorrect {
        background-color: red;
    }

    .action {
        width: 20%;
        align-self: end;
    }

    .content {
        display: flex;
        flex-direction: column;
        gap: 15px;
    }

    @media (max-width: 700px) {
        .answers {
            grid-template-columns: 1fr;
        }

        .action {
            width: 50%;
        }
    }
</style>
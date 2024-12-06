<script lang="ts">
    import {getData} from "../routing";

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
    let guess = $state(-1)
    let showResults = $state(false)
</script>

<section>
    <h1 class="ueberschrift"> The great <b>Quiz</b> Game </h1>
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
                    disabled={showResults}
                    onclick={() => guess = i}
            >{answer.text}</button>
        {/each}
    </div>
    {#if !showResults}
        <button class="guess" onclick={() => showResults = true}>Guess</button>
    {/if}
</section>

<style>
    .answers {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1%;
    }

    .question {
        border-radius: 50px;
        text-align: center;
        padding: 10px 50px;
        border: 2px solid #244242;
    }

    .answer {
        border: 2px solid #244242;
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

    .guess {
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

        .guess {
            width: 50%;
        }
    }
</style>
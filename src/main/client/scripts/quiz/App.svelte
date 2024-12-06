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
</script>

<section>
    <h1 class="ueberschrift"> The great <b>Quiz</b> Game </h1>
</section>
<section class="content">
    <h4 class="question">{question.text}</h4>
    <div class="answers">
        {#each question.answers as answer}
            <button class="answer" class:correct={answer.correct}>{answer.text}</button>
        {/each}
    </div>
    <button class="guess">Guess</button>
</section>

<style>
    .answers {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 1%;
    }

    .question {
        border-radius: 500px;
        text-align: center;
        padding-top: 10px;
        padding-bottom: 10px;
        padding-left: 50px;
        padding-right: 50px;
        border: 2px solid #244242;
    }

    .answer {
        border: 2px solid #244242;
    }

    .correct {
        background-color: green;
    }

    .incorrect {
        background-color: red;
    }

    .selected {
        background-color: gray;
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
<script lang="ts">
    import Icon from "../../Icon.svelte";
import Map from "./Map.svelte";

    let gameContainer: HTMLElement
    let isFullscreen = $state(false)

    const toggleFullscreen = () => {
        if (!isFullscreen) {
            gameContainer.requestFullscreen();
        } else if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    }
</script>

<section bind:this={gameContainer} onfullscreenchange={() => isFullscreen = document.fullscreenElement != null}>
    <div class="map">
        <Map></Map>
    </div>
    <div class="actions">
        <button>XYZ</button>
        <div class="spacing"></div>
        <button onclick={toggleFullscreen}><Icon id={!isFullscreen ? 'fullscreen' : 'fullscreen-exit'}/></button>
    </div>
</section>

<style>
    section {
        display: flex;
        flex-direction: column;
        height: 80vh;
        background-color: var(--secondary);
        border: var(--border);
        border-radius: 1rem;
        overflow: hidden;

        &:fullscreen {
            border: none;
            border-radius: 0;
        }
    }

    .map {
        flex-grow: 1;
    }

    .actions {
        display: flex;
        height: 3rem;

        :not(:first-child) {
                border-left: var(--border);
        }

        .spacing {
            flex-grow: 1;
        }

        button {
            margin: 0;
            padding: 0 1rem;
            border: none;
            border-radius: 0;

            &:hover,
            &:focus-visible {
                background-color: var(--decoration);
            }

            &:active {
                background-color: var(--accent);
            }
        }
    }
</style>
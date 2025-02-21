<script lang="ts">
    import Icon from "../../Icon.svelte";
import Map from "./Map.svelte";

    let gameContainer: HTMLElement

    const toggleFullscreen = () => {
        if (!document.fullscreenElement) {
            gameContainer.requestFullscreen();
        } else if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    }
</script>

<section bind:this={gameContainer}>
    <Map></Map>
    <button onclick={toggleFullscreen}><Icon id='fullscreen'/></button>
    <button onclick={toggleFullscreen}><Icon id='fullscreen-exit'/></button>
</section>

<style>

</style>
<script lang="ts">
    interface Props {
        isFullscreen: boolean
        children: any
    }

    let { isFullscreen = $bindable(), children }: Props = $props()

    let container: HTMLElement

    $effect(() => {
        if (!isFullscreen) {
            container.requestFullscreen();
        } else if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    })
</script>

<section bind:this={container} onfullscreenchange={() => isFullscreen = document.fullscreenElement != null}>
    {@render children?.()}
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
</style>
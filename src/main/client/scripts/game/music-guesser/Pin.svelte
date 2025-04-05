<script lang="ts">
    import { getContext, type Snippet } from "svelte";
    import { fade, fly } from "svelte/transition";

    interface Props {
        year: number,
        delay?: number
        above?: boolean
        interactive?: boolean
        disabled?: boolean
        children: Snippet
    }

    let { year = $bindable(), delay = 0, above = false, interactive = false, disabled = false, children }: Props = $props()

    const yearMin: number = getContext('yearMin')
    const yearMax: number = getContext('yearMax')
    const yearRange: number = getContext('yearRange')
    let timelineSize: { width: number } = getContext('timelineWidth')

    let ratio = $derived((year - yearMin) / yearRange)
    let left = $derived(ratio * timelineSize.width)
</script>

{#if interactive}
    <input type="range" name="year" id="yearInput" min={yearMin} max={yearMax}
    bind:value={year} disabled={disabled}
    transition:fly={{duration: 300, y: 200}}>
{/if}
<div class="pin" class:above={above} class:interactive={interactive}
    in:fly|global={{delay, duration: 300, y: 30}} out:fade
    style="left: {left}px">
    {@render children?.()}
</div>

<style>
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

    .interactive {
        width: 5rem;
        height: 3.5rem;
        transition: 50ms linear all;
        z-index: 8;

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
</style>
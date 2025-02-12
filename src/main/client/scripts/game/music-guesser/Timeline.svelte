<script lang="ts">
    import { setContext } from "svelte";


    interface Props {
        yearMin: number
        yearMax: number
        yearStep?: number
        children: any
    }

    let { yearMin, yearMax, yearStep = 5, children }: Props = $props()

    const yearRange = yearMax - yearMin
    const barCount = yearRange / yearStep + 1
    let timelineSize = $state({ width: 0 })

    setContext('yearMin', yearMin)
    setContext('yearMax', yearMax)
    setContext('yearRange', yearRange)
    setContext('timelineWidth', timelineSize)
</script>

<div class="timeline" bind:clientWidth={timelineSize.width}>
    {#each {length: barCount} as _, i}
        <div class="bar" data-year={yearMin + i * yearStep}></div>
    {/each}
    {@render children?.()}
</div>

<style>
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
    }
</style>

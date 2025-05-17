<script lang="ts">
    import { getContext } from 'svelte';
    import type { MapContext } from './Map.svelte';
    import type { Point } from '../scotland-yard';

    interface Props {
        id: string;
        position: Point;
        radius: number;
        bus: boolean;
        tram: boolean;
        selected?: boolean;
        marker?: string;
        onclick?: L.LeafletMouseEventHandlerFn | null;
        cursor?: string;
    }

    const halfSize = 30;

    let {
        id,
        position,
        radius,
        bus,
        tram,
        selected = false,
        marker = '',
        onclick = null,
        cursor = onclick !== null ? 'pointer' : 'grab',
    }: Props = $props();
    let ctx: MapContext = getContext('map');
    let info = ctx();

    let targetId = $derived(`i${id}`);

    let scale = $derived(radius / halfSize);
    let markerStroke = $derived(1 / Math.pow(2, info.zoom.level * 0.5));
    let markerScale = $derived(0.8 * (1 - info.zoom.ratio))

    let { x, y } = $derived(info.projectPoint(position));

    info.featureEventHandlers.push((target, e) => {
        if (target !== targetId) return;
        onclick?.(e);
    });
</script>

<g
    data-target={targetId}
    class:target={onclick !== null}
    class:bus
    class:tram
    class:selected
    style="cursor: {cursor};"
    transform="translate({x} {y}) scale({scale})"
>
    <path class="half" d="M -30  0 A 30 30 0 0 1 30 0" stroke="black" stroke-width="3" />
    <path class="half bottom" d="M -30 0 A 30 30 0 0 0 30 0" stroke="black" stroke-width="3" />
    <path d="M -30 0 H 30" stroke="black" stroke-width="3" />
    <rect class="box" x="-20" y="-15" width="40" height="30" stroke="black" stroke-width="3" fill="white" />
    <text
        class="number"
        x="0"
        y="-1"
        font-size="23"
        letter-spacing="-1"
    >
        {id}
    </text>

    {#if marker != ''}
    <!-- transform="scale({markerScale})" -->
        <circle cx="0" cy="0" r="35" fill="none" stroke={marker} stroke-width={10 * markerStroke} transform="scale({1 + markerScale})" />
    {/if}
</g>

<style>
    @import url('https://fonts.googleapis.com/css2?family=Oswald:wght@200..700&display=swap');

    g {
        .half {
            fill: var(--taxi-color);
        }

        &.bus .bottom {
            fill: var(--bus-color);
        }

        &.tram {
            .box {
                fill: var(--tram-color);
            }

            .number {
                fill: white;
            }
        }


        .number {
            text-anchor: middle;
            dominant-baseline: central;
            font-family: 'Oswald', 'Arial Narrow', Arial, sans-serif;
            font-weight: 500;
            fill: black;
        }

        &:hover {
            fill: red;
        }

        &.selected {
            path,
            rect {
                stroke: #002da8;
            }

            text {
                fill: #002da8 !important;
            }
        }

        circle {
            transition: all 250ms cubic-bezier(0, 0, 0.25, 1);
        }
    }
</style>

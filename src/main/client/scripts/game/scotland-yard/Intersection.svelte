<script lang="ts">
    import { getContext } from 'svelte';
    import type { MapContext } from './Map.svelte';

    interface Props {
        id: number;
        lat: number;
        lon: number;
        radius: number;
        bus: boolean;
        tram: boolean;
        selected: boolean;
        onclick?: L.LeafletMouseEventHandlerFn | null
    }

    const halfSize = 30;

    let { id, lat, lon, radius, bus, tram, selected, onclick = null }: Props = $props();
    let ctx: MapContext = getContext('map');

    let targetId = $derived(`i${id}`);

    let scale = $derived(radius / halfSize);
    
    let x = $derived(((lon - ctx.boundary.getWest()) / (ctx.boundary.getEast() - ctx.boundary.getWest())) * ctx.width);
    let y = $derived(
        ((ctx.boundary.getNorth() - lat) / (ctx.boundary.getNorth() - ctx.boundary.getSouth())) * ctx.height
    );

    ctx.featureEventHandlers.push((target, e) => {
        if (target !== targetId) return;
        onclick?.(e)
    });
</script>

<g
    data-target={targetId}
    class="intersection target"
    class:bus
    class:tram
    class:selected
    transform="translate({x} {y}) scale({scale}) translate(-{halfSize} -{halfSize})"
>
    <path class="half" d="M 0 30 A 30 30 0 0 1 60 30" stroke="black" stroke-width="3" />
    <path class="half bottom" d="M 0 30 A 30 30 0 0 0 60 30" stroke="black" stroke-width="3" />
    <path d="M 0 30 H 60" stroke="black" stroke-width="3" />
    <rect class="box" x="10" y="15" width="40" height="30" stroke="black" stroke-width="3" fill="white" />
    <text
        class="number"
        x="30"
        y="30"
        text-anchor="middle"
        dominant-baseline="central"
        font-family="Arial Narrow"
        font-weight="700"
        font-size="23"
        fill="black"
    >
        {id}
    </text>
</g>

<style>
    :root {
        --taxi-color: #ffd000;
        --bus-color: #008e59;
        --tram-color: #ff3900;
    }

    .intersection {
        cursor: pointer;

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
    }
</style>

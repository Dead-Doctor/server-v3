<script lang="ts">
    import { getContext } from 'svelte';
    import type { MapContext } from './Map.svelte';
    import type { Point } from './scotland-yard';

    interface Props {
        id: string;
        position: Point;
        radius: number;
        bus: boolean;
        tram: boolean;
        selected?: boolean;
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
        onclick = null,
        cursor = onclick !== null ? 'pointer' : 'grab',
    }: Props = $props();
    let ctx: MapContext = getContext('map');
    let info = ctx()

    let targetId = $derived(`i${id}`);

    let scale = $derived(radius / halfSize);

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

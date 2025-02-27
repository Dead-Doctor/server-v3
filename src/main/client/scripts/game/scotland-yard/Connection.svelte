<script lang="ts">
    import { getContext } from 'svelte';
    import type { MapContext } from './Map.svelte';
    import { Points, transport, type Point, type Shape, type Transport } from './scotland-yard';

    interface Props {
        id: number;
        from: Point;
        to: Point;
        width: number;
        type: Transport;
        shape: Shape
        selected?: boolean;
        onclick?: L.LeafletMouseEventHandlerFn | null
    }

    let { id, from, to, width, type, shape, selected = false, onclick = null }: Props = $props();
    let ctx: MapContext = getContext('map');

    let targetId = $derived(`c${id}`)

    let start = $derived(ctx.projectPoint(from))
    let end = $derived(ctx.projectPoint(to))
    let controlStart = $derived(ctx.projectPoint(Points.add(from, shape.from)))
    let controlEndInverse = $derived(ctx.projectPoint(Points.add(to, shape.to)))

    ctx.featureEventHandlers.push((target, e) => {
        if (target !== targetId) return;
        onclick?.(e)
    });
</script>

<path
    class:target={onclick !== null}
    class:taxi={type === transport.TAXI}
    class:bus={type === transport.BUS}
    class:tram={type === transport.TRAM}
    class:train={type === transport.TRAIN}
    d="M {start.x} {start.y} C {controlStart.x} {controlStart.y} {controlEndInverse.x} {controlEndInverse.y} {end.x} {end.y}"
    fill="none"
    stroke-width={width}
/>

<style>
    .taxi {
        stroke: var(--taxi-color);
    }

    .bus {
        stroke: var(--bus-color);
    }

    .tram {
        stroke: var(--tram-color);
    }

    .train {
        stroke: var(--train-color);
    }
</style>
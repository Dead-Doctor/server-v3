<script lang="ts">
    import { getContext } from 'svelte';
    import type { MapContext } from './Map.svelte';
    import { Points, transport, type Point, type Shape, type Transport } from './scotland-yard';

    interface Props {
        id: string;
        from: Point;
        to: Point;
        width: number;
        type: Transport;
        shape: Shape;
        disabled?: boolean;
        selected?: boolean;
        onclick?: L.LeafletMouseEventHandlerFn | null;
        cursor?: string;
    }

    let {
        id,
        from,
        to,
        width,
        type,
        shape,
        disabled = false,
        selected = false,
        onclick = null,
        cursor = onclick !== null ? 'pointer' : 'grab',
    }: Props = $props();
    let ctx: MapContext = getContext('map');
    let info = ctx()

    let targetId = $derived(`c${id}`);

    let start = $derived(info.projectPoint(from));
    let end = $derived(info.projectPoint(to));
    let controlStart = $derived(info.projectPoint(Points.add(from, shape.from)));
    let controlEndInverse = $derived(info.projectPoint(Points.add(to, shape.to)));

    info.featureEventHandlers.push((target, e) => {
        if (target !== targetId) return;
        onclick?.(e);
    });
</script>

<path
    class:target={!disabled && onclick !== null}
    class:taxi={type === transport.TAXI}
    class:bus={type === transport.BUS}
    class:tram={type === transport.TRAM}
    class:train={type === transport.TRAIN}
    class:disabled
    style="cursor: {cursor};"
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

    .disabled {
        stroke: var(--background);
    }
</style>

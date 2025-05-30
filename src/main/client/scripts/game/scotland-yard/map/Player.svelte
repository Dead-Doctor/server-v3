<script lang="ts">
    import { getContext } from 'svelte';
    import type { MapContext } from './Map.svelte';
    import { role, type Role, type Point } from '../scotland-yard';

    interface Props {
        role: Role;
        position: Point;
        size: number;
        /**
         * Controls how strong zoomin in/out effect this elements size.
         * - `0` means the element behaves completely static (in comparison to the map)
         * - `1` means the element keeps the same size regardless of scale level
         */
        scaleCompensationStrength: number;
        offset?: number;
    }

    const halfSize = 32;

    let { role: r, position, size, scaleCompensationStrength, offset = 0 }: Props = $props();
    let ctx: MapContext = getContext('map');
    let info = ctx();

    let factor = $derived(1 / Math.pow(2, info.zoom.level * scaleCompensationStrength))
    let scale = $derived(size / (halfSize * 2) * factor);
    let { x, y } = $derived(info.projectPoint(position));
</script>

<g
    width="64"
    height="64"
    version="1.1"
    viewBox="0 0 64 64"
    xmlns="http://www.w3.org/2000/svg"
    transform="translate({x} {y}) scale({scale}) translate({halfSize * (-1 + offset)} {halfSize * -1.7})"
>
    <path
        class={r}
        d="m42.969 18.497c1.5318 2.4549 1.8149 3.0728 3.559 4.5351 0 0 1.2965-0.37744 1.841 0.7685 0.54447 1.1459-0.43982 2.0495-1.2635 2.0841-0.82373 0.03462-1.1869-1.1141-1.1869-1.1141s-0.3156 0.58683-0.48319 0.58884c-0.16759 2e-3 -0.74227 7.98e-4 -3.7776-4.1106-1.7821 4.3551-2.9032 6.0007-5.0366 7.6939 0 0 6.371 13.122 7.4799 15.52 1.109 2.398 4.4694 16.045 4.4694 16.045s4.1891 0.05571 3.9698 1.989c-0.14041 1.4598-3.5217 0.74983-7.1708 0.98406-0.4638 0.01209-0.2636-2.2932-0.2636-2.2932s-0.40625 0.30943-0.83918 0.11291-2.7711-11.902-3.8453-13.989c-1.3151-2.555-10.25-13.273-10.25-13.273s-4.9286 12.274-6.3782 14.805c-1.4496 2.5316-10.057 11.327-10.057 11.327s4.3604 0.28995 4.1413 2.0629c-0.22832 1.5595-5.0081 1.2368-7.4344 1.1833-0.58254 9e-3 -0.18683-1.9641-0.12254-2.6565 0.0073-0.07839-0.59426-0.25682-0.41738-0.56925 1.3855-2.4474 7.6408-11.341 9.1186-12.68 2.1762-1.9729 6.2262-18.695 6.2262-18.695s-3.0397-0.32442-3.0506-0.9227c-0.01088-0.59828 1.0305-0.68035 3.4104-3.6401 2.3799-2.9598 3.4812-7.9809 3.3218-11.877 0 0-5.0903-0.82275-6.8921-0.48265-1.8018 0.3401-7.251 4.5421-7.5344 4.5297-0.28344-0.01239-0.36466-0.35312-0.36466-0.35312s-0.56753 1.1151-1.4124 1.084c-0.84486-0.0311-1.5827-0.69056-1.2731-1.7884s1.5898-0.90477 1.5898-0.90477 4.4318-4.2 8.5298-5.3265c4.098-1.1265 9.8698-0.96328 9.8698-0.96328l5.6903 0.42737c0.28009-0.12556 0.45299-0.73236 0.46443-1.4419-1.0738-0.70044-0.72905-2.3845-0.40769-3.8556 0.32136-1.4711 2.4095-3.3091 4.5572-2.5576 2.1478 0.7515 2.9789 1.6756 2.6286 4.4371-0.11468 0.45372 0.3059 1.4671 0.14696 1.6773-0.49862 0.65933-1.216-0.29842-1.125 0.28136 0.08738 0.55717-0.28767 0.4998-0.37769 0.73365-0.04378 0.11374 0.23982 0.368 0.16676 0.72111-0.07306 0.3531-0.77143 0.42168-1.5629 0.29415-0.2453 0.24598-0.40705 0.6969-0.38899 0.94932 1.6674 0.56393 2.9408 1.7515 3.0439 2.4695 0.25477 1.7743-0.41864 4.264-1.3092 6.1907z"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke="white"
    />
</g>

<style>
    g {
        transition: transform 250ms cubic-bezier(0, 0, 0.25, 1);
    }

    .misterX {
        fill: black;
    }

    .detective1 {
        fill: red;
    }

    .detective2 {
        fill: blue;
    }

    .detective3 {
        fill: green;
    }

    .detective4 {
        fill: brown;
    }

    .detective5 {
        fill: purple;
    }

    .detective6 {
        fill: orange;
    }
</style>

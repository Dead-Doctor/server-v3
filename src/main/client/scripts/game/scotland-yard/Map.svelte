<script lang="ts">
    import L from 'leaflet';
    import 'leaflet/dist/leaflet.css';
    import { setContext } from 'svelte';

    interface Props {
        minZoom: number
        boundary: L.LatLngBounds
        scale: number
    }

    let { minZoom, boundary, scale }: Props = $props()

    let ctx: { map: L.Map | undefined, width: number, height: number } = $state({ map: undefined, width: 100, height: 100 })
    setContext('map', ctx)

    let svgElement: SVGElement

    const initializeMap = (node: HTMLElement) => {
        ctx.map = L.map(node, {
            center: boundary.getCenter(),
            minZoom: minZoom,
            zoom: minZoom,
            maxBounds: boundary.pad(0.2),
        })
        L.tileLayer('https://tile.thunderforest.com/atlas/{z}/{x}/{y}.png?apikey=6a53e8b25d114a5e9216df5bf9b5e9c8', {
            maxZoom: 19,
        }).addTo(ctx.map);

        const topLeft = ctx.map.project(boundary.getNorthWest(), minZoom)
        const bottomRight = ctx.map.project(boundary.getSouthEast(), minZoom)

        const size = L.bounds(topLeft, bottomRight).getSize()

        ctx.width = scale
        ctx.height = size.y / size.x * scale

        const overlay = L.svgOverlay(svgElement, boundary, {
            interactive: true
        }).addTo(ctx.map)
        overlay.on('click', (e) => {
            const source = e.originalEvent.target as Element
            const target = source.closest('.target')
            if (target === null) return
            console.log(`Clicked: ${target}`)
        })

        return {
            destroy() {
                ctx.map?.remove();
                ctx.map = undefined;
            },
        };
    }

    const size = 0.6

    const points = [
        {x: 500, y: 500, bus: false, tram: false},
        {x: 550, y: 500, bus: true, tram: false},
        {x: 100, y: 500, bus: false, tram: true},
        {x: 300, y: 300, bus: true, tram: true},
    ]
</script>

<template>
    <svg width={ctx.width} height={ctx.height} viewBox="0 0 {ctx.width} {ctx.height}" bind:this={svgElement}>
        <rect x="0" y="0" width={ctx.width} height={ctx.height} stroke="black" stroke-width="10" fill="none" />
        {#each points as point}
            <g class="intersection target" class:bus={point.bus} class:tram={point.tram} transform="translate({point.x} {point.y}) scale({size}) translate(-40 -40)" >
                <path d="M 10 40 A 30 30 0 0 1 70 40" stroke="black" stroke-width="3" />
                <path d="M 10 40 A 30 30 0 0 0 70 40" stroke="black" stroke-width="3" />
                <path d="M 10 40 H 70" stroke="black" stroke-width="3" />
                <rect x="20" y="25" width="40" height="30" stroke="black" stroke-width="3" fill="white" />
                <text
                    class="number"
                    x="40"
                    y="40"
                    text-anchor="middle"
                    dominant-baseline="central"
                    font-family="Arial Narrow"
                    font-weight="700"
                    font-size="23"
                    fill="black"
                >
                    142
                </text>
            </g>
        {/each}
    </svg>
</template>
<div class="map" use:initializeMap onresize={() => ctx.map?.invalidateSize()}></div>

<style>
    :root {
        --taxi-color: #ffd000;
        --bus-color: #008e59;
        --tram-color: #ff3900;
    }

    .map {
        height: 100%;
    }

    svg {
        cursor: grab;

        .target {
            cursor: pointer;

            &:hover {
                fill: red;
            }
        }
    }

    .intersection > path,
    .intersection.bus > path + path {
        fill: var(--taxi-color);
    }

    .intersection.bus > path + path {
        fill: var(--bus-color);
    }

    .intersection.tram > * + rect {
        fill: var(--tram-color);
    }

    .intersection.tram > * + text {
        fill: white;
    }

    .selected path,
    .selected rect {
        stroke: #002da8;
    }

    .selected text {
        fill: #002da8 !important;
    }
</style>
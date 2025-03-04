<script lang="ts">
    import L from 'leaflet';
    import 'leaflet/dist/leaflet.css';
    import { setContext } from 'svelte';
    import type { Point, Shape } from './scotland-yard';

    export interface MapContext {
        map: L.Map | undefined;
        boundary: L.LatLngBounds;
        width: number;
        height: number;
        projectPoint(point: Point): { x: number; y: number };
        featureEventHandlers: ((target: string, event: L.LeafletMouseEvent) => void)[];
    }

    interface Props {
        minZoom: number;
        boundary: Shape;
        onclick?: L.LeafletMouseEventHandlerFn | null;
        cursor?: string;
        children: any;
    }

    let { minZoom, boundary: corners, onclick = null, cursor = 'grab', children }: Props = $props();
    const boundary = L.latLngBounds([corners.from.lat, corners.from.lon], [corners.to.lat, corners.to.lon]);
    const scale = 1000;

    let ctx: MapContext = $state({
        map: undefined,
        boundary,
        width: 100,
        height: 100,
        projectPoint: (point) => ({
            x: ((point.lon - ctx.boundary.getWest()) / (ctx.boundary.getEast() - ctx.boundary.getWest())) * ctx.width,
            y:
                ((ctx.boundary.getNorth() - point.lat) / (ctx.boundary.getNorth() - ctx.boundary.getSouth())) *
                ctx.height,
        }),
        featureEventHandlers: [],
    });
    setContext('map', ctx);

    let svgElement: SVGElement;

    const initializeMap = (node: HTMLElement) => {
        ctx.map = L.map(node, {
            center: boundary.getCenter(),
            minZoom: minZoom,
            zoom: minZoom,
            maxBounds: boundary.pad(0.2),
        });

        ctx.map.on('click', (e) => {
            onclick?.(e);
        });

        L.tileLayer('https://tile.thunderforest.com/atlas/{z}/{x}/{y}.png?apikey=6a53e8b25d114a5e9216df5bf9b5e9c8', {
            maxZoom: 19,
        }).addTo(ctx.map);

        const topLeft = ctx.map.project(boundary.getNorthWest(), minZoom);
        const bottomRight = ctx.map.project(boundary.getSouthEast(), minZoom);

        const size = L.bounds(topLeft, bottomRight).getSize();

        ctx.width = scale;
        ctx.height = (size.y / size.x) * scale;

        addOverlay();

        return {
            destroy() {
                ctx.map?.remove();
                ctx.map = undefined;
            },
        };
    };

    const addOverlay = () => {
        const svgOverlay = L.svgOverlay(svgElement, boundary, {
            interactive: true,
        }).addTo(ctx.map!);

        svgOverlay.on('click', (e) => {
            const source = e.originalEvent.target as Element;
            const target = source.closest<HTMLElement>('[data-target]');
            if (target === null) return;

            L.DomEvent.stopPropagation(e);
            ctx.featureEventHandlers.forEach((handler) => handler(target.dataset.target!, e));
        });
    };

    $effect(() => {
        svgElement.style.cursor = cursor;
    });
</script>

<template>
    <svg width={ctx.width} height={ctx.height} viewBox="0 0 {ctx.width} {ctx.height}" bind:this={svgElement}>
        <rect x="0" y="0" width={ctx.width} height={ctx.height} stroke="black" stroke-width="10" fill="none" />
        {@render children?.()}
    </svg>
</template>
<div class="map" use:initializeMap onresize={() => ctx.map?.invalidateSize()}></div>

<style>
    .map {
        height: 100%;
    }
</style>

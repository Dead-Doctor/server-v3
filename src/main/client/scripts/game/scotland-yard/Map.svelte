<script lang="ts">
    import L from 'leaflet';
    import 'leaflet/dist/leaflet.css';
    import { setContext, type Snippet } from 'svelte';
    import type { Point, Shape } from './scotland-yard';

    export interface MapInfo {
        map: L.Map;
        boundary: L.LatLngBounds;
        width: number;
        height: number;
        projectPoint(point: Point): { x: number; y: number };
        featureEventHandlers: ((target: string, event: L.LeafletMouseEvent) => void)[];
    }

    export type MapContext = () => MapInfo

    interface Props {
        minZoom: number;
        boundary: Shape;
        onclick?: L.LeafletMouseEventHandlerFn | null;
        cursor?: string;
        children: Snippet;
    }

    let { minZoom, boundary: corners, onclick = null, cursor = 'grab', children }: Props = $props();
    const scale = 1000;

    let map: L.Map | null = $state(null)
    let boundary = $derived(L.latLngBounds([corners.from.lat, corners.from.lon], [corners.to.lat, corners.to.lon]))
    
    let size: L.Point = $derived.by(() => {
        return map !== null ? L.bounds(
            map.project(boundary.getNorthWest(), minZoom),
            map.project(boundary.getSouthEast(), minZoom)
        ).getSize() : L.point(10, 10)
    })
    let featureEventHandlers: ((target: string, event: L.LeafletMouseEvent) => void)[] = $state([])

    let info: MapInfo | null = $derived(map === null ? null : {
        map,
        boundary,
        width: scale,
        height: (size.y / size.x) * scale,
        projectPoint: (point) => ({
            x: ((point.lon - info!.boundary.getWest()) / (info!.boundary.getEast() - info!.boundary.getWest())) * info!.width,
            y:
                ((info!.boundary.getNorth() - point.lat) / (info!.boundary.getNorth() - info!.boundary.getSouth())) *
                info!.height,
        }),
        featureEventHandlers,
    });

    let svgOverlay: L.SVGOverlay;

    const initializeMap = (node: HTMLElement) => {
        map = L.map(node, {
            center: boundary.getCenter(),
            minZoom: minZoom,
            zoom: minZoom,
            maxBounds: boundary.pad(0.2),
        });
        setContext('map', () => info);

        map.on('click', (e) => {
            onclick?.(e);
        });

        L.tileLayer('https://tile.thunderforest.com/atlas/{z}/{x}/{y}.png?apikey=6a53e8b25d114a5e9216df5bf9b5e9c8', {
            maxZoom: 19,
        }).addTo(map);

        return {
            destroy() {
                map?.remove();
                map = null;
            },
        };
    };

    const initializeOverlay = (svgElement: SVGElement) => {
        svgOverlay = L.svgOverlay(svgElement, boundary, {
            interactive: true,
        }).addTo(map!);

        svgOverlay.on('click', (e) => {
            const source = e.originalEvent.target as Element;
            const target = source.closest<HTMLElement>('[data-target]');
            if (target === null) return;

            L.DomEvent.stopPropagation(e);
            featureEventHandlers.forEach((handler) => handler(target.dataset.target!, e));
        });

        $effect(() => {
            svgElement.style.cursor = cursor;
        });

        $effect(() => {
            map?.setMaxBounds(boundary.pad(0.2))
            svgOverlay.setBounds(boundary)
        });
    }

    $effect(() => {
        map?.setMinZoom(minZoom)
    });
</script>

<template>
    {#if info !== null}
        <svg width={info.width} height={info.height} viewBox="0 0 {info.width} {info.height}" use:initializeOverlay>
            {@render children?.()}
            <rect x="0" y="0" width={info.width} height={info.height} stroke="black" stroke-width="10" fill="none" />
        </svg>
    {/if}
</template>
<div class="map" use:initializeMap onresize={() => map?.invalidateSize()}></div>

<style>
    .map {
        height: 100%;
    }
</style>

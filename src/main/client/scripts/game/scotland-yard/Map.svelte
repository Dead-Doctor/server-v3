<script lang="ts">
    import L from 'leaflet';
    import 'leaflet/dist/leaflet.css';
    import { setContext } from 'svelte';

    export interface MapContext {
        map: L.Map | undefined
        boundary: L.LatLngBounds
        width: number
        height: number
    }

    interface Props {
        minZoom: number
        boundary: L.LatLngBounds
        children: any
    }

    let { minZoom, boundary, children }: Props = $props()
    const scale = 1000

    let ctx: MapContext = $state({ map: undefined, boundary, width: 100, height: 100 })
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

        addOverlay()

        return {
            destroy() {
                ctx.map?.remove();
                ctx.map = undefined;
            },
        };
    }

    const addOverlay = () => {
        const svgOverlay = L.svgOverlay(svgElement, boundary, {
            interactive: true
        }).addTo(ctx.map!)

        svgOverlay.on('click', (e) => {
            const source = e.originalEvent.target as Element
            const target = source.closest<HTMLElement>('.target')
            if (target === null) return
            console.log(`Clicked Intersection: ${target.dataset.intersection}`)
        })
    }
</script>

<div style="display: none;">
    <svg width={ctx.width} height={ctx.height} viewBox="0 0 {ctx.width} {ctx.height}" bind:this={svgElement}>
        <rect x="0" y="0" width={ctx.width} height={ctx.height} stroke="black" stroke-width="10" fill="none" />
        {@render children?.()}
    </svg>
</div>
<div class="map" use:initializeMap onresize={() => ctx.map?.invalidateSize()}></div>

<style>
    .map {
        height: 100%;
    }

    svg {
        cursor: grab;
    }
</style>
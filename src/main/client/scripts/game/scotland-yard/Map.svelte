<script lang="ts">
    import L from 'leaflet';
    import 'leaflet/dist/leaflet.css';
    import { setContext } from 'svelte';

    const minZoom = 14;
    const topLeft: L.LatLngTuple = [51.2396, 6.7635];
    const bottomRight: L.LatLngTuple = [51.1997, 6.8212];
    const mapBoundary = L.latLngBounds(topLeft, bottomRight);

    let ctx: { map: L.Map | undefined } = $state({ map: undefined })
    setContext('map', ctx)

    const initializeMap = (node: HTMLElement) => {
        ctx.map = L.map(node, {
            center: mapBoundary.getCenter(),
            minZoom: minZoom,
            zoom: minZoom,
            maxBounds: mapBoundary.pad(0.2),
        })
        L.tileLayer('https://tile.thunderforest.com/atlas/{z}/{x}/{y}.png?apikey=6a53e8b25d114a5e9216df5bf9b5e9c8', {
            maxZoom: 19,
        }).addTo(ctx.map);

        return {
            destroy() {
                ctx.map?.remove();
                ctx.map = undefined;
            },
        };
    }

</script>

<div class="map" use:initializeMap onresize={() => ctx.map?.invalidateSize()}></div>

<style>
    .map {
        height: 100%;
    }
</style>
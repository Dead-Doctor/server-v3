<script lang="ts" module>
    L.Icon.Default.prototype.options.imagePath = 'https://unpkg.com/leaflet@1.9.4/dist/images/';
</script>

<script lang="ts">
    import { getContext, onDestroy, onMount } from 'svelte';
    import type { MapContext } from './Map.svelte';
    import type { Point } from '../scotland-yard';
    import L from 'leaflet';

    interface Props {
        position: Point;
        ondrag?(position: Point): void;
    }

    let { position = $bindable(), ondrag }: Props = $props();
    let ctx: MapContext = getContext('map');
    let info = ctx()

    let marker: L.Marker

    onMount(() => {
        marker = L.marker(
            [position.lat, position.lon],
            ondrag ? { draggable: true, autoPan: true } : undefined
        ).addTo(info.map);

        if (ondrag)
            marker.on('drag', (e) => {
                const { lat, lng } = marker.getLatLng();
                position = { lat, lon: lng };
                ondrag(position);
            });
    })

    onDestroy(() => {
        marker.remove();
    })

    $effect(() => {
        marker.setLatLng([position.lat, position.lon])
    });
</script>

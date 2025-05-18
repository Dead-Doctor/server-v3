<script lang="ts">
    import { getContext, onDestroy, onMount } from "svelte";
    import type { Point } from "../scotland-yard";
    import L from "leaflet";
    import type { MapContext } from "./Map.svelte";

    interface Props {
        from: Point,
        to: Point
    }

    let { from, to }: Props = $props()

    let ctx: MapContext = getContext('map');
    let info = ctx()

    let polyline: L.Polyline

    onMount(() => {
        polyline = L.polyline([[from.lat, from.lon], [to.lat, to.lon]], { color: 'black', weight: 1 }).addTo(info.map);
    })

    $effect(() => {
        polyline.setLatLngs([[from.lat, from.lon], [to.lat, to.lon]])
    })

    onDestroy(() => {
        polyline.remove()
    })
</script>
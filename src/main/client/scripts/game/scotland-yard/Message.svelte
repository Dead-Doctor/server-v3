<script lang="ts">
    import { getContext, onMount } from "svelte";
    import type { Point } from "./scotland-yard";
    import L from "leaflet";
    import type { MapContext } from "./Map.svelte";

    interface Props {
        visible?: boolean,
        position: Point
        content: L.Content
    }

    let { visible = $bindable(false), position, content }: Props = $props()

    let ctx: MapContext = getContext('map');
    let info = ctx()

    let popup: L.Popup

    onMount(() => {
        popup = L.popup({autoPanPadding: L.point(50, 50)})

        popup.on('remove', () => {
            visible = false
        })
    })

    $effect(() => {
        popup.setLatLng([position.lat, position.lon])
    })

    $effect(() => {
        popup.setContent(content)
    })

    $effect(() => {
        if (visible === popup.isOpen()) return
        if (visible) popup.openOn(info.map)
        else popup.close()
    })
</script>
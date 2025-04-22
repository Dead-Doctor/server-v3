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

    let popup: L.Popup | null = null

    $effect(() => {
        popup?.setLatLng([position.lat, position.lon])
    })

    $effect(() => {
        popup?.setContent(content)
    })

    $effect(() => {
        if (visible === (popup !== null)) return

        if (visible) {
            const padding = info.map.getSize().divideBy(3)
            popup = L.popup({autoPanPadding: padding})
            
            popup.setLatLng([position.lat, position.lon])
            popup.setContent(content)

            popup.on('remove', () => {
                visible = false
            })

            popup.openOn(info.map)
        } else {
            popup?.close()
            popup = null
        }
    })
</script>
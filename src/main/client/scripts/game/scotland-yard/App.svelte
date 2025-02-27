<script lang="ts">
    import L from "leaflet";
    import Icon from "../../Icon.svelte";
    import Map from "./Map.svelte";
    import Intersection from "./Intersection.svelte";
    import { getData } from "../../routing";

    let gameContainer: HTMLElement
    let isFullscreen = $state(false)

    const toggleFullscreen = () => {
        if (!isFullscreen) {
            gameContainer.requestFullscreen();
        } else if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    }

    const topLeft: L.LatLngTuple = [51.2396, 6.7635];
    const bottomRight: L.LatLngTuple = [51.1997, 6.8212];
    
    const mapBoundary = L.latLngBounds(topLeft, bottomRight);
    const minZoom = 14

    interface MapData {
        intersections: IntersectionData[],
        connections: any,
    }

    interface IntersectionData {
        id: number
        pos: { lat: number, lon: number }
    }

    const map: MapData = getData('map')

    interface Point {
        id: number,
        lat: number,
        lon: number,
        bus: boolean,
        tram: boolean,
        selected: boolean
    }

    const points: Point[] = $state(map.intersections.map((i) => {
        return {
            id: i.id,
            lat: i.pos.lat,
            lon: i.pos.lon,
            bus: true,
            tram: true,
            selected: false
        }
    }))
    const radius = 5

    const addIntersection = (e: L.LeafletMouseEvent) => {
        points.push({
            id: 0,
            lat: e.latlng.lat,
            lon: e.latlng.lng,
            bus: false,
            tram: false,
            selected: false
        })
    }

    const selectIntersection = (point: Point) => {
        point.selected = !point.selected
    }
</script>

<section bind:this={gameContainer} onfullscreenchange={() => isFullscreen = document.fullscreenElement != null}>
    <div class="map">
        <Map {minZoom} boundary={mapBoundary} onclick={addIntersection}>
            {#each points as point}
                <Intersection id={point.id} lat={point.lat} lon={point.lon} {radius} bus={point.bus} tram={point.tram} selected={point.selected} onclick={() => selectIntersection(point)}></Intersection>
            {/each}
        </Map>
    </div>
    <div class="actions">
        <button>XYZ</button>
        <div class="spacing"></div>
        <button onclick={toggleFullscreen}><Icon id={!isFullscreen ? 'fullscreen' : 'fullscreen-exit'}/></button>
    </div>
</section>

<style>
    section {
        display: flex;
        flex-direction: column;
        height: 80vh;
        background-color: var(--secondary);
        border: var(--border);
        border-radius: 1rem;
        overflow: hidden;

        &:fullscreen {
            border: none;
            border-radius: 0;
        }
    }

    .map {
        flex-grow: 1;
    }

    .actions {
        display: flex;
        height: 3rem;

        :not(:first-child) {
                border-left: var(--border);
        }

        .spacing {
            flex-grow: 1;
        }

        button {
            margin: 0;
            padding: 0 1rem;
            border: none;
            border-radius: 0;

            &:hover,
            &:focus-visible {
                background-color: var(--decoration);
            }

            &:active {
                background-color: var(--accent);
            }
        }
    }
</style>
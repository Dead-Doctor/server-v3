<script lang="ts">
    import L from "leaflet";
    import Icon from "../../Icon.svelte";
    import Map from "./Map.svelte";
    import Intersection from "./Intersection.svelte";
    import { getData } from "../../routing";
    import { transport, type MapData, type Point } from "./scotland-yard";
    import Connection from "./Connection.svelte";

    let gameContainer: HTMLElement
    let isFullscreen = $state(false)

    const toggleFullscreen = () => {
        if (!isFullscreen) {
            gameContainer.requestFullscreen();
        } else if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    }

    const map: MapData = getData('map')

    interface IntersectionPoint {
        id: number,
        position: Point,
        bus: boolean,
        tram: boolean,
    }

    const intersections: IntersectionPoint[] = map.intersections.map((i) => {
        return {
            id: i.id,
            position: i.pos,
            bus: false,
            tram: false,
        }
    })

    const findIntersectionById = (id: number) => intersections.find(i => i.id === id)

    const connections = map.connections.map((c) => {
        const i1 = findIntersectionById(c.from)!
        const i2 = findIntersectionById(c.to)!
        if (c.type === transport.BUS) {
            i1.bus = true; i2.bus = true
        } else if (c.type === transport.TRAM) {
            i1.tram = true; i2.tram = true
        }
        return {
            id: c.id,
            from: i1.position,
            to: i2.position,
            type: c.type,
            shape: c.shape
        }
    })
</script>

<section bind:this={gameContainer} onfullscreenchange={() => isFullscreen = document.fullscreenElement != null}>
    <div class="map">
        <Map minZoom={map.minZoom} boundary={map.boundary}>
            {#each connections as c}
                <Connection id={c.id} from={c.from} to={c.to} width={map.connectionWidth} shape={c.shape} type={c.type}></Connection>
            {/each}
            {#each intersections as i}
                <Intersection id={i.id} position={i.position} radius={map.intersectionRadius} bus={i.bus} tram={i.tram}></Intersection>
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
    :root {
        --taxi-color: #ffd000;
        --bus-color: #008e59;
        --tram-color: #ff3900;
        --train-color: #000000;
    }

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
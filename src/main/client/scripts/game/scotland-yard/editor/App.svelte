<script lang="ts">
    import Icon from "../../../Icon.svelte";
    import { getData } from "../../../routing";
    import Map from "../Map.svelte";
    import Connection from "../Connection.svelte";
    import Fullscreen from "../Fullscreen.svelte";
    import Intersection from "../Intersection.svelte";
    import { transport, type MapData, type Point, type Shape, type Transport } from "../scotland-yard";

    let isFullscreen = $state(false)

    const map: MapData = getData('map')

    interface IntersectionData {
        position: Point
        bus: boolean
        tram: boolean
    }

    interface ConnectionData {
        from: number
        to: number
        type: Transport
        shape: Shape
    }

    const intersections: { [id: number]: IntersectionData} = $state({})
    const connections: { [id: number]: ConnectionData } = $state({})

    for (const i of map.intersections) {
        intersections[i.id] = {
            position: i.pos,
            bus: false,
            tram: false
        }
    }

    for (const c of map.connections) {
        const from = intersections[c.from]
        const to = intersections[c.to]
        if (c.type === transport.BUS) {
            from.bus = true; from.bus = true
        } else if (c.type === transport.TRAM) {
            to.tram = true; to.tram = true
        }

        connections[c.id] = {
            from: c.from,
            to: c.to,
            type: c.type,
            shape: c.shape
        }
    }

    const positionById = (id: number) => intersections[id].position
</script>

<Fullscreen bind:isFullscreen>
    <div class="map">
        <Map minZoom={map.minZoom} boundary={map.boundary}>
            {#each Object.entries(connections) as [id, c] (id)}
                <Connection id={id} from={positionById(c.from)} to={positionById(c.to)} width={map.connectionWidth} shape={c.shape} type={c.type}></Connection>
            {/each}
            {#each Object.entries(intersections) as [id, i] (id)}
                <Intersection id={id} position={i.position} radius={map.intersectionRadius} bus={i.bus} tram={i.tram}></Intersection>
            {/each}
        </Map>
    </div>
    <div class="actions">
        <button>Tool 1</button>
        <button>Tool 2</button>
        <button>Tool 3</button>
        <div class="spacing"></div>
        <button><Icon id="gear"/></button>
        <button onclick={() => isFullscreen = !isFullscreen}><Icon id={!isFullscreen ? 'fullscreen' : 'fullscreen-exit'}/></button>
    </div>
</Fullscreen>

<style>
    :root {
        --taxi-color: #ffd000;
        --bus-color: #008e59;
        --tram-color: #ff3900;
        --train-color: #000000;
    }

    .map {
        flex-grow: 1;
    }

    .actions {
        display: flex;
        height: 3rem;

        > * {
            display: flex;
            align-items: center;
            padding: 0 1rem;
        }

        :not(:first-child) {
            border-left: var(--border);
        }

        .spacing {
            flex-grow: 1;
        }

        button {
            margin: 0;
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
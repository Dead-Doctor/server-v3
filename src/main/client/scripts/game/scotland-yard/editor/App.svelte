<script lang="ts">
    import Icon from '../../../Icon.svelte';
    import { getData } from '../../../routing';
    import Map from '../Map.svelte';
    import Connection from '../Connection.svelte';
    import Fullscreen from '../Fullscreen.svelte';
    import Intersection from '../Intersection.svelte';
    import { transport, type MapData, type Point, type Shape, type Transport } from '../scotland-yard';
    import { connectChannel } from '../../../channel';
    import { bcs } from '../../../bcs';
    import Marker from '../Marker.svelte';
    import Popup from '../../../Popup.svelte';

    let isFullscreen = $state(false);

    let map: MapData = $state(getData('map'));

    const tools = {
        SELECT: 'select',
        ADD: 'add',
    } as const;

    type Tool = (typeof tools)[keyof typeof tools];
    let tool: Tool | null = $state(null);
    let selection:
        | { type: 'boundary' }
        | { type: 'intersection'; id: number; intersection: IntersectionData }
        | { type: 'connection'; id: number; connection: ConnectionData }
        | null = $state(null);

    //Settings
    let showSettings = $state(false);
    let nextMinZoom = $state(map.minZoom);
    let nextIntersectionRadius = $state(map.intersectionRadius);
    let nextConnectionWidth = $state(map.connectionWidth);
    
    let popup = $state({
        visible: false,
        message: '',
        closable: false,
        buttonText: '',
        buttonAction() {}
    })

    const channel = connectChannel();
    const bcsPoint = bcs.struct({
        lat: bcs.double,
        lon: bcs.double,
    });
    const bcsShape = bcs.struct({
        from: bcsPoint,
        to: bcsPoint,
    });
    const bcsIntersection = bcs.struct({
        id: bcs.int,
        pos: bcsPoint,
    });
    const bcsConnection = bcs.struct({
        id: bcs.int,
        from: bcs.int,
        to: bcs.int,
        type: bcs.int,
        shape: bcsShape,
    });
    const bcsMap = bcs.struct({
        boundary: bcsShape,
        minZoom: bcs.int,
        intersectionRadius: bcs.double,
        connectionWidth: bcs.double,
        intersections: bcs.list(bcsIntersection),
        connections: bcs.list(bcsConnection)
    })
    const sendChangeBoundary = channel.destinationWith(bcsShape);
    const sendChangeMinZoom = channel.destinationWith(bcs.int);
    const sendChangeIntersectionRadius = channel.destinationWith(bcs.double);
    const sendChangeConnectionWidth = channel.destinationWith(bcs.double);
    const sendChangeIntersection = channel.destinationWith(bcsIntersection);
    const sendChangeConnection = channel.destinationWith(bcsConnection);
    const sendSave = channel.destination();
    const sendReset = channel.destination();
    channel.receiverWith(onUpdateBoundary, bcsShape)
    channel.receiverWith(onUpdateMinZoom, bcs.int)
    channel.receiverWith(onUpdateIntersectionRadius, bcs.double)
    channel.receiverWith(onUpdateConnectionWidth, bcs.double)
    channel.receiverWith(onSave, bcs.int)
    // channel.receiverWith(onReset, bcsMap)

    interface IntersectionData {
        position: Point;
        bus: boolean;
        tram: boolean;
    }

    interface ConnectionData {
        from: number;
        to: number;
        type: Transport;
        shape: Shape;
    }

    const intersections: { [id: number]: IntersectionData } = $state({});
    const connections: { [id: number]: ConnectionData } = $state({});

    for (const i of map.intersections) {
        intersections[i.id] = {
            position: i.pos,
            bus: false,
            tram: false,
        };
    }

    for (const c of map.connections) {
        const from = intersections[c.from];
        const to = intersections[c.to];
        if (c.type === transport.BUS) {
            from.bus = true;
            from.bus = true;
        } else if (c.type === transport.TRAM) {
            to.tram = true;
            to.tram = true;
        }

        connections[c.id] = {
            from: c.from,
            to: c.to,
            type: c.type,
            shape: c.shape,
        };
    }

    const positionById = (id: number) => intersections[id].position;

    const swapTool = (next: Tool) => {
        tool = tool === next ? null : next;

        if (tool !== 'select') selection = null
    };

    const editBoundary = () => {
        showSettings = false;
        tool = 'select';
        selection = { type: 'boundary' };
    };

    type Side = 'from' | 'to'
    const corners: [Side, Side][] = [
        ['from', 'from'],
        ['to', 'from'],
        ['to', 'to'],
        ['from', 'to']
    ]
    const moveBoundary = (latIdentifier: Side, lonIdentifier: Side) => ({ lat, lon }: Point) => {
        map.boundary[latIdentifier].lat = lat
        map.boundary[lonIdentifier].lon = lon
        sendChangeBoundary(map.boundary)
    }

    function onUpdateBoundary(boundary: Shape) {
        map.boundary = boundary
    }

    const changeMinZoom = () => {
        sendChangeMinZoom(nextMinZoom)
        onUpdateMinZoom(nextMinZoom)
    }

    function onUpdateMinZoom(minZoom: number) {
        map.minZoom = minZoom;
    }

    const changeIntersectionRadius = () => {
        sendChangeIntersectionRadius(nextIntersectionRadius)
        onUpdateIntersectionRadius(nextIntersectionRadius)
    }
    
    function onUpdateIntersectionRadius(intersectionRadius: number) {
        map.intersectionRadius = intersectionRadius
    }

    const changeConnectionWidth = () => {
        sendChangeConnectionWidth(nextConnectionWidth)
        onUpdateConnectionWidth(nextConnectionWidth)
    }

    function onUpdateConnectionWidth(connectionWidth: number) {
        map.connectionWidth = connectionWidth
    }

    //TODO: make versioning system clear (loading, saving)
    //TODO: show playericons of currently editing (connected) users

    const save = () => {
        sendSave()
    }

    function onSave(version: number) {
        popup = {
            visible: true,
            message: `Saved changes as version ${version}.`,
            closable: false,
            buttonText: 'Ok',
            buttonAction() {
                popup.visible = false
            }
        }
    }

    const reset = () => {
        popup = {
            visible: true,
            message: 'Are you sure you want to discard all changes?',
            closable: true,
            buttonText: 'Reset',
            buttonAction() {
                sendReset()
            }
        }
    }

    //TODO: implement enum for bcs encoding
    function onReset(data: MapData) {
        // map = data
        popup.visible = false
    }
</script>

<Fullscreen bind:isFullscreen>
    <div class="map">
        <Map minZoom={map.minZoom} boundary={map.boundary} cursor={tool === 'add' ? 'crosshair' : 'grab'}>
            {#each Object.entries(connections) as [id, c] (id)}
                <Connection
                    {id}
                    from={positionById(c.from)}
                    to={positionById(c.to)}
                    width={map.connectionWidth}
                    shape={c.shape}
                    type={c.type}
                    cursor={tool === 'select' ? 'pointer' : 'grab'}
                />
            {/each}
            {#each Object.entries(intersections) as [id, i] (id)}
                <Intersection
                    {id}
                    position={i.position}
                    radius={map.intersectionRadius}
                    bus={i.bus}
                    tram={i.tram}
                    cursor={tool !== null ? 'pointer' : 'grab'}
                />
            {/each}
            {#if selection?.type === 'boundary'}
                {#each corners as corner}
                    <Marker position={{ lat: map.boundary[corner[0]].lat, lon: map.boundary[corner[1]].lon }} ondrag={moveBoundary(...corner)} />
                {/each}
            {/if}
        </Map>
    </div>
    <div class="tools">
        <button class:active={tool === 'select'} onclick={() => swapTool('select')}>Select Tool</button>
        <button class:active={tool === 'add'} onclick={() => swapTool('add')}>Add Tool</button>
        <div class="spacing"></div>
        <button onclick={() => (showSettings = true)}><Icon id="gear" /></button>
        <button onclick={() => (isFullscreen = !isFullscreen)}
            ><Icon id={!isFullscreen ? 'fullscreen' : 'fullscreen-exit'} /></button
        >
    </div>
    {#if showSettings}
        <div class="settings">
            <button class="closeBtn" onclick={() => (showSettings = false)}><Icon id="x-lg" /></button>
            <div class="options">
                <label for="changeBoundary">Boundary:</label><button id="changeBoundary" onclick={editBoundary}
                    >Edit</button
                >
                <label for="changeMinZoom">Minimum Zoom:</label><input
                    type="number"
                    name="changeMinZoom"
                    id="changeMinZoom"
                    min="0"
                    max="19"
                    bind:value={nextMinZoom}
                    onchange={changeMinZoom}
                />
                <label for="changeIntersectionRadius">Intersection Radius:</label><input
                    type="range"
                    name="changeIntersectionRadius"
                    id="changeIntersectionRadius"
                    min="1"
                    max="20"
                    step="1"
                    bind:value={nextIntersectionRadius}
                    oninput={changeIntersectionRadius}
                />
                <label for="changeConnectionWidth">Connection width:</label><input
                    type="range"
                    name="changeConnectionWidth"
                    id="changeConnectionWidth"
                    min="0.2"
                    max="5"
                    step="0.1"
                    bind:value={nextConnectionWidth}
                    oninput={changeConnectionWidth}
                />
            </div>
            <div class="actions">
                <button onclick={save}>Save</button>
                <button class="danger" onclick={reset}>Reset</button>
            </div>
        </div>
    {/if}
</Fullscreen>
<Popup
    bind:visible={popup.visible}
    message={popup.message}
    closable={popup.closable}
    buttonText={popup.buttonText}
    buttonAction={popup.buttonAction}
/>

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

    .tools {
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
            &:focus-visible,
            &.active {
                background-color: var(--decoration);
            }

            &:active {
                background-color: var(--accent);
            }
        }
    }

    .settings {
        position: absolute;
        inset: 0;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        padding: 10rem 5rem;
        backdrop-filter: brightness(50%) blur(4px);
        z-index: 1000;

        .closeBtn {
            position: absolute;
            top: 1rem;
            right: 1rem;
        }

        .options {
            display: grid;
            grid-template-columns: 1fr 30%;
            align-items: center;
            font-size: 1.2em;
            font-weight: 600;

            input {
                width: auto;
            }
        }

        .actions {
            align-self: end;

            .danger:not(:hover):not(:focus-visible) {
                background-color: hsl(0, 60%, 43%);
            }
        }
    }
</style>

<script lang="ts">
    import Icon from '../../../Icon.svelte';
    import { getData } from '../../../routing';
    import Map from '../map/Map.svelte';
    import Connection from '../map/Connection.svelte';
    import Fullscreen from '../Fullscreen.svelte';
    import Intersection from '../map/Intersection.svelte';
    import {
        Points,
        transport,
        type ConnectionData,
        type Enum,
        type IntersectionData,
        type MapData,
        type MapInfo,
        type Point,
        type Shape,
        type Transport,
    } from '../scotland-yard';
    import { connectChannel } from '../../../channel';
    import { bcs } from '../../../bcs';
    import Marker from '../map/Marker.svelte';
    import Popup from '../../../Popup.svelte';
    import Line from '../map/Line.svelte';

    interface IntersectionDerived {
        position: Point;
        bus: boolean;
        tram: boolean;
    }

    interface ConnectionDerived {
        from: number;
        to: number;
        type: Transport;
        shape: Shape;
    }

    let isFullscreen = $state(false);

    let info: MapInfo = $state(getData('map'));
    let map: MapData = $state(getData('changes'));

    let intersections: { [id: number]: IntersectionDerived } = $state({});
    let connections: { [id: number]: ConnectionDerived } = $state({});

    const tools = {
        SELECT: 'select',
        ADD: 'add',
    } as const;
    type Tool = Enum<typeof tools>;

    let tool: Tool | null = $state(null);
    let selection:
        | { type: 'boundary' }
        | { type: 'intersection'; id: number; position: Point }
        | { type: 'connection'; id: number; from: Point; to: Point }
        | null = $state(null);

    //Settings
    let showSettings = $state(false);

    let popup = $state({
        visible: false,
        message: '',
        closable: false,
        buttonText: '',
        buttonAction() {},
    });

    const deriveMap = () => {
        intersections = {};
        connections = {};
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
                to.bus = true;
            } else if (c.type === transport.TRAM) {
                from.tram = true;
                to.tram = true;
            }

            connections[c.id] = {
                from: c.from,
                to: c.to,
                type: c.type,
                shape: c.shape,
            };
        }
    };
    deriveMap();

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
        type: bcs.enumeration(transport),
        shape: bcsShape,
    });
    const bcsMap = bcs.struct({
        boundary: bcsShape,
        minZoom: bcs.int,
        intersectionRadius: bcs.double,
        connectionWidth: bcs.double,
        intersections: bcs.list(bcsIntersection),
        connections: bcs.list(bcsConnection),
    });
    const sendChangeBoundary = channel.destinationWith(bcsShape);
    const sendChangeMinZoom = channel.destinationWith(bcs.int);
    const sendChangeIntersectionRadius = channel.destinationWith(bcs.double);
    const sendChangeConnectionWidth = channel.destinationWith(bcs.double);
    const sendChangeIntersection = channel.destinationWith(bcsIntersection);
    const sendChangeConnection = channel.destinationWith(bcsConnection);
    const sendSave = channel.destination();
    const sendReset = channel.destination();
    channel.receiverWith(onUpdateBoundary, bcsShape);
    channel.receiverWith(onUpdateMinZoom, bcs.int);
    channel.receiverWith(onUpdateIntersectionRadius, bcs.double);
    channel.receiverWith(onUpdateConnectionWidth, bcs.double);
    channel.receiverWith(onUpdateIntersection, bcsIntersection);
    channel.receiverWith(onUpdateConnection, bcsConnection);
    channel.receiverWith(onSave, bcs.int);
    channel.receiverWith(onReset, bcsMap);

    const positionById = (id: number) => intersections[id].position;

    const swapTool = (next: Tool) => {
        tool = tool === next ? null : next;

        if (tool !== 'select') selection = null;
    };

    const clickIntersection = (id: number) => {
        if (tool === 'select') {
            selection = { type: 'intersection', id, position: intersections[id]!.position };
        }
    };

    const editBoundary = () => {
        showSettings = false;
        tool = 'select';
        selection = { type: 'boundary' };
    };

    type Side = 'from' | 'to';
    const corners: [Side, Side][] = [
        ['from', 'from'],
        ['to', 'from'],
        ['to', 'to'],
        ['from', 'to'],
    ];

    const createUpdateBuffer = (updater: () => void, interval: number) => {
        let sender: number | null = null;

        return () => {
            if (sender === null) {
                sender = setTimeout(() => {
                    updater();
                    sender = null;
                }, interval);
            }
        };
    };

    const moveBoundaryBuffer = createUpdateBuffer(() => sendChangeBoundary(map.boundary), 500);
    const moveBoundary =
        (latIdentifier: Side, lonIdentifier: Side) =>
        ({ lat, lon }: Point) => {
            map.boundary[latIdentifier].lat = lat;
            map.boundary[lonIdentifier].lon = lon;
            moveBoundaryBuffer();
        };

    function onUpdateBoundary(boundary: Shape) {
        map.boundary = boundary;
    }

    const changeMinZoom = () => {
        sendChangeMinZoom(map.minZoom);
    };
    const changeIntersectionRadius = () => {
        sendChangeIntersectionRadius(map.intersectionRadius);
    };
    const changeConnectionWidth = () => {
        sendChangeConnectionWidth(map.connectionWidth);
    };

    function onUpdateMinZoom(minZoom: number) {
        map.minZoom = minZoom;
    }
    function onUpdateIntersectionRadius(intersectionRadius: number) {
        map.intersectionRadius = intersectionRadius;
    }
    function onUpdateConnectionWidth(connectionWidth: number) {
        map.connectionWidth = connectionWidth;
    }

    let lastIntersectionData: IntersectionData;
    const intersectionMoveBuffer = createUpdateBuffer(() => sendChangeIntersection(lastIntersectionData), 500);
    const editIntersectionPosition = () => {
        if (selection?.type !== 'intersection') return;
        const intersection = intersections[selection.id]!;
        intersection.position = selection.position;
        lastIntersectionData = { id: selection.id, pos: intersection.position };
        intersectionMoveBuffer();
    };

    function onUpdateIntersection(intersection: IntersectionData) {
        intersections[intersection.id].position = intersection.pos;
    }

    let lastConnectionData: ConnectionData;
    const editConnectionBuffer = createUpdateBuffer(() => sendChangeConnection(lastConnectionData), 500);
    const editConnectionShape = () => {
        if (selection?.type !== 'connection') return;
        const connection = connections[selection.id]!;
        const from = Points.sub(selection.from, intersections[connection.from]!.position);
        const to = Points.sub(selection.to, intersections[connection.to]!.position);
        connection.shape = { from, to };
        lastConnectionData = { id: selection.id, ...connection };
        editConnectionBuffer();
    };

    function onUpdateConnection(connection: ConnectionData) {
        connections[connection.id] = connection;
        //TODO: Updated derived info
    }

    //TODO: show playericons of currently editing (connected) users
    // -> maybe message in corner 'Editing v65 (dd) (user)'

    const save = () => {
        sendSave();
    };

    function onSave(version: number) {
        info.version = version;
        popup = {
            visible: true,
            message: `Saved changes as version ${version}.`,
            closable: false,
            buttonText: 'Ok',
            buttonAction() {
                showSettings = false;
                popup.visible = false;
            },
        };
    }

    const reset = () => {
        popup = {
            visible: true,
            message: 'Are you sure you want to discard all changes?',
            closable: true,
            buttonText: 'Reset',
            buttonAction() {
                sendReset();
            },
        };
    };

    function onReset(data: MapData) {
        map.boundary = data.boundary;
        map.minZoom = data.minZoom;
        map.intersectionRadius = data.intersectionRadius;
        map.connectionWidth = data.connectionWidth;
        map.intersections = data.intersections;
        map.connections = data.connections;
        deriveMap();
        showSettings = false;
        popup.visible = false;
    }
</script>

<Fullscreen bind:isFullscreen>
    <div class="map">
        <Map
            minZoom={map.minZoom}
            boundary={map.boundary}
            cursor={tool === 'add' ? 'crosshair' : 'grab'}
            onclick={() => tool === 'select' && (selection = null)}
        >
            {#each Object.entries(connections) as [key, c] (key)}
                {@const id = parseInt(key)}
                <Connection
                    id={key}
                    from={positionById(c.from)}
                    to={positionById(c.to)}
                    width={map.connectionWidth}
                    shape={c.shape}
                    type={c.type}
                    cursor={tool === 'select' ? 'pointer' : 'grab'}
                    onclick={() =>
                        tool === 'select' &&
                        (selection = {
                            type: 'connection',
                            id,
                            from: Points.add(intersections[c.from]!.position, c.shape.from),
                            to: Points.add(intersections[c.to]!.position, c.shape.to),
                        })}
                    selected={selection?.type === 'connection' && selection.id === id}
                />
            {/each}
            {#each Object.entries(intersections) as [key, i] (key)}
                {@const id = parseInt(key)}
                <Intersection
                    id={key}
                    position={i.position}
                    radius={map.intersectionRadius}
                    bus={i.bus}
                    tram={i.tram}
                    cursor={tool !== null ? 'pointer' : 'grab'}
                    onclick={() => clickIntersection(id)}
                    selected={selection?.type === 'intersection' && selection.id === id}
                />
            {/each}
            {#if selection?.type === 'boundary'}
                {#each corners as corner}
                    <Marker
                        position={{ lat: map.boundary[corner[0]].lat, lon: map.boundary[corner[1]].lon }}
                        ondrag={moveBoundary(...corner)}
                    />
                {/each}
            {:else if selection?.type === 'intersection'}
                <Marker bind:position={selection.position} ondrag={editIntersectionPosition} />
            {:else if selection?.type === 'connection'}
                {@const connection = connections[selection.id]!}
                <Marker bind:position={selection.from} ondrag={editConnectionShape} />
                <Marker bind:position={selection.to} ondrag={editConnectionShape} />
                <Line from={intersections[connection.from]!.position} to={selection.from} />
                <Line from={intersections[connection.to]!.position} to={selection.to} />
            {/if}
        </Map>
        <span class="info">Editing {info.name} v{info.version}</span>
    </div>
    <div class="tools">
        <button class:active={tool === 'select'} onclick={() => swapTool('select')}>Select Tool</button>
        <button class:active={tool === 'add'} onclick={() => swapTool('add')}>Add Tool</button>
        <div class="spacing"></div>
        <button onclick={() => ((showSettings = true), (selection = null))}><Icon id="gear" /></button>
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
                    bind:value={map.minZoom}
                    onchange={changeMinZoom}
                />
                <label for="changeIntersectionRadius">Intersection Radius:</label><input
                    type="range"
                    name="changeIntersectionRadius"
                    id="changeIntersectionRadius"
                    min="1"
                    max="20"
                    step="1"
                    bind:value={map.intersectionRadius}
                    oninput={changeIntersectionRadius}
                />
                <label for="changeConnectionWidth">Connection width:</label><input
                    type="range"
                    name="changeConnectionWidth"
                    id="changeConnectionWidth"
                    min="0.2"
                    max="5"
                    step="0.1"
                    bind:value={map.connectionWidth}
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
        position: relative;

        .info {
            position: absolute;
            top: 0;
            right: 0;
            margin: 1rem;
            color: white;
            font-weight: 600;
            text-shadow:
                black 0 0 4px,
                black 0 0 6px;
            pointer-events: none;
            z-index: 400;
        }
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

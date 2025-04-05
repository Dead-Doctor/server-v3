<script lang="ts">
    import Icon from '../../Icon.svelte';
    import Map from './Map.svelte';
    import Intersection from './Intersection.svelte';
    import { getData } from '../../routing';
    import "../../lobby.svelte";
    import {
        role,
        transport,
        type Enum,
        type MapData,
        type Role,
        type Point,
        type Transport,
        type Shape,
        roleNames,
    } from './scotland-yard';
    import Connection from './Connection.svelte';
    import Fullscreen from './Fullscreen.svelte';
    import { connectGameChannel } from '../game.svelte';
    import Player from './Player.svelte';
    import { playerById, you, type PlayerId } from '../../lobby.svelte';
    import { bcs } from '../../bcs';
    import Message from './Message.svelte';

    const ticket = {
        TAXI: transport.TAXI,
        BUS: transport.BUS,
        TRAM: transport.TRAM,
        MULTI: 'multi'
    } as const;
    type Ticket = Enum<typeof ticket>;

    const ticketNames: { [_ in Ticket]: string } = {
        [ticket.TAXI]: 'Taxi',
        [ticket.BUS]: 'Bus',
        [ticket.TRAM]: 'Tram',
        [ticket.MULTI]: 'Multi'
    };

    interface IntersectionData {
        position: Point;
        bus: boolean;
        tram: boolean;
        connections: number[];
    }

    interface ConnectionData {
        from: number;
        to: number;
        type: Transport;
        shape: Shape;
    }

    const map: MapData = getData('map');

    let intersections: { [id: number]: IntersectionData } = {};
    let connections: { [id: number]: ConnectionData } = {};

    for (const i of map.intersections) {
        intersections[i.id] = {
            position: i.pos,
            bus: false,
            tram: false,
            connections: [],
        };
    }

    for (const c of map.connections) {
        const from = intersections[c.from];
        const to = intersections[c.to];

        from.connections.push(c.id);
        to.connections.push(c.id);
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
    
    let isFullscreen = $state(false);
    
    const roles: { [_ in Role]: PlayerId | null } = $state(getData('roles'));
    const yourRole = $derived((Object.entries(roles).find(([_, id]) => id === you.id)?.[0] as Role | undefined) ?? null)

    const positions: { [_ in Role]: number } = $state(getData('positions'));
    let turn: Role = $state(getData('turn'))
    const yourTurn = $derived(turn === yourRole || (roles[turn] === null && (yourRole?.startsWith('detective') ?? false)))
    let yourTurnMessage = $state(false)

    let showTickets = $state(false);
    let selectedTicket: Ticket | null = $state(null);

    const availableConnections = $derived(yourTurn ? intersections[positions[turn!]].connections : null);

    let chosenConnection: string | null = $state(null)

    const bcsRole = bcs.enumeration(role)
    const channel = connectGameChannel();
    const sendTakeConnection = channel.destinationWith(bcs.tuple([bcs.enumeration(ticket), bcs.int]))
    channel.receiverWith(onMove, bcs.tuple([bcsRole, bcs.int] as const))
    channel.receiverWith(onNextTurn, bcsRole)

    const availableTickets: { [_ in Ticket]: boolean } = {
        [ticket.TAXI]: false,
        [ticket.BUS]: false,
        [ticket.TRAM]: false,
        [ticket.MULTI]: true
    };
    
    const beginTurn = () => {
        if (!yourTurn) return
        
        showTickets = true;
        yourTurnMessage = true;

        availableTickets[ticket.TAXI] = false;
        availableTickets[ticket.BUS] = false;
        availableTickets[ticket.TRAM] = false;
        for (const id of availableConnections!) {
            const connection = connections[id];
            switch (connection.type) {
                case transport.TAXI:
                    availableTickets[ticket.TAXI] = true; break;
                case transport.BUS:
                    availableTickets[ticket.BUS] = true; break;
                case transport.TRAM:
                    availableTickets[ticket.TRAM] = true; break;
            }
        }
    };

    const endTurn = () => {
        showTickets = false;
        yourTurnMessage = false;
        selectedTicket = null;
    };

    const selectTicket = (t: Ticket) => {
        selectedTicket = selectedTicket === t ? null : t;
    };

    const canChooseConnection = $derived(showTickets && selectedTicket != null)

    const isValidTicket = (type: Transport, t: Ticket) =>
        t === ticket.MULTI
        || t === ticket.TAXI && type === transport.TAXI
        || t === ticket.BUS && type === transport.BUS
        || t === ticket.TRAM && type === transport.TRAM
    
    const chooseConnection = (id: string) => () => {
        if (selectedTicket === null) return
        const connection = parseInt(id)

        sendTakeConnection([selectedTicket, connection])
    }

    function onMove([r, id]: [Role, number]) {
        positions[r] = id
    }

    beginTurn()
    function onNextTurn(next: Role) {
        endTurn()
        turn = next
        beginTurn()
    }
</script>

<Fullscreen bind:isFullscreen>
    <div class="map">
        <Map minZoom={map.minZoom} boundary={map.boundary}>
            {#each Object.entries(connections) as [id, c]}
                <Connection
                    id={id}
                    from={intersections[c.from].position}
                    to={intersections[c.to].position}
                    width={map.connectionWidth}
                    shape={c.shape}
                    type={c.type}
                    disabled={canChooseConnection
                    && (!isValidTicket(c.type, selectedTicket!)
                        || (availableConnections !== null && !availableConnections.includes(parseInt(id))))}
                    onclick={canChooseConnection ? chooseConnection(id) : null}
                    selected={chosenConnection === id}
                ></Connection>
            {/each}
            {#each Object.entries(intersections) as [id, i]}
                <Intersection
                    id={id.toString()}
                    position={i.position}
                    radius={map.intersectionRadius}
                    bus={i.bus}
                    tram={i.tram}
                ></Intersection>
            {/each}
            {#each Object.entries(positions) as [role, id]}
                <Player
                    role={role as Role}
                    position={intersections[id].position}
                    size={map.intersectionRadius * 4}
                ></Player>
            {/each}
            <Message bind:visible={yourTurnMessage} position={intersections[positions[turn]].position} content="It's your turn!" />
        </Map>
        <div class="overlay info">
            <h3>{roleNames[turn]}'s turn</h3>
            {#if yourTurn}
                <span>It's your turn</span>
            {:else}
                <span>Wait for {roles[turn] !== null ? playerById(roles[turn]!)!.name : 'the detectives'} to finish</span>
            {/if}
        </div>
        <div class="overlay tickets" class:enabled={showTickets}>
            {#each Object.values(ticket) as t}
                <button
                    class={t}
                    disabled={!showTickets || !availableTickets[t]}
                    class:active={(selectedTicket === null && availableTickets[t]) || selectedTicket === t}
                    onclick={() => selectTicket(t)}>{ticketNames[t]}</button
                >
            {/each}
        </div>
    </div>
    <div class="actions">
        <div>{yourRole !== null ? roleNames[yourRole] : 'Spectator'}</div>
        <div class="spacing"></div>
        <button>TEST</button>
        <button>123</button>
        <button>ABC</button>
        <div class="spacing"></div>
        <button><Icon id="gear" /></button>
        <button onclick={() => (isFullscreen = !isFullscreen)}>
            <Icon id={!isFullscreen ? 'fullscreen' : 'fullscreen-exit'} />
        </button>
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
        position: relative;

        .overlay {
            position: absolute;
            left: 0;
            right: 0;
            margin: 1rem 0;
            pointer-events: none;
            z-index: 400;
        }

        .info {
            top: 0;
            color: white;
            text-shadow: black 0 0 2px, black 0 0 4px, black 0 0 6px, black 0 0 8px;
            text-align: center;
        }

        .tickets {
            display: none;
            bottom: 0;
            justify-content: center;
            gap: 1rem;

            &.enabled {
                display: flex;
            }

            button {
                width: 5rem;
                padding: 0.7rem 0;
                border: var(--border);
                border-radius: 0.3rem;
                text-align: center;
                text-transform: uppercase;
                filter: saturate(0.5) contrast(0.6);
                pointer-events: all;

                &.taxi {
                    background-color: var(--taxi-color);
                }

                &.bus {
                    background-color: var(--bus-color);
                }

                &.tram {
                    background-color: var(--tram-color);
                }

                &.multi {
                    background: conic-gradient(
                        var(--bus-color) 0% 25%,
                        var(--train-color) 25% 50%,
                        var(--tram-color) 50% 75%,
                        var(--taxi-color) 75% 0%
                    );
                }

                &:disabled {
                    opacity: 0.4;
                }

                &:not(:disabled) {
                    &.active {
                        filter: none;

                        &:hover {
                            font-weight: 600;
                        }
                    }
                }
            }
        }
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

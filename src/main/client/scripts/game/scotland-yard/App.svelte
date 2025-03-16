<script lang="ts">
    import Icon from '../../Icon.svelte';
    import Map from './Map.svelte';
    import Intersection from './Intersection.svelte';
    import { getData } from '../../routing';
    import { playerType, transport, type Enum, type MapData, type PlayerType, type Point } from './scotland-yard';
    import Connection from './Connection.svelte';
    import Fullscreen from './Fullscreen.svelte';
    import { connectGameChannel } from '../game.svelte';
    import Player from './Player.svelte';

    const ticket = {
        TAXI: transport.TAXI,
        BUS: transport.BUS,
        TRAM: transport.TRAM,
        MULTI: 'multi',
        DOUBLE: 'double',
    } as const;
    type Ticket = Enum<typeof ticket>;

    const ticketNames: { [ticket: string]: string } = {};
    ticketNames[ticket.TAXI] = 'Taxi';
    ticketNames[ticket.BUS] = 'Bus';
    ticketNames[ticket.TRAM] = 'Tram';
    ticketNames[ticket.MULTI] = 'Multi';
    ticketNames[ticket.DOUBLE] = '2x';

    let isFullscreen = $state(false);

    const map: MapData = getData('map');
    const positions: { [type: string]: number } = $state(getData('positions'))

    let showTickets = $state(false);
    let selectedTicket: Ticket | null = $state(null);
    
    const channel = connectGameChannel()

    interface IntersectionPoint {
        id: number;
        position: Point;
        bus: boolean;
        tram: boolean;
    }

    const intersections: IntersectionPoint[] = map.intersections.map((i) => {
        return {
            id: i.id,
            position: i.pos,
            bus: false,
            tram: false,
        };
    });

    const findIntersectionById = (id: number) => intersections.find((i) => i.id === id);

    const connections = map.connections.map((c) => {
        const i1 = findIntersectionById(c.from)!;
        const i2 = findIntersectionById(c.to)!;
        if (c.type === transport.BUS) {
            i1.bus = true;
            i2.bus = true;
        } else if (c.type === transport.TRAM) {
            i1.tram = true;
            i2.tram = true;
        }
        return {
            id: c.id,
            from: i1.position,
            to: i2.position,
            type: c.type,
            shape: c.shape,
        };
    });

    const beginTurn = () => {
        showTickets = true;
    };

    const endTurn = () => {
        showTickets = false;
        selectedTicket = null;
    };

    const selectTicket = (t: Ticket) => {
        selectedTicket = selectedTicket === t ? null : t;
    };
</script>

<Fullscreen bind:isFullscreen>
    <div class="map">
        <Map minZoom={map.minZoom} boundary={map.boundary}>
            {#each connections as c}
                <Connection
                    id={c.id.toString()}
                    from={c.from}
                    to={c.to}
                    width={map.connectionWidth}
                    shape={c.shape}
                    type={c.type}
                ></Connection>
            {/each}
            {#each intersections as i}
                <Intersection
                    id={i.id.toString()}
                    position={i.position}
                    radius={map.intersectionRadius}
                    bus={i.bus}
                    tram={i.tram}
                ></Intersection>
            {/each}
            {#each Object.entries(positions) as [type, id]}
                {@const intersection = findIntersectionById(id)!}
                <Player type={type as PlayerType} position={intersection.position} size={map.intersectionRadius * 4}></Player>
            {/each}
        </Map>
        <div class="tickets" class:enabled={showTickets}>
            {#each Object.values(ticket) as t}
                <button
                    class={t}
                    disabled={!showTickets}
                    class:active={selectedTicket === null || selectedTicket === t}
                    onclick={() => selectTicket(t)}>{ticketNames[t]}</button
                >
            {/each}
        </div>
    </div>
    <div class="actions">
        <div>Mister X</div>
        <div class="spacing"></div>
        <button onclick={() => (showTickets ? endTurn() : beginTurn())}>TEST</button>
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

        .tickets {
            position: absolute;
            display: flex;
            bottom: 0;
            left: 0;
            right: 0;
            margin: 1rem 0;
            justify-content: center;
            gap: 1rem;
            opacity: 0.5;
            z-index: 400;

            &.enabled {
                opacity: 1;
            }

            button {
                width: 5rem;
                padding: 0.7rem 0;
                border: var(--border);
                border-radius: 0.3rem;
                text-align: center;
                text-transform: uppercase;
                filter: saturate(0.5) contrast(0.6);

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

                &.double {
                    background-color: var(--train-color);
                }

                &:not(:disabled) {
                    &:hover {
                        font-weight: 600;
                    }

                    &.active {
                        filter: none;
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

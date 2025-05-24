<script lang="ts">
    import Icon from '../../Icon.svelte';
    import Map from './map/Map.svelte';
    import Intersection from './map/Intersection.svelte';
    import { getData } from '../../routing';
    import '../../lobby.svelte';
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
        teamNames,
        type Team,
        team,
    } from './scotland-yard';
    import Connection from './map/Connection.svelte';
    import Fullscreen from './Fullscreen.svelte';
    import { connectGameChannel } from '../game.svelte';
    import Player from './map/Player.svelte';
    import { isOperator, playerById, you, type PlayerId } from '../../lobby.svelte';
    import { bcs } from '../../bcs';
    import Message from './map/Message.svelte';
    import { onMount } from 'svelte';

    const ticket = {
        TAXI: transport.TAXI,
        BUS: transport.BUS,
        TRAM: transport.TRAM,
        MULTI: 'multi',
    } as const;
    type Ticket = Enum<typeof ticket>;

    const ticketNames: { [_ in Ticket]: string } = {
        [ticket.TAXI]: 'Taxi',
        [ticket.BUS]: 'Bus',
        [ticket.TRAM]: 'Tram',
        [ticket.MULTI]: 'Multi',
    };

    interface Clue {
        position: number;
        ticket: Ticket | null;
        reveal: boolean;
    }

    interface Title {
        misterXTicket?: Ticket;
        misterXRevealed?: number;
        misterXWon?: null;
        detectivesWon?: boolean;
    }

    interface IntersectionData {
        position: Point;
        label: number;
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
            label: i.label,
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

    const toTeam = (r: Role | null): Team =>
        r === null ? team.NONE : r === role.MISTER_X ? team.MISTER_X : team.DETECTIVES;
    const yourTeam = $derived(
        toTeam((Object.entries(roles) as [Role, string | null][]).find(([_, id]) => id === you.id)?.[0] ?? null)
    );

    const tickets: { [_ in Role]: { [_ in Ticket]: number } } = $state(getData('tickets'));
    const infiniteCount = 2147483647;
    const positions: { [_ in Role]: number } = $state(getData('positions'));
    let winner: Team = $state(getData('winner'));
    let round: number = $state(getData('round'));
    const clues: Clue[] = $state(getData('clues'));
    let showClues = $state(false);
    let possibleLocations: number[] = $state(getData('possibleLocations'));

    let title: Title | null = $state(null);

    let turn: Role | null = $state(getData('turn'));
    const yourTurn = $derived(
        winner === team.NONE &&
            turn !== null &&
            (roles[turn] === you.id || (roles[turn] === null && yourTeam === team.DETECTIVES))
    );
    let yourTurnMessage = $state(false);

    let showTickets = $state(false);
    let selectedTicket: Ticket | null = $state(null);

    let availableConnections: number[] | null = $state(getData('availableConnections'));

    const bcsRole = bcs.enumeration(role);
    const bcsTicket = bcs.enumeration(ticket);

    const channel = connectGameChannel();
    const sendTakeConnection = channel.destinationWith(bcs.tuple([bcs.enumeration(ticket), bcs.int]));
    const sendFinish = channel.destination();
    channel.receiverWith(onNextRound, bcs.tuple([bcs.int, bcs.boolean] as const));
    channel.receiverWith(onNextTurn, bcs.nullable(bcsRole));
    channel.receiverWith(onBeginTurn, bcs.list(bcs.int));
    channel.receiverWith(onUseTicket, bcs.tuple([bcsRole, bcsTicket, bcs.int] as const));
    channel.receiverWith(onMove, bcs.tuple([bcsRole, bcs.int] as const));
    channel.receiverWith(onPossibleLocations, bcs.list(bcs.int));
    channel.receiverWith(onReveal, bcs.int);
    channel.receiverWith(onWinner, bcs.tuple([bcs.enumeration(team), bcs.list(bcs.int)] as const));

    const availableTickets: { [_ in Ticket]: boolean } = {
        [ticket.TAXI]: false,
        [ticket.BUS]: false,
        [ticket.TRAM]: false,
        [ticket.MULTI]: true,
    };

    const beginTurn = () => {
        showTickets = true;
        yourTurnMessage = true;

        availableTickets[ticket.TAXI] = false;
        availableTickets[ticket.BUS] = false;
        availableTickets[ticket.TRAM] = false;
        for (const id of availableConnections!) {
            const connection = connections[id];
            switch (connection.type) {
                case transport.TAXI:
                    availableTickets[ticket.TAXI] = true;
                    break;
                case transport.BUS:
                    availableTickets[ticket.BUS] = true;
                    break;
                case transport.TRAM:
                    availableTickets[ticket.TRAM] = true;
                    break;
            }
        }
    };

    const endTurn = () => {
        showTickets = false;
        yourTurnMessage = false;
        selectedTicket = null;
        availableConnections = null;
    };

    const selectTicket = (t: Ticket) => {
        selectedTicket = selectedTicket === t ? null : t;
    };

    const canChooseConnection = $derived(showTickets && selectedTicket != null);

    const isValidTicket = (type: Transport, t: Ticket) =>
        t === ticket.MULTI ||
        (t === ticket.TAXI && type === transport.TAXI) ||
        (t === ticket.BUS && type === transport.BUS) ||
        (t === ticket.TRAM && type === transport.TRAM);

    const chooseConnection = (connection: number) => () => {
        if (selectedTicket === null) return;
        sendTakeConnection([selectedTicket, connection]);
    };

    const chooseIntersection = (id: number) => () => {
        if (availableConnections === null || selectedTicket === null) return;
        if (id === positions[turn!]) return;

        for (const c of availableConnections) {
            const connection = connections[c];
            if (connection.from !== id && connection.to !== id) continue;
            if (!isValidTicket(connection.type, selectedTicket)) continue;

            chooseConnection(c)();
            return;
        }
    };

    let titleTimeout: number | undefined = undefined;
    const showTitle = (t: Title) => {
        title = t;

        clearTimeout(titleTimeout);
        titleTimeout = setTimeout(() => {
            title = null;
            titleTimeout = undefined;
        }, 3000);
    };

    function onNextRound([next, reveal]: [number, boolean]) {
        round = next;
        clues.push({ position: yourTeam === role.MISTER_X ? positions[role.MISTER_X] : -1, ticket: null, reveal });
    }

    function onNextTurn(next: Role | null) {
        if (next === null) endTurn();
        turn = next;
    }

    function onBeginTurn(connections: number[]) {
        if (!yourTurn) return;
        availableConnections = connections;
        beginTurn();
    }

    function onUseTicket([r, t, count]: [Role, Ticket, number]) {
        tickets[r][t] = count;
        if (r !== role.MISTER_X) return;

        clues[round].ticket = t;
        showTitle({ misterXTicket: t });
    }

    function onMove([r, id]: [Role, number]) {
        positions[r] = id;
    }

    function onPossibleLocations(locations: number[]) {
        possibleLocations = locations;
    }

    function onReveal(id: number) {
        positions[role.MISTER_X] = id;
        clues[round].position = id;
        possibleLocations = [id];

        showTitle({ misterXRevealed: id });
    }

    function onWinner([t, cluePositions]: [Team, number[]]) {
        winner = t;
        cluePositions.forEach((position, i) => (clues[i].position = position));

        if (t === team.DETECTIVES) {
            let caught = false;
            for (const [r, id] of Object.entries(positions))
                if (r !== role.MISTER_X && positions[role.MISTER_X] === id) caught = true;

            showTitle({ detectivesWon: caught });
        } else {
            showTitle({ misterXWon: null });
        }
    }

    onMount(() => {
        if (yourTurn && availableConnections !== null) beginTurn();
    });
</script>

<Fullscreen bind:isFullscreen>
    <div class="map">
        <Map minZoom={map.minZoom} boundary={map.boundary}>
            {#each Object.entries(connections) as [id, c] (id)}
                <Connection
                    {id}
                    from={intersections[c.from].position}
                    to={intersections[c.to].position}
                    width={map.connectionWidth}
                    shape={c.shape}
                    type={c.type}
                    disabled={canChooseConnection &&
                        (!isValidTicket(c.type, selectedTicket!) ||
                            (availableConnections !== null && !availableConnections.includes(parseInt(id))))}
                    onclick={canChooseConnection ? chooseConnection(parseInt(id)) : null}
                ></Connection>
            {/each}
            {#each Object.entries(intersections) as [id, i] (id)}
                <Intersection
                    id={id.toString()}
                    position={i.position}
                    label={i.label}
                    radius={map.intersectionRadius}
                    bus={i.bus}
                    tram={i.tram}
                    marker={possibleLocations.includes(parseInt(id)) ? '#000A' : ''}
                    onclick={canChooseConnection ? chooseIntersection(parseInt(id)) : null}
                ></Intersection>
            {/each}
            {#each Object.entries(positions) as [r, id] (r)}
                {#if id !== -1}
                    {@const offset = r !== role.MISTER_X && positions[role.MISTER_X] === id ? 0.2 : 0}
                    <Player
                        role={r as Role}
                        position={intersections[id].position}
                        size={map.intersectionRadius * 16}
                        scaleCompensationStrength={0.8}
                        {offset}
                    />
                {/if}
            {/each}
            <Message
                bind:visible={yourTurnMessage}
                position={yourTurn ? intersections[positions[turn!]].position : { lat: 0.0, lon: 0.0 }}
                content="It's your turn!"
            />
        </Map>
        <div class="overlay float tickets" class:enabled={showTickets}>
            {#each Object.values(ticket) as t}
                {@const count = tickets[turn ?? role.MISTER_X][t]}
                {#if count !== -1}
                    <button
                        class="ticket {t}"
                        disabled={!showTickets || !availableTickets[t] || count === 0}
                        class:active={(selectedTicket === null && availableTickets[t]) || selectedTicket === t}
                        onclick={() => selectTicket(t)}
                    >
                        {ticketNames[t]}
                        <span class="count">
                            {#if count === infiniteCount}
                                &infin;
                            {:else}
                                {count}
                            {/if}
                        </span>
                    </button>
                {/if}
            {/each}
        </div>
        <div class="overlay float info">
            {#if winner === team.NONE}
                <h3>Round {round + 1}</h3>
                {#if turn !== null}
                    <span
                        >{roleNames[turn]}'s turn ({roles[turn] !== null
                            ? playerById(roles[turn]!)!.name
                            : teamNames[team.DETECTIVES]})</span
                    >
                {/if}
            {:else}
                <h3>Game End</h3>
                <span>{teamNames[winner]} won</span>
            {/if}
        </div>
        {#if title !== null}
            <div class="overlay float title">
                {#if title.misterXTicket !== undefined}
                    <h1>Mister X used</h1>
                    <div class="ticket {title.misterXTicket}">{ticketNames[title.misterXTicket]}</div>
                {/if}
                {#if title.misterXRevealed !== undefined}
                    <h1>Mister X revealed</h1>
                    <h3>Seen at {title.misterXRevealed}</h3>
                {/if}
                {#if title.misterXWon !== undefined}
                    <h1>Mister X won</h1>
                    <h3>Reached 3 checkpoints</h3>
                {/if}
                {#if title.detectivesWon !== undefined}
                    <h1>Detective won</h1>
                    <h3>Mister X was {title.detectivesWon ? 'caught' : 'surrounded'}</h3>
                {/if}
            </div>
        {/if}
        <div class="overlay clues" class:visible={showClues}>
            <button onclick={() => (showClues = false)}><Icon id="x-lg" /></button>
            <h3>Clues</h3>
            <div class="table">
                <span>Round</span>
                <span>Position</span>
                <span>Ticket</span>
                <div class="seperator"></div>
                {#each clues as clue, i (i)}
                    <span>{i + 1}</span>
                    <span>{clue.reveal ? clue.position : clue.position === -1 ? '?' : `(${clue.position})`}</span>
                    {#if clue.ticket !== null}
                        <div class="ticket {clue.ticket}">{ticketNames[clue.ticket]}</div>
                    {:else}
                        <div class="ticket">&nbsp;</div>
                    {/if}
                {/each}
            </div>
        </div>
    </div>
    <div class="actions">
        <div>{teamNames[yourTeam]}</div>
        <div class="spacing"></div>
        <button class="stretch" onclick={() => (showClues = !showClues)}>Clues</button>
        <button class="stretch">Powerups</button>
        {#if winner !== team.NONE && isOperator()}
            <button onclick={() => sendFinish()}>Finish</button>
        {/if}
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
            right: 0;
            z-index: 1200;

            &.float {
                left: 0;
                margin: 1rem 0;
                pointer-events: none;
            }
        }

        .info {
            top: 0;
            color: white;
            text-shadow:
                black 0 0 2px,
                black 0 0 4px,
                black 0 0 6px,
                black 0 0 8px;
            text-align: center;
        }

        .tickets {
            display: none;
            bottom: 0;
            justify-content: center;
            gap: 1rem;

            &.enabled {
                display: flex;

                @media (width < 40rem) {
                    &:has(> :last-child:nth-child(4)) {
                        display: grid;
                        grid-template-columns: repeat(2, auto);
                    }
                }
            }

            button {
                position: relative;
                filter: saturate(0.5) contrast(0.6);
                pointer-events: all;

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

                .count {
                    position: absolute;
                    top: 0;
                    right: 0;
                    width: 1.3rem;
                    height: 1.3rem;
                    transform: translate(50%, -50%);
                    background-color: var(--tram-color);
                    border-radius: 50%;
                    line-height: 1.2rem;
                    font-weight: normal;
                }
            }
        }

        .title {
            display: flex;
            flex-direction: column;
            top: 0;
            bottom: 0;
            justify-content: center;
            align-items: center;

            h1,
            h3 {
                -webkit-text-stroke: var(--decoration-thickness) var(--decoration);
            }

            .ticket {
                font-size: 1.5rem;
            }
        }

        .clues {
            display: flex;
            flex-direction: column;
            top: 0;
            bottom: 0;
            width: min(20rem, 100%);
            backdrop-filter: brightness(0.6) blur(4px);
            transform: translateX(100%);
            transition: 200ms all ease-out;

            &.visible {
                transform: translateX(0);
            }

            button {
                align-self: end;
                margin: 0.7rem;
                padding: 0.7rem;
                background: none;
                border: none;
                z-index: 1;

                &:hover {
                    background-color: rgba(0, 0, 0, 0.4);
                }
            }

            h3 {
                margin-top: -2rem;
                text-align: center;
            }

            .table {
                display: grid;
                margin: 2rem;
                grid-template-columns: 1fr 1.5fr 1fr;
                gap: 0.3rem;
                justify-items: center;
                align-items: center;
                overflow-y: scroll;

                .seperator {
                    grid-column: 1 / 4;
                    width: 100%;
                    height: var(--decoration-thickness);
                    background-color: var(--muted);
                }
            }
        }
    }

    .ticket {
        width: 5em;
        padding: 0.7em 0;
        border: var(--border);
        border-radius: 0.3em;
        text-align: center;
        text-transform: uppercase;

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
    }

    .actions {
        display: flex;
        height: 3rem;
        container-type: inline-size;

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

        @container (width < 30rem) {
            > * {
                padding: 0 0.7rem;
            }

            .stretch {
                flex-grow: 1;
                justify-content: center;
            }

            .spacing {
                display: none;
            }
        }
    }
</style>

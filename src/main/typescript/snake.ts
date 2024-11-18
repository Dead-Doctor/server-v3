import {sendAddressed, socket} from './ws.js'

const container = document.querySelector('section')!
const lobbyMenu = document.querySelector('.lobby')!
const lobbyPlayersContainer = document.querySelector('.lobby .players')!
const joinBtn: HTMLButtonElement = document.querySelector('#joinBtn')!
const startBtn: HTMLButtonElement = document.querySelector('#startBtn')!
const winnerMenu = document.querySelector('.winner')!
const winnerPlayerContainer = document.querySelector('.winner .players')!
const closeWinnerBtn: HTMLButtonElement = document.querySelector('#closeWinnerBtn')!
const canvas = document.querySelector('canvas')!
const ctx = canvas.getContext('2d')!

interface PacketTypeMap {
    'gameState': GameState
    'updateYou': PlayerId | 'alreadyLoggedIn'
    'updatePlayers': PlayerInfo[]
    'updateSnakes': Snake[]
}

interface Packet<K extends keyof PacketTypeMap> {
    type: K
    data: PacketTypeMap[K]
}

enum GameState {
    LOBBY,
    START,
    RUNNING,
    WINNER
}

type PlayerId = string

interface PlayerInfo {
    id: PlayerId
    name: string
    avatar: string
    playing: boolean
}

type Vec = [number, number]

interface Snake {
    segments: Vec[]
    width: number
    color: string
    dead: boolean
    player: PlayerId
}

const speed = 0.5
const maximumTurningRadius = 0.03
const headSizeIncrease = 1.6
const deadColor = "#676767"

export let currentState = GameState.LOBBY
export let you: PlayerId | null = null
export let playing: boolean | null = null
export let players: PlayerInfo[]

export let profileImages: {
    [id: string]: HTMLImageElement
}

let size: number
let canvasRect: DOMRect

let lastTime: DOMHighResTimeStamp | null = null
export let ownSnake: Snake | null = null
export let otherSnakes: Snake[] = []

let snakeLength: number
let aimPos: Vec = [0.5, 999]

window.addEventListener('mousemove', (e) => {
    const x = (e.clientX - canvasRect.left) / canvas.width
    const y = (e.clientY - canvasRect.top) / canvas.width
    // if (0 <= x && x <= canvas.width / size && 0 <= y && y <= canvas.height / size)
    aimPos = [x, y]
})

window.addEventListener('touchmove', (e) => {
    e.preventDefault()
    let biggestIdentifier = -1
    let latestTouch: Touch
    for (const touch of e.touches) {
        if (touch.identifier <= biggestIdentifier) continue
        biggestIdentifier = touch.identifier
        latestTouch = touch
    }
    const x = (latestTouch!.clientX - canvasRect.left) / canvas.width
    const y = (latestTouch!.clientY - canvasRect.top) / canvas.width
    aimPos = [x, y]
})

const isPacket = <T extends keyof PacketTypeMap>(packet: Packet<any>, type: T): packet is Packet<T> => packet.type === type
socket.addEventListener('message', (e: MessageEvent<string>) => {
    let packet: Packet<keyof PacketTypeMap> = JSON.parse(e.data)
    console.log(`Received packet of type: ${packet.type}`)
    if (isPacket(packet, 'gameState')) {
        updateGameState(packet.data)
    } else if (isPacket(packet, 'updateYou')) {
        if (packet.data == 'alreadyLoggedIn') {
            alert('You are already logged in!')
            window.location.pathname = '/'
        }
        you = packet.data
        joinBtn.disabled = false
    } else if (isPacket(packet, 'updatePlayers')) {
        updatePlayers(packet.data)
    } else if (isPacket(packet, 'updateSnakes')) {
        updateSnakes(packet.data)
    }
})

const updateGameState = (nextState: GameState) => {
    console.log(`Update GameState (${GameState[nextState]})`)
    if (nextState != GameState.LOBBY) {
        lobbyMenu.classList.remove('show')
    }
    if (nextState == GameState.START) {
        //TODO: show countdown
    }
    if (nextState == GameState.RUNNING) {
        startGame()
    }
    if (nextState == GameState.WINNER) {
        winnerMenu.classList.add('show')
        winnerPlayerContainer.replaceChildren()
        if (playing) closeWinnerBtn.disabled = false
    }
    if (nextState != GameState.WINNER) {
        winnerMenu.classList.remove('show')
    }
    if (nextState == GameState.LOBBY) {
        lobbyMenu.classList.add('show')
    }
    currentState = nextState
}

const updatePlayers = (playerInfos: PlayerInfo[]) => {
    console.log(`Update players (${playerInfos.length})`)
    players = playerInfos
    if (you) {
        const youInfo = players.find(p => p.id == you)!
        if (youInfo.playing != playing) {
            playing = youInfo.playing
            joinBtn.innerText = playing ? 'Leave' : 'Join'
            startBtn.disabled = !playing
        }
    }

    if (currentState == GameState.LOBBY) {
        profileImages = {}
        const images = players.map((player, _, __) => {
            if (player.playing) {
                const imageData = new Image()
                imageData.src = player.avatar
                profileImages[player.id] = imageData
            }
            const image = document.createElement('img')
            image.src = player.avatar
            image.alt = `Avatar of ${player.name}`
            image.classList.toggle('not-joined', !player.playing)
            return image
        })
        lobbyPlayersContainer.replaceChildren(...images)
        console.log(profileImages)
    }
}

const updateSnakes = (snakes: Snake[]) => {
    otherSnakes = []
    if (snakes != null) {
        for (const snake of snakes) {
            if (snake.player != you) {
                otherSnakes.push(snake)
            } else if ((ownSnake == null && currentState == GameState.START) || currentState == GameState.WINNER) {
                ownSnake = snake
                const head = snake.segments[0]
                const tail = snake.segments[snake.segments.length - 1]
                snakeLength = getMagnitude(subVec(head, tail))
            }
        }
    } else {
        ownSnake = null
    }
    if (currentState != GameState.RUNNING) redraw()
    if (currentState == GameState.WINNER) showWinner()
}

const startGame = () => {
    lastTime = null
    requestAnimationFrame(update)
}

const showWinner = () => {
    let winner = ownSnake?.dead == false ? ownSnake.player : null
    for (const snake of otherSnakes) {
        if (!snake.dead) winner = snake.player
    }
    if (winner != null) {
        const player = players.find(player => player.id == winner)!
        const image = document.createElement('img')
        image.src = player.avatar
        image.alt = `Avatar of ${player.name}`
        winnerPlayerContainer.replaceChildren(image)
    }
}

joinBtn.addEventListener('click', () => {
    if (you)
        sendAddressed('join', !playing)
})

startBtn.addEventListener('click', () => {
    if (playing)
        sendAddressed('start')
})

closeWinnerBtn.addEventListener('click', () => {
    if (playing)
        sendAddressed('reset')
})

const update = (time: DOMHighResTimeStamp) => {
    const delta = lastTime ? (time - lastTime) / 1_000 : null
    lastTime = time

    if (currentState == GameState.RUNNING) {
        requestAnimationFrame(update)

        if (delta != null && ownSnake && !ownSnake.dead) {
            const head = ownSnake.segments[0]
            // Make sure the move distance is never bigger than the turning radius
            const moveDistance = Math.min(speed * delta, maximumTurningRadius)

            snakeLength *= 1.0 + 0.1 * delta

            const lastDirection = angleOfVec(subVec(head, ownSnake.segments[1]))
            const direction = angleOfVec(subVec(aimPos, head))

            const directionChange = trueMod(direction - lastDirection + Math.PI, 2.0 * Math.PI) - Math.PI
            const angleChangeLimit = Math.asin(moveDistance / 2.0 / maximumTurningRadius)
            const limitedDirectionChange = clamp(-angleChangeLimit, directionChange, angleChangeLimit)

            const nextDirection = angleToVec(lastDirection + limitedDirectionChange)
            const nextPosition = addVec(head, scaleVec(nextDirection, moveDistance))

            let headRadius = ownSnake.width * headSizeIncrease / 2;
            if (nextPosition[0] - headRadius < 0.0
                || nextPosition[0] + headRadius > 1.0
                || nextPosition[1] - headRadius < 0.0
                || nextPosition[1] + headRadius > canvas.height / canvas.width) {
                fail()
                return
            }

            for (const snake of otherSnakes) {
                if (snake.dead) continue
                for (const segment of snake.segments) {
                    const distance = getMagnitude(subVec(segment, nextPosition))
                    if (distance < headRadius + (snake.width / 2)) {
                        fail()
                        return
                    }
                }
            }
            ownSnake.segments.unshift(nextPosition)

            let currentLength = 0
            let removeNSegments = 0
            for (let i = 1; i < ownSnake.segments.length; i++) {
                if (currentLength >= snakeLength) {
                    removeNSegments++
                } else {
                    const segmentStart = ownSnake.segments[i - 1]
                    const segmentEnd = ownSnake.segments[i]
                    currentLength += getMagnitude(subVec(segmentEnd, segmentStart))

                    //TODO: Better way to check self collision
                    if (currentLength > (ownSnake.width / 2) * (headSizeIncrease + 2.5)) {
                        const distance = getMagnitude(subVec(segmentEnd, nextPosition))
                        if (distance < headRadius + (ownSnake.width / 2)) {
                            fail()
                            return
                        }
                    }
                }
            }
            for (let i = 0; i < removeNSegments; i++) {
                ownSnake.segments.pop()
            }

            //TODO: maybe only do every other tick or so
            uploadSnake()
        }
        redraw()
    }
}

const fail = () => {
    ownSnake!.dead = true
    redraw()
    sendAddressed("fail")
}

const uploadSnake = () => {
    sendAddressed('snake', ownSnake)
}

const resizeCanvas = () => {
    canvas.width = container.clientWidth
    canvas.height = container.clientHeight
    canvasRect = canvas.getBoundingClientRect()
    size = canvas.width
    redraw()
}

const redraw = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    if (ownSnake)
        drawSnake(ownSnake)
    for (const snake of otherSnakes) {
        drawSnake(snake)
    }
}

const drawSnake = (snake: Snake) => {
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.strokeStyle = snake.dead ? deadColor : snake.color

    const points = snake.segments
    ctx.beginPath()
    ctx.moveTo(points[0][0] * size, points[0][1] * size)
    for (let i = 1; i < points.length; i++) {
        ctx.lineTo(points[i][0] * size, points[i][1] * size)
    }
    ctx.lineWidth = snake.width * size
    ctx.stroke()

    ctx.save()
    const headSize = snake.width * size * headSizeIncrease
    const headX = points[0][0] * size
    const headY = points[0][1] * size
    const rotation = angleOfVec(subVec(points[0], points[1])) + Math.PI / 2
    ctx.translate(headX, headY)
    ctx.rotate(rotation)
    ctx.beginPath()
    ctx.arc(0, 0, (headSize / 2), 0, Math.PI * 2)
    ctx.clip()
    if (snake.dead) {
        ctx.filter = 'grayscale(100%)'
    }
    ctx.drawImage(profileImages[snake.player], -headSize / 2, -headSize / 2, headSize, headSize)
    ctx.restore()
}

// const drawDebugLine = (color: string, a: number[], b: number[]) => {
//     ctx.beginPath()
//     ctx.moveTo(a[0] * size, a[1] * size)
//     ctx.lineTo(b[0] * size, b[1] * size)
//     ctx.lineWidth = 1
//     ctx.lineCap = 'butt'
//     ctx.strokeStyle = color
//     ctx.stroke()
// }

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(min, value), max)
const trueMod = (value: number, mod: number) => (value % mod + mod) % mod

const scaleVec = (a: Vec, s: number): Vec => [a[0] * s, a[1] * s]
const addVec = (a: Vec, b: Vec): Vec => [a[0] + b[0], a[1] + b[1]]
const subVec = (a: Vec, b: Vec): Vec => [a[0] - b[0], a[1] - b[1]]
const getMagnitude = (a: Vec) => Math.sqrt(a[0] * a[0] + a[1] * a[1])
// const normalizeVec = (a: Vec): Vec => scaleVec(a, 1.0 / getMagnitude(a))
const angleOfVec = (a: Vec) => Math.atan2(a[1], a[0])
const angleToVec = (d: number): Vec => [Math.cos(d), Math.sin(d)]

window.addEventListener('resize', resizeCanvas)
joinBtn.disabled = true
startBtn.disabled = true
resizeCanvas()

<script lang="ts">
    import {getData} from "../routing";
    import {openSocket} from '../ws'
    import Leaderboard from "../Leaderboard.svelte";
    import {fade, fly, slide} from 'svelte/transition'
    import {sineInOut} from 'svelte/easing'

    interface PacketTypeMap {
        checkedName: string[]
        join: PlayerId
        playerJoined: Player
        playerActiveChanged: { player: PlayerId, active: boolean }
        hostChanged: PlayerId
        kicked: string
    }

    type PlayerId = string

    interface Player {
        id: PlayerId
        name: string
        verified: boolean
        avatar: string | null
        active: boolean
        score: number
    }
</script>

<section>
    <h1>Lobby</h1>
</section>

<style>

</style>
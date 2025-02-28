<script lang="ts">
    import { getData } from "../../../routing";

    interface MapInfo {
        id: string,
        name: string
    }

    const maps: MapInfo[] = getData('maps')
</script>

<h1>Load Map</h1>

<section class="grid">
    {#each maps as map}
        <div>
            <h3>{map.name}</h3>
            <p>Authors: ???</p>
            <a href="/game/scotland-yard/editor/{map.id}">Edit</a>
        </div>
    {/each}
</section>
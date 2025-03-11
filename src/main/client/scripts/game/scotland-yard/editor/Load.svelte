<script lang="ts">
    import { getData } from "../../../routing";
    import type { MapInfo } from "../scotland-yard";

    const maps: MapInfo[] = getData('maps')
</script>

<h1>Load Map</h1>

<section class="grid">
    {#each maps as map}
        <div>
            <h3>{map.name}</h3>
            <p>Version: {map.version}</p>
            <a href="/game/scotland-yard/editor/{map.id}">Edit</a>
        </div>
    {/each}
</section>
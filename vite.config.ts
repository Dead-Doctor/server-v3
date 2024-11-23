import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

const entries = ['wsTest.ts', 'chat.ts', 'snake.ts', 'music-guesser.ts']

// https://vite.dev/config/
export default defineConfig({
    plugins: [svelte()],
    root: 'src/main/client',
    publicDir: 'public',
    build: {
        manifest: true,
        outDir: './../resources/dist',
        emptyOutDir: true,
        rollupOptions: {
            input: entries.map(entry => `src/main/client/scripts/${entry}`)
        }
    },
    esbuild: {
        supported: {
            'top-level-await': true
        }
    }
})

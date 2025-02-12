import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

const entries = ['wsTest', 'chat', 'snake', 'lobby/main', 'quiz/main', 'game/music-guesser/main']

// https://vite.dev/config/
export default defineConfig({
    plugins: [svelte()],
    root: 'src/main/client',
    publicDir: 'public',
    build: {
        target: ['es2020', 'edge88', 'firefox78', 'chrome88', 'safari14'],
        manifest: true,
        outDir: './../resources/dist',
        emptyOutDir: true,
        rollupOptions: {
            input: entries.map(entry => `src/main/client/scripts/${entry}.ts`)
        }
    },
    esbuild: {
        supported: {
            'top-level-await': true
        }
    }
})

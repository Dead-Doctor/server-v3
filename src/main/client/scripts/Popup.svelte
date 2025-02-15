<script lang="ts">
    import { fade, fly, slide } from "svelte/transition";

    interface Props {
        visible: boolean
        message: string;
        closable?: boolean;
        buttonText?: string;
        buttonDisabled?: boolean;
        buttonAction?: () => void;
        input?: boolean
        inputPlaceholder?: string
        inputValue?: string
        inputAction?: () => void
        inputErrors?: string[]
        login?: boolean
    }

    let {
        visible = $bindable(),
        message,
        closable = true,
        buttonText = '',
        buttonDisabled = false,
        buttonAction = () => {},
        input = false,
        inputPlaceholder = '',
        inputValue = $bindable(''),
        inputAction = () => {},
        inputErrors = [],
        login = false
    }: Props = $props()
</script>

{#if visible}
    <div class="overlay" in:fade={{ duration: 200 }} out:fade={{ delay: 300, duration: 200 }}>
        <div class="popup" in:fly={{ delay: 200, duration: 300, y: 200 }} out:fly={{ duration: 300, y: 200 }}>
            <h3>{message}</h3>
            {#if input}
                <input
                    class="textInput"
                    class:error={inputErrors.length !== 0}
                    type="text"
                    name="textInput"
                    id="textInput"
                    placeholder={inputPlaceholder}
                    size="20"
                    maxlength="20"
                    bind:value={inputValue}
                    oninput={() => inputAction()}
                />
                <div>
                    {#each inputErrors as error (error)}
                        <p class="error" transition:slide>{@html error}</p>
                    {/each}
                    <!-- suppress css warning from samp styling -->
                    <span class="error" style="display: none"><samp></samp></span>
                </div>
            {/if}
            {#if login}
                <span class="login"
                    >Or <a href={`/login?redirectUrl=${encodeURIComponent(location.pathname)}`}>Login</a></span
                >
            {/if}
            <div class="actions">
                <button onclick={() => buttonAction()} disabled={buttonDisabled}
                    >{buttonText}</button
                >
                {#if closable}
                    <button onclick={() => (visible = false)}>Close</button>
                {/if}
            </div>
        </div>
    </div>
{/if}

<style>
    :global(body:has(.overlay)) {
        overflow: hidden;
    }

    .overlay {
        display: flex;
        position: fixed;
        inset: 0;
        justify-content: center;
        align-items: center;
        backdrop-filter: brightness(80%) blur(4px);
        z-index: 9999;

        .popup {
            display: flex;
            flex-direction: column;
            padding: 1.5rem;
            gap: 1rem;
            background-color: var(--background);
            border: var(--border);
            border-radius: 1rem;

            .textInput {
                width: 35rem;
                font-size: 1.5em;
                font-weight: bold;
                outline: var(--decoration-thickness) solid transparent;
                transition: 150ms outline-color ease-in-out;

                &.error {
                    outline-color: var(--primary);
                }
            }

            div .error {
                width: 35rem;
                padding: 0.4em;
                background-color: var(--secondary);

                :global(samp) {
                    color: var(--primary);
                    font-size: 1.2em;
                    font-weight: bold;
                }
            }

            .login {
                align-self: center;
                margin-bottom: -3rem;
                color: var(--muted);
            }

            .actions {
                align-self: end;
            }
        }
    }
</style>
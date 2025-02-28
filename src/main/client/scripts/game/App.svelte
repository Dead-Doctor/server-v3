<script lang="ts">
    import { bcs } from '@iota/bcs';
    import { connectChannel } from '../channel';
    import type { GameType } from '../lobby.svelte';
    import { getData } from '../routing';

    const gameTypes: GameType[] = getData('gameTypes');
    const otherGames: { [name: string]: string } = getData('otherGames');

    const channel = connectChannel();
    const sendCode = channel.destinationWith(bcs.string());

    channel.receiver(onInvalid);
    channel.receiver(onSuccess);

    const codeLength = 4;
    let codeContainer: HTMLElement;
    let codeInputs: (HTMLInputElement | null)[] = Array(codeLength).fill(null);
    let codeChars: string[] = $state(Array(codeLength).fill(''));
    let code = $derived(codeChars.join(''));

    const selectCodeInput = (
        e: FocusEvent & {
            currentTarget: EventTarget & HTMLInputElement;
        }
    ) => {
        e.currentTarget.select();
    };

    const changeCodeInput = (i: number) => (e: Event & { currentTarget: EventTarget & HTMLInputElement }) => {
        const value = e.currentTarget.value.toUpperCase().replace(/[^A-Z]/, '');
        codeChars[i] = value;

        if (value.length > 0) {
            const next = e.currentTarget.nextElementSibling as HTMLInputElement | null;
            next && next.focus();
        }
    };

    const onKeyUp = (e: KeyboardEvent & { currentTarget: EventTarget & HTMLInputElement }) => {
        if (e.code === 'Backspace') {
            const previous = e.currentTarget.previousElementSibling as HTMLInputElement | null;
            previous?.focus();
        }
    };

    const onPaste = (i: number) => (e: ClipboardEvent & { currentTarget: EventTarget & HTMLInputElement }) => {
        const text = e.clipboardData?.getData('text');
        if (text === undefined) return;
        const filtered = text.toUpperCase().replace(/[^A-Z]/g, '');
        if (filtered.length !== codeLength) return;

        e.preventDefault();
        codeChars = filtered.split('');
    };

    $effect(() => {
        if (code.length === codeLength) {
            sendCode(code);
        }
    });

    function onInvalid() {
        codeContainer.animate(
            {
                transform: [
                    'translateX(0px)',
                    'translateX(-1px)',
                    'translateX(2px)',
                    'translateX(-4px)',
                    'translateX(4px)',
                    'translateX(-4px)',
                    'translateX(4px)',
                    'translateX(-4px)',
                    'translateX(2px)',
                    'translateX(-1px)',
                    'translateX(0px)',
                ],
            },
            {
                duration: 820,
                easing: 'cubic-bezier(.36,.07,.19,.97)',
                fill: 'both',
            }
        );
        codeInputs[0]?.focus();
        codeChars.fill('');
    }

    function onSuccess() {
        location.pathname = `/lobby/${code}`;
    }
</script>

<section class="title">
    <h1>Games</h1>
    <div class="join">
        <h5>Join <b>lobby</b> with code?</h5>
        <div class="code" bind:this={codeContainer}>
            {#each codeChars as _, i}
                <input
                    type="text"
                    maxlength="1"
                    bind:this={codeInputs[i]}
                    bind:value={codeChars[i]}
                    oninput={changeCodeInput(i)}
                    onkeyup={onKeyUp}
                    onfocus={selectCodeInput}
                    onpaste={onPaste(i)}
                />
            {/each}
        </div>
    </div>
</section>
<section class="grid">
    {#each gameTypes as type}
        <div>
            <h3>{type.name}</h3>
            <p>{type.description}</p>
            <div class="actions">
                <a href="/lobby/new?game={type.id}">Create Lobby</a>
                {#if type.links !== null}
                    {#each Object.entries(type.links) as [name, href]}
                        <a {href}>{name}</a>
                    {/each}
                {/if}
            </div>
        </div>
    {/each}
</section>
<section class="grid">
    {#each Object.entries(otherGames) as [name, href]}
        <a {href}>{name}</a>
    {/each}
</section>

<style>
    .title {
        display: flex;
        justify-content: space-between;
        align-items: end;
        border-bottom: var(--border);

        .join {
            display: flex;
            justify-content: space-evenly;
            align-items: center;
            padding: 1.2rem 0.5rem;
            gap: 2rem;

            h5 {
                margin: 0;
            }

            .code {
                display: flex;
                gap: 0.5rem;

                input {
                    font-size: 1.2rem;
                    width: 2.2rem;
                    height: 2.5rem;
                    text-align: center;
                }
            }
        }
    }

    .actions {
        display: flex;
        gap: 1rem;
    }
</style>

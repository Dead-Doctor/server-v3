import { expect, test } from 'vitest';
import { bcs } from '../scripts/bcs';

const toArray = (buffer: ArrayBufferLike) => Array.from(new Uint8Array(buffer));
const fromArray = (array: number[]) => new Uint8Array(array).buffer;

test('boolean serialization', () => {
    const original = true;
    const data = toArray(bcs.boolean.serialize(original));
    expect(data).toStrictEqual([0x01]);
});

test('boolean deserialization', () => {
    const original = fromArray([0x01]);
    const data = bcs.boolean.deserialize(original);
    expect(data).toStrictEqual(true);
});

test('integer serialization', () => {
    const original = -305419896;
    const data = toArray(bcs.int.serialize(original));
    expect(data).toStrictEqual([0x88, 0xa9, 0xcb, 0xed]);
});

test('integer deserialization', () => {
    const original = fromArray([0x88, 0xa9, 0xcb, 0xed]);
    const data = bcs.int.deserialize(original);
    expect(data).toStrictEqual(-305419896);
});

test('string serialization', () => {
    const original = 'test';
    const data = toArray(bcs.string.serialize(original));
    expect(data).toStrictEqual([4, 116, 101, 115, 116]);
});

test('string deserialization', () => {
    const original = fromArray([4, 116, 101, 115, 116]);
    const data = bcs.string.deserialize(original);
    expect(data).toStrictEqual('test');
});

test('tuple serialization', () => {
    const original = [true, 'test'];
    const data = toArray(bcs.tuple([bcs.boolean, bcs.string]).serialize(original));
    expect(data).toStrictEqual([1, 4, 116, 101, 115, 116]);
});

test('tuple deserialization', () => {
    const original = fromArray([1, 4, 116, 101, 115, 116]);
    const data = bcs.tuple([bcs.boolean, bcs.string]).deserialize(original);
    expect(data).toStrictEqual([true, 'test']);
});

test('structure serialization', () => {
    const original = {
        id: 'ccf3332d-5b05-4a3c-a99f-e66b41bf490f',
        name: 'test?',
        verified: false,
        avatar: null,
        active: true,
        score: 0,
    };
    const data = toArray(
        bcs
            .struct({
                id: bcs.string,
                name: bcs.string,
                verified: bcs.boolean,
                avatar: bcs.nullable(bcs.string),
                active: bcs.boolean,
                score: bcs.int,
            })
            .serialize(original)
    );
    expect(data).toStrictEqual([
        36, 99, 99, 102, 51, 51, 51, 50, 100, 45, 53, 98, 48, 53, 45, 52, 97, 51, 99, 45, 97, 57, 57, 102, 45, 101, 54,
        54, 98, 52, 49, 98, 102, 52, 57, 48, 102, 5, 116, 101, 115, 116, 63, 0, 0, 1, 0, 0, 0, 0,
    ]);
});

test('structure deserialization', () => {
    const original = fromArray([
        36, 99, 99, 102, 51, 51, 51, 50, 100, 45, 53, 98, 48, 53, 45, 52, 97, 51, 99, 45, 97, 57, 57, 102, 45, 101, 54,
        54, 98, 52, 49, 98, 102, 52, 57, 48, 102, 5, 116, 101, 115, 116, 63, 0, 0, 1, 0, 0, 0, 0,
    ]);
    const data = bcs
        .struct({
            id: bcs.string,
            name: bcs.string,
            verified: bcs.boolean,
            avatar: bcs.nullable(bcs.string),
            active: bcs.boolean,
            score: bcs.int,
        })
        .deserialize(original);
    expect(data).toStrictEqual({
        id: 'ccf3332d-5b05-4a3c-a99f-e66b41bf490f',
        name: 'test?',
        verified: false,
        avatar: null,
        active: true,
        score: 0,
    });
});

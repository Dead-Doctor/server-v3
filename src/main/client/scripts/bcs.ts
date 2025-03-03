export interface BcsType<T> {
    size(value: T): number;
    put(view: DataView, i: number, value: T): number;
    take(view: DataView, i: number): [number, T];
}

export interface Bcs<T> extends BcsType<T> {
    serialize(value: T): ArrayBufferLike;
    deserialize(buffer: ArrayBufferLike): T;
}

const bcsType = <T>(type: BcsType<T>): Bcs<T> => ({
    ...type,
    serialize(value) {
        const size = type.size(value)
        const buffer = new ArrayBuffer(size);
        const view = new DataView(buffer);
        const length = type.put(view, 0, value);
        if (length != size) throw Error('Serialized byte array was not as long as expected.')
        return buffer;
    },
    deserialize(buffer) {
        const view = new DataView(buffer);
        const [length, value] = type.take(view, 0);
        if (length != buffer.byteLength) throw Error('Deserialization did not use the expected number of bytes.')
        return value;
    },
});

/**
 * Unsigned-LEB128 encoded number ([Wiki](https://en.wikipedia.org/wiki/LEB128))
 */
const uleb128 = bcsType<number>({
    size: (value) => {
        let i = 0;
        do {
            value >>= 7;
            i++;
        } while (value > 0);
        return i;
    },
    put: (view, start, value) => {
        let i = 0;
        do {
            let byte = value & 127;
            if ((value >>= 7)) byte |= 128;
            view.setUint8(start + i++, byte);
        } while (value > 0);
        return i;
    },
    take: (view, start) => {
        let i = 0
        let value = 0;
        let shift = 0;
        while (true) {
            let byte = view.getUint8(start + i++);
            value |= (byte & 127) << shift;
            if (byte & 128) break;
            shift += 7;
        }
        return [i, value];
    },
});

const FALSE = 0;
const TRUE = 1;

const boolean = bcsType<boolean>({
    size: () => 1,
    put: (view, i, value) => {
        view.setUint8(i, value ? TRUE : FALSE);
        return 1;
    },
    take: (view, i) => [1, view.getUint8(i) === TRUE],
});

const int = bcsType<number>({
    size: () => 4,
    put: (view, i, value) => {
        view.setInt32(i, value, true);
        return 4;
    },
    take: (view, i) => [4, view.getInt32(i, true)],
});

const long = bcsType<bigint>({
    size: () => 8,
    put: (view, i, value) => {
        view.setBigInt64(i, value, true);
        return 8;
    },
    take: (view, i) => [8, view.getBigInt64(i, true)],
});

const float = bcsType<number>({
    size: () => 4,
    put: (view, i, value) => {
        view.setFloat32(i, value, true);
        return 4;
    },
    take: (view, i) => [4, view.getFloat32(i, true)],
});

const double = bcsType<number>({
    size: () => 8,
    put: (view, i, value) => {
        view.setFloat64(i, value, true);
        return 8;
    },
    take: (view, i) => [8, view.getFloat64(i, true)],
});

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();
const string = bcsType<string>({
    size: (value) => uleb128.size(value.length) + textEncoder.encode(value).length,
    put: (view, start, value) => {
        const array = new Uint8Array(view.buffer, start);
        let i = uleb128.put(view, start, value.length);
        const encodeded = textEncoder.encode(value);
        array.set(encodeded, i);
        return i + encodeded.length;
    },
    take: (view, start) => {
        const [i, length] = uleb128.take(view, start);
        const array = new Uint8Array(view.buffer, start + i, length);
        return [i + length, textDecoder.decode(array)];
    },
});

const nullable = <T>(type: BcsType<T>) =>
    bcsType<T | null>({
        size: (value) => (value !== null ? 1 + type.size(value) : 1),
        put: (view, i, value) =>
            value !== null ? boolean.put(view, i, true) + type.put(view, i + 1, value) : boolean.put(view, i, false),
        take: (view, i) => boolean.take(view, i) ? (() => {
            const [length, value] = type.take(view, i + 1)
            return [1 + length, value]
        })() : [1, null],
    });

const list = <T>(type: BcsType<T>) =>
    bcsType<T[]>({
        size: (values) => values.reduce((n, value) => n + type.size(value), 0),
        put: (view, start, values) =>
            uleb128.put(view, start, values.length) +
            values.reduce((i, value) => i + type.put(view, start + 1 + i, value), 0),
        take: (view, start) => {
            let [i, count] = uleb128.take(view, start)
            const array = Array.from(Array(count), () => {
                const [length, value] = type.take(view, start + i)
                i += length
                return value
            })
            return [i, array]
        },
    });

const map = null

type Result<T extends BcsType<any>> = T extends BcsType<infer U> ? U : never

const tuple = <T extends BcsType<any>[]>(types: T) => bcsType<{[I in keyof T]: Result<T[I]>}>({
    size: (values) => types.reduce((n, type, i) => n + type.size(values[i]), 0),
    put: (view, start, values) => types.reduce((n, type, i) => n + type.put(view, start + n, values[i]), 0),
    take: (view, start) => {
        let i = 0
        const values = types.map((type, i) => {
            const [n, value] = type.take(view, start + i)
            i += n
            return value
        }) as {[I in keyof T]: Result<T[I]>}
        return [i, values]
    }
})

const struct = <T extends { [name: string]: BcsType<any> }>(object: T) =>
    bcsType<{ [K in keyof T]: Result<T[K]>}>({
        size: (value) => Object.entries(object).reduce((total, [key, element]) => total + element.size(value[key]), 0),
        put: (view, start, value) =>
            Object.entries(object).reduce((i, [key, element]) => i + element.put(view, start + i, value[key]), 0),
        take: (view, i) => {
            const entries = Object.entries(object).map(([key, element]) => {
                const value = element.take(view, i);
                i += element.size(value);
                return [key, value];
            });
            return Object.fromEntries(entries);
        },
    });

export const bcs = {
    uleb128,
    boolean,
    int,
    long,
    float,
    double,
    string,
    nullable,
    list,
    map,
    tuple,
    struct,
};

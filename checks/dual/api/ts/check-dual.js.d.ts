type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare namespace example {
    function addOne(x: number): number;
    function d2f(f: number): number;
    function s2b(s: string): boolean;
    function dummy(): void;
}
export declare namespace example {
    class ExampleDataClass {
        constructor(value: number);
        get value(): number;
        copy(value?: number): example.ExampleDataClass;
        toString(): string;
        hashCode(): number;
        equals(other: Nullable<any>): boolean;
    }
    namespace ExampleDataClass {
        /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
        namespace $metadata$ {
            const constructor: abstract new () => ExampleDataClass;
        }
    }
}
export as namespace check_dual;

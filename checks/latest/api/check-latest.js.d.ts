type Nullable<T> = T | null | undefined
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
}
export as namespace check_latest;

type Nullable<T> = T | null | undefined
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

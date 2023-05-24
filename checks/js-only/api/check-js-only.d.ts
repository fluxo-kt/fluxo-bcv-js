type Nullable<T> = T | null | undefined
declare const __doNotImplementIt: unique symbol
type __doNotImplementIt = typeof __doNotImplementIt
export namespace example {
    class ExampleDataClass {
        constructor(value: number);
        get value(): number;
        component1(): number;
        copy(value: number): example.ExampleDataClass;
        toString(): string;
        hashCode(): number;
        equals(other: Nullable<any>): boolean;
    }
}
export as namespace check_js_only;

type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare function addOne(x: number): number;
export declare function d2f(f: number): number;
export declare function s2b(s: string): boolean;
export declare function dummy(): void;

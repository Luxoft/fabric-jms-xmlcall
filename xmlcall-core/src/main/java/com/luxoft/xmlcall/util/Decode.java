package com.luxoft.xmlcall.util;

import java.util.function.Supplier;

public class Decode<T,R>
{
    private final T value;
    private R result = null;

    public Decode(T value)
    {
        this.value = value;
    }

    public Decode<T,R> when(T value, R result)
    {
        if (this.result == null && this.value.equals(value))
            this.result = result;
        return this;
    }

    public R orElse(R result)
    {
        return this.result == null ? result : this.result;
    }

    public <E extends Throwable> R orThrow(Supplier<E> t) throws E
    {
        if (this.result != null)
            return this.result;

        throw t.get();
    }
}

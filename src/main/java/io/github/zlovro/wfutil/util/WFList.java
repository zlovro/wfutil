package io.github.zlovro.wfutil.util;

import io.github.zlovro.wfutil.WFMod;

import java.util.ArrayList;
import java.util.Arrays;

public class WFList<T> extends ArrayList<T> {
    @SafeVarargs
    public WFList(T... var) {
        this.addAll(Arrays.asList(var));
    }

    public T getRandom() {
        return get(WFMod.RANDOM.nextInt(size()));
    }
}

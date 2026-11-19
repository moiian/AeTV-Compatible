package com.fongmi.android.tv.bean;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cache {

    private final Map<String, List<Filter>> cache;
    private String siteKey;
    private List<Class> types = List.of();

    private static class Loader {
        static volatile Cache INSTANCE = new Cache();
    }

    private static Cache get() {
        return Loader.INSTANCE;
    }

    public Cache() {
        cache = new HashMap<>();
    }

    public void put(Result result, String key) {
        if (result == null) return;
        siteKey = key;
        types = List.copyOf(result.getTypes());
        result.getTypes().forEach(type -> get().cache.put(type.getTypeId(), type.getFilters()));
    }

    public static List<Filter> get(Class type) {
        return get(type.getTypeId());
    }

    public static List<Filter> get(String typeId) {
        List<Filter> filters = get().cache.get(typeId);
        return filters == null ? Collections.emptyList() : filters;
    }

    public static List<Filter> copy(String typeId) {
        List<Filter> filters = get().cache.get(typeId);
        if (filters == null) return Collections.emptyList();
        return filters.stream().map(Filter::copy).toList();
    }

    public static List<Class> types(String key) {
        Cache cache = get();
        return key.equals(cache.siteKey) ? cache.types : List.of();
    }

    public static Cache clear() {
        get().cache.clear();
        get().types = List.of();
        get().siteKey = null;
        return get();
    }
}

package com.distelli.graphql;

import java.util.List;

public interface Resolver<T> {
    List<T> resolve(List<T> unresolved);
}

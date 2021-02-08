package com.distelli.graphql;

import graphql.schema.DataFetchingEnvironment;

public interface ResolveDataFetchingEnvironment<T> {
  T resolve(DataFetchingEnvironment env);
}

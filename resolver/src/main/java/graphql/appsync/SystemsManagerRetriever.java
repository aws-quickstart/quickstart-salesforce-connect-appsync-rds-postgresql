package graphql.appsync;

import graphql.sql.GraphQlTypeMetadata;

public interface SystemsManagerRetriever {
    GraphQlTypeMetadata lookupSystemParameter(String parameterName);
}

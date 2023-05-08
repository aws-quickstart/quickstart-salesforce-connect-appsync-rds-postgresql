package graphql.appsync;

import graphql.DatabaseConnectionParameters;

public interface SecretsManagerRetriever {
    DatabaseConnectionParameters lookupSecret(SecretsManagerSecret secret);
}

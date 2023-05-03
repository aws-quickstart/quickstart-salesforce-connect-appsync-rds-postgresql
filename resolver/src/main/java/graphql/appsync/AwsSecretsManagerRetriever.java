package graphql.appsync;

import graphql.DatabaseConnectionParameters;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;
import util.Util;

public class AwsSecretsManagerRetriever implements SecretsManagerRetriever {

    @Override
    public DatabaseConnectionParameters lookupSecret(SecretsManagerSecret secret) {
        // Create a Secrets Manager client
        SecretsManagerClient client = SecretsManagerClient.builder()
                .region(Region.of(secret.getRegion()))
                .build();

        GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                .secretId(secret.getName())
                .build();
        GetSecretValueResponse getSecretValueResponse = null;

        try {
            getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
        } catch (SecretsManagerException e) {
            throw new RuntimeException(e);
        }
        String retrievedSecret = getSecretValueResponse.secretString();

        // secret must match format of DbConnectionParameters class
        return Util.GSON.fromJson(retrievedSecret, DatabaseConnectionParameters.class);
    }
}
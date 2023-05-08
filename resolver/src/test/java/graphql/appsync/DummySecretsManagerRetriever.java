package graphql.appsync;

import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;
import graphql.sql.db.BaseDatabaseTest;
import graphql.sql.db.InMemoryDatabaseProvider;
import util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test mock of SecretsManager. Gets test DB credentials from specific locations on machine running test
 */
public class DummySecretsManagerRetriever implements SecretsManagerRetriever {
    public static final DummySecretsManagerRetriever INSTANCE = new DummySecretsManagerRetriever();
    private final Map<String, DatabaseConnectionParameters> dummySecrets = new HashMap<>();
    
    private DummySecretsManagerRetriever() {
        String testDataRoot = System.getProperty("user.home") + "/resolver/";
        try (Stream<Path> stream = Files.walk(Path.of(testDataRoot), 1)) {
            List<Path> testSqlPaths = stream
                    .filter(Files::isRegularFile)
                    .sorted().collect(Collectors.toList());

            for (Path path : testSqlPaths) {
                String baseName = com.google.common.io.Files.getNameWithoutExtension(path.toString());
                String paramJson = Files.readString(path, StandardCharsets.UTF_8);
                DatabaseConnectionParameters params = Util.GSON.fromJson(paramJson, DatabaseConnectionParameters.class);
                dummySecrets.put(baseName, params);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public DatabaseConnectionParameters lookupSecret(SecretsManagerSecret secret) {
        // in-memory DB just needs to know what compatibility mode to set to, don't need actual credentials
        if (secret.getName().contains(BaseDatabaseTest.inMemoryPrefix)) {
            DatabaseConnectionParameters params = new DatabaseConnectionParameters();
            params.setDbname(secret.getName());  // secret name is test name. database is unique to test
            params.setEngine(InMemoryDatabaseProvider.VENDOR);
            return params;
        }
        
        // for testing on DBs running on local
        if (!dummySecrets.containsKey(secret.getName())) {
            throw new GraphQlAdapterException(String.format(
                    "missing DummySecretsManager entry for: %s", secret.getName()));
        }
        return dummySecrets.get(secret.getName());
    }
}

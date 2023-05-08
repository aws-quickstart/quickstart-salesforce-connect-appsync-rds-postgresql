package graphql.appsync;

import graphql.GraphQlAdapterException;
import graphql.sql.GraphQlTypeMetadata;
import util.Util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test mock of SystemsManager. Pulls in GraphQL type metadata from test resources.
 */
public class DummySystemsManagerRetriever implements SystemsManagerRetriever {
    private String secret = null;
    private final Map<String, GraphQlTypeMetadata> nameToMetadata = new HashMap<>();
    
    public DummySystemsManagerRetriever() {
        init();
    }
    
    @Override
    public GraphQlTypeMetadata lookupSystemParameter(String parameterName) {
        parameterName = parameterName.replace("ResolverTesting", "");
        if (!nameToMetadata.containsKey(parameterName)) {
            throw new GraphQlAdapterException("unknown DummySystemsManager parameter: " + parameterName);
        }
        if (secret == null) {
            throw new GraphQlAdapterException("must set the secret name to be used for this test");
        }
        GraphQlTypeMetadata testMetadata = nameToMetadata.get(parameterName);
        
        // DummySecretsManagerRetriever expects the DB vendor as secret name
        testMetadata.setSecretName(secret);
        testMetadata.setSecretRegion(secret);
        
        return nameToMetadata.get(parameterName);
    }

    /**
     * Set to name of test for in-memory database test, or name of vendor for real database test
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }
    
    private void init() {
        String typeMetadataRoot = "src/test/resources/test_data/";
        List<Path> typeMetadataFilePaths;
        try (Stream<Path> stream = Files.walk(Path.of(typeMetadataRoot), 3)) {
            typeMetadataFilePaths = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".json"))
                    .collect(Collectors.toList());
            
            for (Path path : typeMetadataFilePaths) {
                String typeName = path.getFileName().toString().replace(".json", "");
                String tableMetadataString = Files.readString(path, StandardCharsets.UTF_8);
                GraphQlTypeMetadata metadata = Util.GSON.fromJson(tableMetadataString, GraphQlTypeMetadata.class);
                nameToMetadata.put(typeName, metadata);
            }
        } catch (Exception ex) {
            throw new RuntimeException("fatal error setting up DummySystemsManagerRetriever", ex);
        }
    }
}

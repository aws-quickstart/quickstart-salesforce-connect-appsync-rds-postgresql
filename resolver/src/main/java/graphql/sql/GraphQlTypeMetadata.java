package graphql.sql;

import graphql.GraphQlFieldType;

import java.util.List;
import java.util.Map;

/**
 * Holds metadata about a GraphQL type, as the resolver does not have access to the GraphQL schema in AppSync. 
 * Currently retrieved from only SystemsManager
 */
public class GraphQlTypeMetadata {
    private String secretName;
    private String secretRegion;
    private List<String> keyFields;
    private Map<String, LookupInfo> lookupInfos;
    private String databaseTableName;
    private Map<String, GraphQlFieldDefinition> graphQLFields;

    public String getDatabaseTableName() {
        return databaseTableName;
    }

    public void setDatabaseTableName(String databaseTableName) {
        this.databaseTableName = databaseTableName;
    }

    public Map<String, GraphQlFieldDefinition> getGraphQLFields() {
        return graphQLFields;
    }

    public void setGraphQLFields(Map<String, GraphQlFieldDefinition> graphQLFields) {
        this.graphQLFields = graphQLFields;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getSecretRegion() {
        return secretRegion;
    }

    public void setSecretRegion(String secretRegion) {
        this.secretRegion = secretRegion;
    }

    public List<String> getKeyFields() {
        return keyFields;
    }

    public void setKeyFields(List<String> keyFields) {
        this.keyFields = keyFields;
    }

    public Map<String, LookupInfo> getLookupInfos() {
        return lookupInfos;
    }

    public void setLookupInfos(Map<String, LookupInfo> lookupInfos) {
        this.lookupInfos = lookupInfos;
    }
}

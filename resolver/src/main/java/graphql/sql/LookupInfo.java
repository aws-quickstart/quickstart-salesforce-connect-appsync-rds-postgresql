package graphql.sql;

import graphql.GraphQlFieldType;

import java.util.List;
import java.util.Map;

/**
 * Holds metadata about a GraphQL type, as the resolver does not have access to the GraphQL schema in AppSync. 
 * Currently retrieved from only SystemsManager
 */
public class LookupInfo {
    private String type;
    private List<String> keyFields;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getKeyFields() {
        return keyFields;
    }

    public void setKeyFields(List<String> keyFields) {
        this.keyFields = keyFields;
    }
}

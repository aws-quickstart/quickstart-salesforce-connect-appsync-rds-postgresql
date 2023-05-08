package graphql.appsync;

import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;
import graphql.GraphQlFieldType;
import graphql.RequestType;
import graphql.sql.GraphQlFieldDefinition;
import graphql.sql.LookupInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Context input data for SQL data source resolver
 * 
 * Example query input from AppSync:
 * {
 *     "arguments": {}
 *     "info": {
 *         "selectionSetList": [
 *             "edges",
 *             "edges/node",
 *             "edges/node/OrderId",
 *             "edges/node/Status",
 *             "edges/node/TotalCost",
 *             "edges/node/OrderDate",
 *             "edges/node/id"
 *         ],
 *         "selectionSetGraphQL": "{\n  edges {\n    node {\n      OrderId\n      Status\n      TotalCost\n      OrderDate\n      id\n    }\n  }\n}",
 *         "fieldName": "public_MyOrder",
 *         "parentTypeName": "Query",
 *         "variables": {}
 *     }
 * }
 * 
 * Example of node/global ID query input:
 * {
 *     "arguments": {
 *         "id": "MySchema_MyOrder-ORD\\-500"
 *     },
 *     "info": {
 *         "selectionSetList": [],
 *         "selectionSetGraphQL": "{\n  ... on MySchema_MyOrder {\n    id\n    OrderId\n    OrderDate\n    ContactName\n  }\n  ... on MySchema_MyOrderLineItem {\n    id\n  }\n}",
 *         "fieldName": "node",
 *         "parentTypeName": "Query",
 *         "variables": {}
 *     }
 * }
 */
public class AppSyncSqlResolverInput {
    // directly from AppSync event, we do not use the request mapping template
    private String fieldName;  // name of the query
    private RequestType requestType;
    private Map<String, Object> queryArguments;
    private List<String> selectionSetList;  // which fields to return
    private Map<String, GraphQlFieldDefinition> graphQLFieldsInfo; // contains graphQL 'fieldType' and 'columnName' info
    // retrieved from SystemsManager
    private Map<String, GraphQlFieldType> fieldTypes;
    private List<String> keyFields;
    private Map<String, LookupInfo> lookupInfos;
    private String databaseTableName;
    
    // retrieved from SecretsManager
    private DatabaseConnectionParameters dbConnectionParameters;

    public AppSyncSqlResolverInput(Map<String, Object> mapInput, boolean isQueryByGlobalId) {
        Map<String,Object> info = (Map<String, Object>) mapInput.get("info");
        this.setRequestType(RequestType.valueOf(((String) info.get("parentTypeName")).toUpperCase()));
        this.setQueryArguments((Map<String, Object>) mapInput.get("arguments"));
        
        List<String> appsyncSelectList = (List<String>) info.get("selectionSetList");
        
        if (isQueryByGlobalId) {
            String graphQlTypeName = getTypeNameFromGlobalId((String) getQueryArguments().get("id"));
            String selectionSetString = (String) info.get("selectionSetGraphQL");
            List<String> parsedSelect = parseSelectForNodeQuery(selectionSetString, graphQlTypeName);
            
            // order doesn't matter, AppSync takes care of it
            parsedSelect = parsedSelect.stream().filter(parsed -> !appsyncSelectList.contains(parsed)).collect(Collectors.toList());
            appsyncSelectList.addAll(parsedSelect);
            
            this.setFieldName(graphQlTypeName);
            this.setSelectionSetList(appsyncSelectList);
        } else {
            this.setFieldName((String) info.get("fieldName"));
            this.setSelectionSetList(appsyncSelectList);
        }
    }
    
    private String getTypeNameFromGlobalId(String globalId) {
        int dashIndex = globalId.indexOf("-");
        if (dashIndex == -1) {
            throw new GraphQlAdapterException("invalid global ID, must be in format 'TypeName-Value1-Value2...'. Got: " + globalId);
        }
        return globalId.substring(0, dashIndex);  // GraphQL type names syntactically cannot have dashes, so this is safe
    }

    private List<String> parseSelectForNodeQuery(String selectionSetString, String graphQlTypeName) {
        // group 1: ... on MySchema_MyOrder
        // group 2: the bracket after group 1
        Pattern pattern = Pattern.compile("(\\.\\.\\. on \\S+) (\\{[^}]+})");
        Matcher matcher = pattern.matcher(selectionSetString);
        Map<String, String> matches = matcher.results().collect(Collectors.toMap(
                mr -> mr.group(1).replace("... on ", ""),
                mr -> mr.group(2)
        ));
        
        if (matches.size() != 1) {
            throw new GraphQlAdapterException("expected node query return type to be a Union type with a single inline fragment, got fragments: " + matches.keySet());
        }
        String fragmentType = matches.keySet().iterator().next();
        if (!graphQlTypeName.equals(fragmentType)) {
            throw new GraphQlAdapterException(String.format(
                    "the fragment type does not match the global ID type. Fragment type: %s. Global ID type: %s", 
                    fragmentType, graphQlTypeName));
        }
        
        // get selection set in the brackets
        String matchedSelectString = matches.get(graphQlTypeName);
        matchedSelectString = matchedSelectString.replaceAll("[\\{}]", "");

        return Arrays.stream(matchedSelectString.split("\\s+"))
                .filter(str -> !str.isEmpty()).collect(Collectors.toList());
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Map<String, Object> getQueryArguments() {
        return queryArguments;
    }

    public void setQueryArguments(Map<String, Object> queryArguments) {
        this.queryArguments = queryArguments;
    }

    public List<String> getSelectionSetList() {
        return selectionSetList;
    }

    public void setSelectionSetList(List<String> selectionSetList) {
        this.selectionSetList = selectionSetList;
    }

    public Map<String, GraphQlFieldType> getFieldTypes() {
        return fieldTypes;
    }

    /**
     * Set 'this.fieldTypes' using System Manager 'graphQLFields' property
     *
     * input:
     * "graphQLFields": {
     *     "OrderId": {
     *         "fieldType": "String",
     *         "columnName": "order_id"
     *     },
     *     "OrderDate": {
     *         "fieldType": "AWSDateTime",
     *         "columnName": "order_date"
     *     },
     *     "Status": {
     *         "fieldType": "String",
     *        "columnName": "status"
     *     },
     *     "TotalCost": {
     *         "fieldType": "Float",
     *         "columnName": "total_cost"
     *     }
     * }
     *
     */
    public void setFieldTypes(Map<String, GraphQlFieldDefinition> graphQLFields) {
        this.fieldTypes = new HashMap<>();
        if (graphQLFields != null) {
            graphQLFields.forEach((graphQLField, definition) -> {
                if (definition != null) {
                    this.fieldTypes.put(graphQLField, definition.getFieldType());
                }
            });
        }
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public void setRequestType(RequestType requestType) {
        this.requestType = requestType;
    }

    public DatabaseConnectionParameters getDbConnectionParameters() {
        return dbConnectionParameters;
    }

    public void setDbConnectionParameters(DatabaseConnectionParameters dbConnectionParameters) {
        this.dbConnectionParameters = dbConnectionParameters;
    }

    public List<String> getKeyFields() {
        return keyFields;
    }

    public void setKeyFields(List<String> keyFields) {
        if (keyFields == null) {
            keyFields = new ArrayList<>();
        }
        this.keyFields = keyFields;
    }

    public Map<String, LookupInfo> getLookupInfos() {
        return lookupInfos;
    }

    public void setLookupInfos(Map<String, LookupInfo> lookupInfos) {
        if (lookupInfos == null) {
            lookupInfos = new HashMap<>();
        }
        this.lookupInfos = lookupInfos;
    }

    public String getDatabaseTableName() {
        return databaseTableName;
    }

    public void setDatabaseTableName(String databaseTableName) {
        this.databaseTableName = databaseTableName;
    }

    public Map<String, GraphQlFieldDefinition> getGraphQLFieldsInfo() {
        return graphQLFieldsInfo;
    }

    public void setGraphQLFieldsInfo(Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
        this.graphQLFieldsInfo = graphQLFieldsInfo;
    }

    @Override
    public String toString() {
        return "AppSyncSqlResolverInput{" +
                "fieldName='" + fieldName + '\'' +
                ", requestType=" + requestType +
                ", queryArguments=" + queryArguments +
                ", selectionSetList=" + selectionSetList +
                ", databaseTableName='" + databaseTableName + '\'' +
                ", graphQLFieldsInfo=" + graphQLFieldsInfo.toString() +
                ", keyFields=" + keyFields +
                ", dbConnectionParameters=" + dbConnectionParameters +
                '}';
    }
}

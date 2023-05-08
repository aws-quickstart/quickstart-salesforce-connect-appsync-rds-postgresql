package graphql.appsync;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.common.annotations.VisibleForTesting;
import graphql.sql.GraphQlTypeMetadata;
import graphql.sql.LookupInfo;
import graphql.sql.db.SqlDatabaseProvider;
import graphql.sql.db.SqlDatabaseProviderFactory;
import graphql.*;
import graphql.sql.SqlStatementType;
import util.Util;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class AppSyncSqlResolverLambdaRequestHandler implements RequestHandler<Map<String,Object>, Object> {
    private final SecretsManagerRetriever secretsManagerClient;
    private final SystemsManagerRetriever systemsManagerRetriever;
    
    public static LambdaLogger LOGGER = new NoOpLogger();
    public static final String LOOKUP_FIELD_SUFFIX = "_LookupId";
    
    public AppSyncSqlResolverLambdaRequestHandler() {
        this(new AwsSecretsManagerRetriever(), new AwsSystemsManagerRetriever());
    }
    
    @VisibleForTesting
    public AppSyncSqlResolverLambdaRequestHandler(SecretsManagerRetriever secretsManagerRetriever, SystemsManagerRetriever systemsManagerRetriever) {
        this.secretsManagerClient = secretsManagerRetriever;
        this.systemsManagerRetriever = systemsManagerRetriever;
    }

    /**
     * Lambda entry point
     */
    public Object handleRequest(Map<String,Object> requestInput, Context context) {
        if (context != null) {
            LOGGER = context.getLogger();
        }
        LOGGER.log("Raw Input: " + Util.GSON.toJson(requestInput));
        
        boolean isQueryById = "node".equals(((Map<String, Object>) requestInput.get("info")).get("fieldName"));
    
        AppSyncSqlResolverInput input = new AppSyncSqlResolverInput(requestInput, isQueryById);
        RequestType requestType = input.getRequestType();
        String graphQlTypeName = getGraphQlTypeName(input.getFieldName(), requestType);

        // get table metadata and SecretsManager secret name from SystemsManager
        GraphQlTypeMetadata tableMetadata = systemsManagerRetriever.lookupSystemParameter(graphQlTypeName);
        LOGGER.log("SystemsManager parameter: " + Util.GSON.toJson(tableMetadata));
        input.setKeyFields(tableMetadata.getKeyFields());
        input.setGraphQLFieldsInfo(tableMetadata.getGraphQLFields());
        input.setFieldTypes(tableMetadata.getGraphQLFields());
        input.setDatabaseTableName(tableMetadata.getDatabaseTableName());
        input.setLookupInfos(tableMetadata.getLookupInfos());
        validateSystemsManagerParameters(input, graphQlTypeName);
        
        // get DB credentials using SecretsManager secret
        SecretsManagerSecret secret = new SecretsManagerSecret(tableMetadata.getSecretName(), tableMetadata.getSecretRegion());
        DatabaseConnectionParameters params = secretsManagerClient.lookupSecret(secret);
        input.setDbConnectionParameters(params);

        SqlDatabaseProvider dbProvider = SqlDatabaseProviderFactory.getProvider(input.getDbConnectionParameters().getEngine());
        try (GraphQlSqlResolverRunner resolverRunner = new GraphQlSqlResolverRunner(dbProvider, graphQlTypeName, input)) {
            Object result;
            if (requestType == RequestType.QUERY) {
                QueryResultSet queryResultSet = handleQuery(resolverRunner, input, graphQlTypeName, isQueryById);
                if (isQueryById) {
                    result = processResultForQueryById(queryResultSet, input, graphQlTypeName);
                } else {
                    result = queryResultSet;
                }
            } else {
                result = handleMutation(resolverRunner, input, graphQlTypeName);
            }
            LOGGER.log(result.toString());
            return result;
        }
    }
    
    private QueryResultSet handleQuery(GraphQlSqlResolverRunner resolverRunner, AppSyncSqlResolverInput input, String graphQlTypeName, boolean isQueryById) {
        String sqlTableName = input.getDatabaseTableName();
        SelectInfo selectInfo = getSelectInfo(input);
        boolean setEdgeCursorValue = input.getSelectionSetList().stream().filter(val -> val.equals("edges/cursor")).count() > 0;
        // query by id: convert "id" argument into a where clause
        if (isQueryById) {
            String globalIdValue = (String) input.getQueryArguments().get("id");
            LinkedHashMap<String, Object> keyColumnToValue = Util.globalIdToComponents(globalIdValue, input.getKeyFields(), graphQlTypeName);
            LinkedHashMap<String, Object> whereClauseMap = Util.convertToWhereClauseMap(keyColumnToValue);
            input.getQueryArguments().put("where", whereClauseMap);
        }
        
        QueryResultSet resultSet = resolverRunner.query(sqlTableName, input.getQueryArguments(), selectInfo.sqlColumns);
        List<LinkedHashMap<String, Object>> graphQlEdges = resultSet.getEdges();
        graphQlEdges = processResult(graphQlEdges, graphQlTypeName, input, selectInfo);

        // regular queries support paging, wrap results in "node" as defined in Relay spec
        // this "node" is completely separate from the query by global ID function called "node"
        // For each edge in the connection, we asked for a cursor. This cursor is an opaque string,
        // and is precisely what we would pass to the after arg to paginate starting after this edge.
        graphQlEdges = graphQlEdges.stream().map(item -> {
                    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                    if (setEdgeCursorValue) {
                        map.put("cursor", item.get("Edges/cursor"));
                        item.remove("Edges/cursor");
                    }
                    map.put("node", item);
                    return map;
                }).collect(Collectors.toList());
        return new QueryResultSet(graphQlEdges, resultSet.getPageInfo());
    }

    private Map<String, Object> handleMutation(GraphQlSqlResolverRunner resolverRunner, AppSyncSqlResolverInput input, String graphQlTypeName) {
        String sqlTableName = input.getDatabaseTableName();
        int firstUnderscoreIndex = input.getFieldName().indexOf('_');
        String cudString = input.getFieldName().substring(0, firstUnderscoreIndex);
        SqlStatementType statementType;
        switch (cudString) {
            case "create":
                statementType = SqlStatementType.INSERT;
                break;
            case "update":
                statementType = SqlStatementType.UPDATE;
                break;
            case "delete":
                statementType = SqlStatementType.DELETE;
                break;
            default:
                throw new GraphQlAdapterException("Unknown mutation type: " + cudString);
        }

        // set the new values to insert/update
        LinkedHashMap<String, Object> newValues = new LinkedHashMap<>();
        if (Set.of(SqlStatementType.INSERT, SqlStatementType.UPDATE).contains(statementType)) {
            Map<String, Object> arguments = (Map<String, Object>) input.getQueryArguments().get("input");
            
            for (String key : arguments.keySet()) {
                if (key.equals("id")) {
                    continue;
                }
                // convert any lookups to their values
                if (input.getLookupInfos().containsKey(key)) {
                    LookupInfo lookupInfo = input.getLookupInfos().get(key);
                    LinkedHashMap<String, Object> components = Util.globalIdToComponents((String) arguments.get(key), 
                            lookupInfo.getKeyFields(), lookupInfo.getType());
                    components.forEach((field, value) -> {
                        if (arguments.containsKey(field) && !value.equals(arguments.get(field))) {  // the lookup and physical values differ
                            throw new GraphQlAdapterException(String.format("the provided input values for lookup %s and physical " +
                                    "column %s are different. lookup: %s. physical column: %s",
                                    key, field, arguments.get(key), arguments.get(field)));
                        }
                        newValues.put(field, value);
                    });
                } else {
                    newValues.put(key, arguments.get(key));
                }
            }
            if (SqlStatementType.UPDATE.equals(statementType) && newValues.isEmpty()) {
                throw new GraphQlAdapterException("no-op updates are not allowed, at least one field to update must be specified");
            }
        }
        
        // mutations return the affect rows as result. construct the WHERE clause to get the affected rows
        LinkedHashMap<String, Object> where;
        String globalIdValue = null;
        if (SqlStatementType.INSERT.equals(statementType)) {
            LinkedHashMap<String, Object> keyColumnToValue = new LinkedHashMap<>();
            input.getKeyFields().forEach(keyCol -> {
                Object componentValue = newValues.get(keyCol);
                if (componentValue == null) {
                    throw new GraphQlAdapterException("create mutation is missing required value: " + keyCol);
                }
                keyColumnToValue.put(keyCol, componentValue);
            });
            where = keyColumnToValue;
        } else if (SqlStatementType.UPDATE.equals(statementType)) {
            globalIdValue = (String) ((Map<String, Object>)input.getQueryArguments().get("input")).get("id");
            where = Util.globalIdToComponents(globalIdValue, input.getKeyFields(), graphQlTypeName);
        } else {  // delete
            globalIdValue = (String) input.getQueryArguments().get("id");
            where = Util.globalIdToComponents(globalIdValue, input.getKeyFields(), graphQlTypeName);
        }
        
        SelectInfo selectInfo = getSelectInfo(input);
        QueryResultSet result;
        try {
            result = resolverRunner.mutation(sqlTableName, newValues, where, selectInfo.sqlColumns, statementType);
        } catch (NoAffectedRowsException e) {
            throw new GraphQlAdapterException("did not find an object with id: " + globalIdValue);
        }
        List<LinkedHashMap<String, Object>> graphQlEdges = result.getEdges();
        graphQlEdges = processResult(graphQlEdges, graphQlTypeName, input, selectInfo);

        // always returning just one entry
        return graphQlEdges.get(0);
    }

    /**
     * parse the selectionSetList into SELECT columns. If "id" is a return type, ensure the key columns are in SELECT
     */
    private SelectInfo getSelectInfo(AppSyncSqlResolverInput input) {
        // get the select column names
        List<String> select = input.getSelectionSetList().stream()
                .map(selection -> {
                    if ("edges".equals(selection) || "edges/node".equals(selection) || "edges/cursor".equals(selection)
                            || "pageInfo".equals(selection) || "pageInfo/endCursor".equals(selection ) ||
                            "pageInfo/hasNextPage".equals(selection )) {
                        return null;
                    }
                    String expectedPrefix = "edges/node/";
                    if (!selection.startsWith(expectedPrefix)) {
                        return selection;  // mutations do not have edge/node structure
                    }
                    // edges/node/OrderDate -> OrderDate
                    return selection.substring(expectedPrefix.length());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (select.isEmpty()) {
            throw new GraphQlAdapterException("no valid select columns provided. Selection set: " + input.getSelectionSetList());
        }
        
        // fields added for global ID/lookup fields, must remove after the SQL query before returning result
        Set<String> fieldsToRemove = new HashSet<>();
        
        List<String> missingFieldTypes = select.stream()
                .filter(field -> !field.equals("id"))
                .filter(field -> !input.getLookupInfos().containsKey(field))
                .filter(field -> !input.getFieldTypes().containsKey(field)).collect(Collectors.toList());
        if (!missingFieldTypes.isEmpty()) {
            throw new GraphQlAdapterException("missing field type declarations in SystemsManager parameter: " + missingFieldTypes);
        }

        // add missing key column fields to select if querying global ID from Node interface
        boolean selectGlobalId = modifySelectForGlobalId("id", input.getKeyFields(), select, fieldsToRemove);

        List<String> lookupFields = select.stream().filter(input.getLookupInfos()::containsKey).collect(Collectors.toList());
        lookupFields.forEach(lookupField -> {
            LookupInfo info = input.getLookupInfos().get(lookupField);
            modifySelectForGlobalId(lookupField, info.getKeyFields(), select, fieldsToRemove);
        });

        // convert column names to SQL names
        List<String> selectSqlNames = select.stream()
                .map(graphQLName -> Util.graphQlToSqlName(graphQLName,input.getGraphQLFieldsInfo()))
                .collect(Collectors.toList());
        return new SelectInfo(selectSqlNames, selectGlobalId, lookupFields, fieldsToRemove);
    }

    static class SelectInfo {
        public final List<String> sqlColumns;
        public final boolean retrieveGlobalId;
        public final List<String> lookupFields;
        public final Set<String> fieldsToRemove;

        public SelectInfo(List<String> sqlColumns, boolean retrieveGlobalId, List<String> lookupFields, Set<String> fieldsToRemove) {
            this.sqlColumns = sqlColumns;
            this.retrieveGlobalId = retrieveGlobalId;
            this.fieldsToRemove = fieldsToRemove;
            
            this.lookupFields = lookupFields;
        }
    }

    /**
     * query by ID is handled differently than regular query. 
     * Query by ID should have exactly one result, and the response needs to include the result type
     */
    private LinkedHashMap<String, Object> processResultForQueryById(QueryResultSet queryResultSet, AppSyncSqlResolverInput input, String graphQlTypeName) {
        List<LinkedHashMap<String, Object>> edges = queryResultSet.getEdges();
        if (edges.size() == 0) {
            throw new GraphQlAdapterException("no object exists with id: " + input.getQueryArguments().get("id"));
        } if (edges.size() > 1) {
            throw new GraphQlAdapterException("internal error: got more than one object when querying by global id");
        }
        LinkedHashMap<String, Object> result = (LinkedHashMap<String, Object>) edges.get(0).get("node");
        // add __typename because query by id has a Union result type
        result.put("__typename", graphQlTypeName);

        return result;
    }

    private List<LinkedHashMap<String, Object>> processResult(List<LinkedHashMap<String, Object>> edges, String graphQlTypeName, AppSyncSqlResolverInput input, SelectInfo selectInfo) {
        List<LinkedHashMap<String, Object>> graphQlEdges = edges.stream().map(value -> Util.sqlNameToGraphQlName(value, input.getGraphQLFieldsInfo())).collect(Collectors.toList());
        addLookupFieldValues(graphQlEdges, input.getLookupInfos(), selectInfo.lookupFields);
        if (selectInfo.retrieveGlobalId) {
            addGlobalIdValues(graphQlEdges, "id", graphQlTypeName, input.getKeyFields());
        }
        removeExtraFields(graphQlEdges, selectInfo.fieldsToRemove);
        return graphQlEdges;
    }

    private void addLookupFieldValues(List<LinkedHashMap<String, Object>> result, Map<String, LookupInfo> lookupFieldToType, List<String> lookupFields) {
        // lookup field value is just global ID of another type
        lookupFields.forEach(lookupField -> {
            LookupInfo lookupInfo = lookupFieldToType.get(lookupField);
            addGlobalIdValues(result, lookupField, lookupInfo.getType(), lookupInfo.getKeyFields());
        });
    }
    
    /**
     * Add global id to each row in the result, constructing the id with the key columns
     */
    private void addGlobalIdValues(List<LinkedHashMap<String, Object>> result, String fieldName, String typeName, List<String> keyColumns) {
        for (LinkedHashMap<String, Object> row : result) {
            List<Object> externalIdComponents = new ArrayList<>();
            externalIdComponents.add(typeName);
            for (String keyCol : keyColumns) {
                if (!row.containsKey(keyCol)) {
                    throw new GraphQlAdapterException(
                            String.format("expected key column %s to be in result set: %s", keyCol, row));
                }
                Object value = row.get(keyCol);
                if (value instanceof BigDecimal) {
                    value = ((BigDecimal) value).stripTrailingZeros().toPlainString();
                }
                externalIdComponents.add(value.toString().replace("-", "\\-"));
            }
            // id will always be at end of result set, that is okay because AppSync will put it in the correct order
            row.put(fieldName, externalIdComponents.stream().map(Object::toString).collect(Collectors.joining("-")));
        }
    }

    /**
     * when returning the global ID, the SQL query may include SELECT columns required to get global ID, but weren't 
     * requested in the original selectionSetList
     */
    private void removeExtraFields(List<LinkedHashMap<String, Object>> result, Set<String> toRemove) {
        for (LinkedHashMap<String, Object> row : result) {
            toRemove.forEach(row::remove);
        }
    }

    /**
     * Query mySchema_myOrder -> MySchema_MyOrder
     * Mutation create_mySchema_myOrder -> MySchema_MyOrder
     */
    private String getGraphQlTypeName(String appsyncFieldName, RequestType requestType) {
        if (RequestType.MUTATION.equals(requestType)) {
            int firstUnderscoreIndex = appsyncFieldName.indexOf("_");
            if (firstUnderscoreIndex == -1) {
                throw new GraphQlAdapterException("invalid mutation name: " + appsyncFieldName);
            }
            appsyncFieldName = appsyncFieldName.substring(firstUnderscoreIndex + 1);
        } else {  // capitalize first letter for query
            appsyncFieldName = appsyncFieldName.substring(0, 1).toUpperCase() + appsyncFieldName.substring(1);
        }
        return appsyncFieldName;
    }

    /**
     * validate the parameter retrieved from SystemsManager
     */
    private void validateSystemsManagerParameters(AppSyncSqlResolverInput input, String graphQlTypeName) {
        if (input.getKeyFields() == null || input.getKeyFields().isEmpty()) {
            throw new GraphQlAdapterException(String.format("SystemsManager parameter for %s must define keyFields", graphQlTypeName));
        }
        if (input.getFieldTypes() == null || input.getFieldTypes().isEmpty()) {
            throw new GraphQlAdapterException(String.format("SystemsManager parameter for %s must define fieldType in 'graphQLFields' property", graphQlTypeName));
        }
        List<String> keyColsMissingFieldType = input.getKeyFields().stream()
                .filter(keyCol -> input.getFieldTypes().containsKey(keyCol))
                .collect(Collectors.toList());
        if (keyColsMissingFieldType.isEmpty()) {
            throw new GraphQlAdapterException("Missing field type declarations for key columns: " + keyColsMissingFieldType);
        }
    }
    
    private boolean modifySelectForGlobalId(String field, List<String> keyColumns, List<String> select, Set<String> fieldsToRemove) {
        if (!select.contains(field)) {
            return false;
        }
        select.remove(field);
        for (String keyCol : keyColumns) {
            if (!select.contains(keyCol)) {
                select.add(keyCol);
                fieldsToRemove.add(keyCol);
            }
        }
        return true;
    }
}

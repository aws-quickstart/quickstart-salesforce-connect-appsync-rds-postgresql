package graphql.sql;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import graphql.GraphQlAdapterException;
import graphql.GraphQlFieldType;
import util.Util;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static graphql.appsync.AppSyncSqlResolverLambdaRequestHandler.LOOKUP_FIELD_SUFFIX;

/**
 * WhereClause requires custom deserializer because some json entries are the names of the table's fields
 */
public class WhereClauseDeserializer implements JsonDeserializer<SqlQueryStatement.WhereClause> {
    private final String graphQlTypeName;
    private final Map<String, GraphQlFieldType> fieldTypes;
    private Map<String, GraphQlFieldDefinition> graphQLFieldsInfo; // contains graphQL 'fieldType' and 'sqlName' info
    private final List<String> keyFields;
    private final Map<String, LookupInfo> lookupInfos;
    private final String vendor;

    public WhereClauseDeserializer(String graphQlTypeName, Map<String, GraphQlFieldType> fieldTypes, 
                                   List<String> keyFields, Map<String, LookupInfo> lookupInfos,
                                   String vendor, Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
        this.graphQlTypeName = graphQlTypeName;
        this.fieldTypes = fieldTypes;
        this.keyFields = keyFields;
        this.lookupInfos = lookupInfos;
        this.vendor = vendor;
        this.graphQLFieldsInfo = graphQLFieldsInfo;
    }

    @Override
    public SqlQueryStatement.WhereClause deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        SqlQueryStatement.WhereClause output = new SqlQueryStatement.WhereClause();
        output.setFieldTypes(fieldTypes);
        output.setVendor(vendor);
        LinkedHashMap<String, Map<String, Object>> filter = new LinkedHashMap<>();

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            switch (entry.getKey()) {
                case "and":
                    output.setAnd(jsonDeserializationContext.deserialize(entry.getValue(), new TypeToken<ArrayList<SqlQueryStatement.WhereClause>>(){}.getType()));
                    break;
                case "or":
                    output.setOr(jsonDeserializationContext.deserialize(entry.getValue(), new TypeToken<ArrayList<SqlQueryStatement.WhereClause>>(){}.getType()));
                    break;
                case "not":
                    output.setNot(jsonDeserializationContext.deserialize(entry.getValue(), new TypeToken<SqlQueryStatement.WhereClause>(){}.getType()));
                    break;
                case "id":
                    handleGlobalId(output, jsonDeserializationContext, entry, graphQlTypeName, keyFields);
                    break;
                default:
                    String fieldName = entry.getKey();
                    if (lookupInfos.containsKey(fieldName)) {
                        LookupInfo info = lookupInfos.get(fieldName);
                        handleGlobalId(output, jsonDeserializationContext, entry, info.getType(), info.getKeyFields());
                        break;
                    }
                    
                    Map<String, Object> filterPredicate = jsonDeserializationContext.deserialize(entry.getValue(), Object.class);
                    if (filterPredicate.keySet().size() != 1) {
                        throw new GraphQlAdapterException("filter should only have one operator, got: " + filterPredicate);
                    }
                    String operator = filterPredicate.keySet().iterator().next();
                    Object rawObject = filterPredicate.get(operator);
                    if (fieldTypes.get(fieldName) == null) {
                        throw new GraphQlAdapterException("field type not defined for column: " + fieldName);    
                    }
                    Object convertedObject = TypeMapper.convertToJdbcReadyType(rawObject, fieldTypes.get(fieldName), vendor);
                    filterPredicate.put(operator, convertedObject);
                    filter.put(Util.graphQlToSqlName(fieldName, this.graphQLFieldsInfo), filterPredicate);
            }
        }
        output.setFilter(filter);

        return output;
    }
    
    private void handleGlobalId(SqlQueryStatement.WhereClause output, JsonDeserializationContext jsonDeserializationContext,
                                Map.Entry<String, JsonElement> filter, String typeName, List<String> keyFields) {
        Map<String, Object> filterPredicate = jsonDeserializationContext.deserialize(filter.getValue(), Object.class);
        if (filterPredicate.keySet().size() != 1) {
            throw new GraphQlAdapterException("filter should only have one operator, got: " + filterPredicate);
        }
        String operator = filterPredicate.keySet().iterator().next();
        if (!Set.of("eq", "in").contains(operator)) {
            throw new GraphQlAdapterException("global ID/lookup filter only supports 'eq' and 'in' filter, got: " + filterPredicate);
        }
        List<String> globalIdValues = new ArrayList<>();
        if (operator.equals("eq")) {
            globalIdValues.add((String)filterPredicate.get(operator));
        } else {  // in
            globalIdValues.addAll((List<String>)filterPredicate.get(operator));
        }
        List<Object> andClauses = new ArrayList<>();
        for (String globalIdValue : globalIdValues) {
            LinkedHashMap<String, Object> globalIdComponents = Util.globalIdToComponents(
                    globalIdValue, keyFields, typeName);
            LinkedHashMap<String, Object> eqWrapped = Util.convertToWhereClauseMap(globalIdComponents);
            // and clause is a list of individual filters
            List<Map<String, Object>> componentFilters = eqWrapped.keySet().stream()
                    .map(fieldName -> Map.of(fieldName, eqWrapped.get(fieldName)))
                    .collect(Collectors.toList());
            andClauses.add(Map.of("and", componentFilters));
        }
        output.setOr(jsonDeserializationContext.deserialize(Util.GSON.toJsonTree(andClauses), new TypeToken<ArrayList<SqlQueryStatement.WhereClause>>(){}.getType()));
    }
}
package util;

import com.google.common.base.CaseFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import graphql.GraphQlAdapterException;
import graphql.sql.GraphQlFieldDefinition;
import software.amazon.awssdk.utils.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Util {
    public static Gson GSON = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .serializeNulls()
            .create();
    
    /**
     * Split a name into its single underscore-separated components, where single underscores separate components
     */
    public static List<String> splitNameToComponents(String graphQlName) {
        Matcher m = Pattern.compile("([a-zA-Z0-9]|_{2,})+").matcher(graphQlName);
        return m.results().map(mr -> graphQlName.substring(mr.start(), mr.end())).collect(Collectors.toList());
    }
    
    /**
     * Convert a GraphQL type/field name to a SQL table/column name.
     * Names will never start or end with an underscore.
     * 
     * MySchema_MyOrder -> my_schema.my_order
     * My__Schema_My___Order -> my__schema.my___order
     */
    @Deprecated
    public static String graphQlToSqlName(String graphQlName) {
        return splitNameToComponents(graphQlName).stream()
                .map(comp -> CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, comp))
                .map(Util::removeOneConsecutiveUnderscore)  // case conversion creates an extra underscore
                .collect(Collectors.joining("."));
    }

    /**
     * Convert a GraphQL type/field name to a SQL table/column name.
     * table/column names always need to be defined in the System Manager parameter 'graphQLFields'
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
     * TotalCost -> total_cost
     */
    public static String graphQlToSqlName(String graphQLFieldName, Map<String, GraphQlFieldDefinition> fieldsInfo) {
        if (fieldsInfo != null && fieldsInfo.containsKey(graphQLFieldName)) {
            return fieldsInfo.get(graphQLFieldName).getColumnName();
        } else {
            throw new GraphQlAdapterException(String.format("The graphQL field %s not defined in system manager 'graphQLFields' parameter", graphQLFieldName));
        }
    }

    /**
     * "item_name" -> "ItemName"
     * "item__name" -> "Item__Name"
     * 
     * Names will never start or end with an underscore
     */
    @Deprecated
    public static String sqlToGraphQlName(String snakeString) {
        StringBuilder graphQlName = new StringBuilder();
        
        int consecutiveUnderscores = 0;
        for (int i=0; i<snakeString.length(); i++) {
            String c = snakeString.substring(i, i+1);
            if (c.equals("_")) {
                consecutiveUnderscores += 1;
                continue;
            }
            if (consecutiveUnderscores >= 2) {
                graphQlName.append("_".repeat(consecutiveUnderscores));
            }
            if (i == 0 || consecutiveUnderscores > 0) {
                c = c.toUpperCase();
            }
            consecutiveUnderscores = 0;
            graphQlName.append(c);
        }
        
        return graphQlName.toString();
    }

    /**
     * "item_name" -> "ItemName"
     * table/column names always need to be defined in the System Manager parameter 'graphQLFields'
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
    public static String sqlToGraphQlName(String sqlName, Map<String, GraphQlFieldDefinition> fieldsInfo) {
        String graphQLName = null;
        if (sqlName == null || fieldsInfo == null) {
            return null;
        }
        for (Map.Entry<String, GraphQlFieldDefinition> entry : fieldsInfo.entrySet()) {
            if (entry.getValue().getColumnName().equals(sqlName)) {
                graphQLName = entry.getKey();
                break;
            }
        }
        if (graphQLName == null) {
            throw new GraphQlAdapterException(String.format("The sql field %s not defined in system manager 'graphQLFields' parameter", sqlName));
        }
        return graphQLName;
    }

    /**
     * mySchema_my__Table -> mySchema_my_Table
     */
    private static String removeOneConsecutiveUnderscore(String input) {
        char lastChar = input.charAt(input.length()-1);
        if (lastChar == '_') {
            throw new GraphQlAdapterException("graphql names cannot end with underscores: " + input);
        }
        
        StringBuilder out = new StringBuilder();
        boolean previousUnderscore = false;
        for (int i=0; i<input.length()-1; i++) {
            if (input.charAt(i) == '_') {  // remove the last underscore
                if (previousUnderscore && input.charAt(i+1) != '_') {
                    continue;
                }
                previousUnderscore = true;
            } else {
                previousUnderscore = false;
            }
            out.append(input.charAt(i));
        }
        out.append(lastChar);
        return out.toString();
    }

    /**
     * Helper method for multiple name conversions
     */
    @Deprecated
    public static LinkedHashMap<String, Object> graphQlToSqlName(Map<String, Object> graphQlNames) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : graphQlNames.keySet()) {
            result.put(graphQlToSqlName(key), graphQlNames.get(key));
        }
        return result;
    }

    /**
     * Helper method for multiple name conversions
     */
    public static LinkedHashMap<String, Object> graphQlToSqlName(Map<String, Object> graphQlNames, Map<String, GraphQlFieldDefinition> fieldsInfo) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : graphQlNames.keySet()) {
            result.put(graphQlToSqlName(key, fieldsInfo), graphQlNames.get(key));
        }
        return result;
    }

    /**
     * Convert a SQL table/column name to a GraphQL type/field name .
     *
     * my_schema.my_order -> MySchema_MyOrder
     * my__schema.my___order -> My__Schema_My___Order
     */
    @Deprecated
    public static LinkedHashMap<String, Object> sqlNameToGraphQlName(Map<String, Object> snakeCase) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : snakeCase.keySet()) {
            result.put(sqlToGraphQlName(key), snakeCase.get(key));
        }
        return result;
    }

    /**
     * Convert a SQL table/column name to a GraphQL type/field name .
     *
     */
    public static LinkedHashMap<String, Object> sqlNameToGraphQlName(Map<String, Object> snakeCase, Map<String, GraphQlFieldDefinition> fieldsInfo) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (String key : snakeCase.keySet()) {
            String graphQlName = null;
            if (key.equals("edges/cursor")) {
                graphQlName = StringUtils.capitalize(key);
            } else {
                graphQlName = sqlToGraphQlName(key, fieldsInfo);
            }
            result.put(graphQlName, snakeCase.get(key));
        }
        return result;
    }

    /**
     * Parse a global ID into its components based on key columns. Verifies the GlobalID is for the right type
     * 
     * MySchema_MyOrderItem-ORD\-100-PRD-200 -> {"ParentOrderId": ORD-100, "ParentProductId": PRD-200}
     */
    public static LinkedHashMap<String, Object> globalIdToComponents(String globalIdValue, List<String> keyColumns, String graphQlTypeName) {
        List<String> globalIdComponents = Arrays.asList(globalIdValue.split("(?<!\\\\)-"));  // split on "-" if not escaped
        if (globalIdComponents.size() != keyColumns.size() + 1) {
            String expectedFormat = String.format("%s-%s", graphQlTypeName, String.join("-", keyColumns));
            throw new GraphQlAdapterException(
                    String.format("unable to parse global id value %s. expected it to be in the form: %s. dashes should be escaped with '\\'",
                            globalIdValue, expectedFormat));
        }
        if (!graphQlTypeName.equals(globalIdComponents.get(0))) {
            throw new GraphQlAdapterException(String.format("expected id for %s, got: %s", graphQlTypeName, globalIdValue));
        }
        
        globalIdComponents = globalIdComponents.stream().map(str -> str.replace("\\-", "-")).collect(Collectors.toList());
        globalIdComponents = globalIdComponents.subList(1, globalIdComponents.size());
        
        LinkedHashMap<String, Object> keyColumnToValue = new LinkedHashMap<>();
        for (int i=0; i<globalIdComponents.size(); i++) {
            keyColumnToValue.put(keyColumns.get(i), globalIdComponents.get(i));
        }
        return keyColumnToValue;
    }

    /**
     * {"fieldName": "fieldValue"} -> {"fieldName": {"eq": "fieldValue}}
     */
    public static LinkedHashMap<String, Object> convertToWhereClauseMap(LinkedHashMap<String, Object> map) {
        LinkedHashMap<String, Object> whereClauseMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Map<String, Object> filter = new HashMap<>(Map.of("eq", entry.getValue()));
            whereClauseMap.put(entry.getKey(), filter);
        }
        return whereClauseMap;
    }
    
    public static String rawValueFromGlobalId(String globalId, String expectedType) {
        String expectedPrefix = expectedType + "-";
        if (!globalId.startsWith(expectedPrefix)) {
            throw new GraphQlAdapterException(
                    String.format("expected filter by lookup value to be of type %s, got: %s",
                            expectedType, globalId));
        }
        return globalId.substring(expectedPrefix.length())
                .replace("\\-", "-");
    }
}
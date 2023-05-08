package graphql.appsync;

import java.util.Base64;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.reflect.TypeToken;
import graphql.*;
import graphql.sql.*;
import graphql.sql.db.SqlDatabaseProvider;
import util.Util;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class GraphQlSqlResolverRunner implements GraphQlResolverRunner, AutoCloseable {
    private final SqlDatabaseProvider provider;
    private final Connection connection;
    private final Gson gson;
    private final Map<String, GraphQlFieldType> graphQlNameToFieldTypes;
    private final Map<String, GraphQlFieldType> sqlNameToFieldtypes;
    
    private static final Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    private final Map<String, GraphQlFieldType> fieldTypes;
    private final List<String> keyFields;
    private final Map<String, LookupInfo> lookupInfos;
    private final List<String> selectionSetList;
    // relay forward pagination arguments
    private final String PAGINATION_FIRST_PARAMETER = "first";
    private final String PAGINATION_AFTER_PARAMETER = "after";
    private Long limitQueryParamValueToPaginate = null; // limit query param value
    private Long cursorOffset = null; // offset relative to the query
    private boolean setNextCursor = false;
    private final String CURSOR_OFFSET_IN_OPAQUE_CURSOR = "cursorOffset";
    private Map<String, Object> opaqueCursor = new HashMap<>();
    private Map<String, GraphQlFieldDefinition> graphQLFieldsInfo;

    public GraphQlSqlResolverRunner(SqlDatabaseProvider provider, String graphQlTypeName, AppSyncSqlResolverInput input) {
        this.provider = provider;
        this.connection = provider.newConnection(input.getDbConnectionParameters());

        this.fieldTypes = input.getFieldTypes();
        this.keyFields = input.getKeyFields();
        this.lookupInfos = input.getLookupInfos();
        this.selectionSetList = input.getSelectionSetList();
        this.graphQLFieldsInfo = input.getGraphQLFieldsInfo();
        this.gson = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .serializeNulls()
                .registerTypeAdapter(SqlQueryStatement.WhereClause.class, 
                        new WhereClauseDeserializer(graphQlTypeName, fieldTypes, keyFields, lookupInfos,
                                provider.getVendor(), graphQLFieldsInfo))
                .create();
        this.graphQlNameToFieldTypes = fieldTypes;
        this.sqlNameToFieldtypes = fieldTypes.keySet().stream()
                .collect(Collectors.toMap(field -> Util.graphQlToSqlName(field, input.getGraphQLFieldsInfo()), fieldTypes::get));
        // This resolver assumes the database is at UTC+0. Setting the JVM to UTC+0 prevents JDBC from doing driver-dependent TimeZone conversions
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void close() {
        try {
            this.connection.close();
        } catch (SQLException e) {
            throw new GraphQlAdapterException("issue closing connection", e);
        }
    }

    @Override
    public QueryResultSet query(String sqlTableName, Map<String, Object> arguments,
                                List<String> selectedFields) throws GraphQlAdapterException {
        SqlQueryStatement.WhereClause whereClause = gson.fromJson(gson.toJsonTree(arguments.get("where")), SqlQueryStatement.WhereClause.class);
        
        Type orderByType = new TypeToken<List<Map<String, SqlQueryStatement.OrderBy>>>(){}.getType();
        List<Map<String, SqlQueryStatement.OrderBy>> orderBys = gson.fromJson(gson.toJsonTree(arguments.get("orderBy")), orderByType);
        if (orderBys != null) {
            List<Map<String, SqlQueryStatement.OrderBy>> convertedOrderBys = new ArrayList<>();
            
            for (Map<String, SqlQueryStatement.OrderBy> singleOrderBy : orderBys) {
                if (singleOrderBy.keySet().size() == 0) {
                    continue;
                }
                if (singleOrderBy.keySet().size() > 1) {
                    throw new GraphQlAdapterException("each OrderBy object can only specify one field to order by. got: " + singleOrderBy.keySet());
                }
                String graphQlName = singleOrderBy.keySet().iterator().next();
                SqlQueryStatement.OrderBy orderByValue;
                if (singleOrderBy.get(graphQlName) != null) {
                    orderByValue = singleOrderBy.get(graphQlName);
                } else {
                    orderByValue = new SqlQueryStatement.OrderBy();
                }
                // split global ID into components
                if (graphQlName.equals("id") || lookupInfos.containsKey(graphQlName)) {
                    List<String> keyColumns = this.keyFields;  // for 'id'
                    if (lookupInfos.containsKey(graphQlName)) {  // for lookups
                        keyColumns = lookupInfos.get(graphQlName).getKeyFields();
                    }
                    keyColumns.forEach(keyCol -> {
                        SqlQueryStatement.OrderBy keyColOrderBy = new SqlQueryStatement.OrderBy();
                        keyColOrderBy.setDirection(orderByValue.getDirection());
                        keyColOrderBy.setNulls(orderByValue.getNulls());
                        convertedOrderBys.add(Map.of(Util.graphQlToSqlName(keyCol, this.graphQLFieldsInfo), keyColOrderBy));
                    });
                } else {
                    if (!fieldTypes.containsKey(graphQlName)) {
                        throw new GraphQlAdapterException("tried to order by unknown field, must be defined in fieldTypes or as a lookup. field: " + graphQlName);
                    }
                    convertedOrderBys.add(Map.of(Util.graphQlToSqlName(graphQlName, this.graphQLFieldsInfo), orderByValue));
                }
            }
            orderBys = convertedOrderBys;
        }
        
        Long offset = null;
        if (arguments.get("offset") != null) {
            // could be Integer or Long, depending on AppSync or gson. Parse from String to handle both simply
            offset = Long.valueOf(arguments.get("offset").toString());
        }
        Long limit = null;
        if (arguments.get("limit") != null) {
            limit = Long.valueOf(arguments.get("limit").toString());
        }
        // Based on the relay specifications for pagination, the server use those two arguments to modify the edges returned by the connection,
        // returning edges after the "after"(PAGINATION_AFTER_PARAMETER) cursor,
        // and returning at most "first"(PAGINATION_FIRST_PARAMETER) edges.
        if (arguments.containsKey(PAGINATION_AFTER_PARAMETER)) {
            String opaqueCursorValue = new String(Base64.getDecoder().decode((String) arguments.get(PAGINATION_AFTER_PARAMETER)));
            this.cursorOffset  = getCursorOffsetFromOpaqueCursor(opaqueCursorValue);
        }
        Long firstEdges = null;
        if (arguments.containsKey(PAGINATION_FIRST_PARAMETER)) {
            firstEdges = Long.valueOf(arguments.get(PAGINATION_FIRST_PARAMETER).toString());
            if (firstEdges < 0 || firstEdges == null) {
                throw new GraphQlAdapterException(String.format("Pagination parameter %s, can not be negative or null", PAGINATION_FIRST_PARAMETER));
            }
            this.setNextCursor = true;
            if (Objects.equals(limit, firstEdges) && !arguments.containsKey(PAGINATION_AFTER_PARAMETER)) {
                this.setNextCursor = false;
            } else {
                this.limitQueryParamValueToPaginate = limit;
                // If edges contains more than "first" elements, than we return hasNextPage = true, otherwise false.
                limit = firstEdges + 1;
                if (this.limitQueryParamValueToPaginate != null) {
                    if (firstEdges > this.limitQueryParamValueToPaginate) {
                        throw new GraphQlAdapterException(String.format("Pagination parameter %s, can not be greater than %s value", PAGINATION_FIRST_PARAMETER, "limit"));
                    }
                    if (this.cursorOffset != null) {
                        if ((firstEdges + this.cursorOffset) > (this.limitQueryParamValueToPaginate)) {
                            limit =  this.limitQueryParamValueToPaginate - this.cursorOffset;
                            this.setNextCursor = false;
                        }
                    }
                }
            }
        }
        // we build the SQL with cursor offset + query param offset
        if (this.cursorOffset != null) {
            if (offset != null) {
                offset = offset + this.cursorOffset;
            } else {
                offset = this.cursorOffset;
            }
        }
        SqlQueryStatement statement = new SqlQueryStatement(sqlTableName, selectedFields, whereClause, orderBys, offset, limit, provider.getVendor());
        statement.setGraphQLFieldsInfo(this.graphQLFieldsInfo);
        return executeStatement(statement);
    }

    @Override
    public QueryResultSet mutation(String sqlTableName, LinkedHashMap<String, Object> newValues, 
                                   LinkedHashMap<String, Object> dmlWhere, List<String> returning,
                                   SqlStatementType statementType) throws GraphQlAdapterException {
        // SELECT statement to get results to return
        LinkedHashMap<String, Object> queryWhereMap = Util.convertToWhereClauseMap(dmlWhere);
        if (SqlStatementType.UPDATE.equals(statementType)) {
            for (String key : queryWhereMap.keySet()) {
                if (newValues.containsKey(key) && !newValues.get(key).equals(dmlWhere.get(key))) {
                    throw new GraphQlAdapterException("The " + key + " field isn't available for editing, because its values come from primary keys on the external system.");
                }
            }
        }
        SqlQueryStatement.WhereClause returningWhereClause = gson.fromJson(gson.toJsonTree(queryWhereMap), SqlQueryStatement.WhereClause.class);
        SqlQueryStatement returningStatement = new SqlQueryStatement(sqlTableName, returning, returningWhereClause, null, null, null, provider.getVendor());
        returningStatement.setGraphQLFieldsInfo(this.graphQLFieldsInfo);
        // DML statement with type mapped input values
        newValues = TypeMapper.convertToJdbcReadyType(newValues, this.graphQlNameToFieldTypes, provider.getVendor());
        newValues = Util.graphQlToSqlName(newValues, this.graphQLFieldsInfo);
        LinkedHashMap<String, Object> convertedDmlWhere = TypeMapper.convertToJdbcReadyType(dmlWhere, graphQlNameToFieldTypes, provider.getVendor());
        convertedDmlWhere = Util.graphQlToSqlName(convertedDmlWhere, this.graphQLFieldsInfo);
        SqlDmlStatement dmlStatement = new SqlDmlStatement(statementType, sqlTableName, newValues, convertedDmlWhere);

        if (SqlStatementType.DELETE.equals(statementType)) {
            // delete needs to query first before deleting, in order to return result
            QueryResultSet result = executeStatement(returningStatement);
            executeStatement(dmlStatement);
            return result;
        } else {
            executeStatement(dmlStatement);
            return executeStatement(returningStatement);
        }
    }

    private QueryResultSet executeStatement(SqlStatement statement) throws GraphQlAdapterException {
        try {
            PreparedStatement preparedStatement = connection.prepareStatement(statement.getPreparedStatement());
            List<Object> parameters = statement.getParameters();
            for (int i = 0; i < parameters.size(); i++) {
                Object param = parameters.get(i);
                preparedStatement.setObject(i+1, param);
            }
            AppSyncSqlResolverLambdaRequestHandler.LOGGER.log(String.format("executing query: %s. parameters: %s",
                    statement.getPreparedStatement(), statement.getParameters()));

            if (statement instanceof SqlDmlStatement) {
                int rowsAffected = preparedStatement.executeUpdate();
                if (rowsAffected == 0) {
                    // let caller handle because we don't have enough context here (don't have the full global ID)
                    throw new NoAffectedRowsException();
                }
                return null;
            }

            ResultSet rs = preparedStatement.executeQuery();
            List<LinkedHashMap<String, Object>> resultSet = new ArrayList<>();
            ResultSetMetaData resultSetMetaData = preparedStatement.getMetaData();
            boolean setEdgeCursorValue = this.selectionSetList.stream().filter(val -> val.equals("edges/cursor")).count() > 0;
            int rowCounter = 0;
            while (rs.next()) {
                rowCounter += 1;
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= resultSetMetaData.getColumnCount(); i++) {
                    String columnName = resultSetMetaData.getColumnName(i).toLowerCase();
                    Object value;
                    if (resultSetMetaData.getColumnType(i) == JDBCType.DATE.getVendorTypeNumber()) {
                        value = rs.getDate(i);
                    } else if (resultSetMetaData.getColumnType(i) == JDBCType.TIME.getVendorTypeNumber()) {
                        value = rs.getTime(i, utcCalendar);
                    } else if (resultSetMetaData.getColumnType(i) == JDBCType.TIMESTAMP.getVendorTypeNumber()) {
                        value = rs.getTimestamp(i, utcCalendar);
                    } else {
                        value = rs.getObject(i);
                    }
                    value = TypeMapper.convertFromJdbcResult(value, sqlNameToFieldtypes.get(columnName), provider.getVendor());
                    row.put(columnName, value);
                    // Based on the relay specifications for pagination, for each edge in the connection, we asked for a cursor.
                    // This cursor is an opaque string, and is precisely what we would pass to the after arg to paginate starting after this edge.
                    if (setEdgeCursorValue) {
                        Long cursorValue = this.cursorOffset != null ? this.cursorOffset + rowCounter : rowCounter;
                        String cursor = createOpaqueCursor(cursorValue);
                        row.put("edges/cursor", cursor);
                    }
                }
                resultSet.add(row);
            }
            rs.close();
            preparedStatement.close();
            Map<String, Object> pageInfo = new HashMap<String, Object>();
            Long limit = ((SqlQueryStatement) statement).getLimit();
            boolean hasNextPage = false;
            String cursor = null;
            if (!resultSet.isEmpty() && limit != null) {
                if (this.setNextCursor && resultSet.size() == limit) {
                    hasNextPage = true;
                    if (resultSet.size() > 1) {
                        resultSet.remove(resultSet.size() - 1);
                    }
                    Long cursorValue = this.cursorOffset != null ? this.cursorOffset + resultSet.size() : resultSet.size();
                    cursor = createOpaqueCursor(cursorValue);
                    if (this.limitQueryParamValueToPaginate != null) {
                        if (this.cursorOffset != null) {
                            if (this.limitQueryParamValueToPaginate.equals(Math.abs(this.cursorOffset)) ||
                                    this.limitQueryParamValueToPaginate.equals(Math.abs(cursorValue))) {
                                hasNextPage = false;
                                cursor = null;
                            }
                        }
                    }
                }
            }
            pageInfo.put("endCursor", cursor);
            pageInfo.put("hasNextPage", hasNextPage);
            return new QueryResultSet(resultSet, pageInfo);

        } catch (SQLException e) {
            String errorMessage = String.format("Error running SQL query: %s. Parameters: %s. Error: %s",
                    statement.getPreparedStatement(), statement.getParameters(), e.getMessage());
            throw new GraphQlAdapterException(errorMessage, e);
        }
    }

    /**
     * Opaque cursor is using the offset relative to the query (not the offset query param) as a part of the cursor
     *
     * @param cursorValue cursor offset relative to the query
     * @return String that represents opaque cursor: "{"cursorOffset":4}"
     */
    private String createOpaqueCursor(Long cursorValue) {
        if (cursorValue == null) {
            return null;
        }
        opaqueCursor.put(CURSOR_OFFSET_IN_OPAQUE_CURSOR, cursorValue);
        String jsonCursor = new Gson().toJson(opaqueCursor);
        return Base64.getEncoder().encodeToString(jsonCursor.getBytes());
    }


    /**
     * Get the cursorOffset from opaque cursor
     *
     * @param opaqueCursorValue opaque cursor: "{"cursorOffset":4}"
     * @return Long value that corresponds to the offset relative to the query
     */
    private Long getCursorOffsetFromOpaqueCursor(String opaqueCursorValue) {
        if (opaqueCursorValue == null) {
            return null;
        }
        opaqueCursor = gson.fromJson(opaqueCursorValue, Map.class);
        if (opaqueCursor.get(CURSOR_OFFSET_IN_OPAQUE_CURSOR) == null) {
            return null;
        }
        return (Long) opaqueCursor.get(CURSOR_OFFSET_IN_OPAQUE_CURSOR);
    }

}

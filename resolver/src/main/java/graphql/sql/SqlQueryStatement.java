package graphql.sql;

import graphql.GraphQlFieldType;
import graphql.sql.db.PostgreSqlDatabaseProvider;
import graphql.sql.db.SqlServerDatabaseProvider;
import util.Util;

import java.util.*;

public class SqlQueryStatement implements SqlStatement {
    private final String tableName;
    private final List<String> select;
    private final WhereClause where;
    private final List<Map<String, OrderBy>> orderByClause;
    private final Long offset;
    private final Long limit;
    private final String vendor;  // not preferred but Oracle and SQL Server have edge cases with syntax
    private Map<String, GraphQlFieldDefinition> graphQLFieldsInfo; // contains graphQL 'fieldType' and 'sqlColumnName' info
    
    private String preparedStatement;
    private final List<Object> parameters = new ArrayList<>();

    public SqlQueryStatement(String tableName, List<String> select,
                             WhereClause whereClause, List<Map<String, OrderBy>> orderByClause,
                             Long offset, Long limit, String vendor) {
        this.tableName = tableName;
        this.select = select;
        this.where = whereClause;
        this.orderByClause = orderByClause;
        this.offset = offset;
        this.limit = limit;
        this.vendor = vendor;
    }

    public String getPreparedStatement() {
        build();
        return preparedStatement;
    }

    public List<Object> getParameters() {
        build();
        return new ArrayList<>(parameters);
    }

    public Long getLimit() {
        return limit;
    }

    public Long getOffset() {
        return offset;
    }

    private void build() {
        if (preparedStatement != null) {
            return;
        }
        buildSelect();
    }

    public Map<String, GraphQlFieldDefinition> getGraphQLFieldsInfo() {
        return graphQLFieldsInfo;
    }

    public void setGraphQLFieldsInfo(Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
        this.graphQLFieldsInfo = graphQLFieldsInfo;
    }

    private void buildSelect() {
        StringBuilder sql = new StringBuilder();

        // SELECT
        sql.append("SELECT ");
        Iterator<String> it = select.iterator();
        while (it.hasNext()) {
            sql.append(it.next());
            if (it.hasNext()) {
                sql.append(", ");
            }
        }

        // FROM
        sql.append(" FROM ");
        sql.append(tableName);

        // WHERE
        if (where != null && !(where.filter.isEmpty() && where.and == null && where.or == null && where.not == null)) {
            sql.append(" WHERE ");
            where.addClause(sql, parameters, this.graphQLFieldsInfo);
        }

        // ORDER BY
        if (orderByClause != null && !orderByClause.isEmpty()) {
            sql.append(" ORDER BY ");
            Iterator<Map<String, OrderBy>> iterator = orderByClause.iterator();
            while (iterator.hasNext()) {
                Map<String, OrderBy> orderByEntry = iterator.next();
                String fieldName = orderByEntry.keySet().iterator().next();
                OrderBy orderBy = orderByEntry.get(fieldName);

                // database agnostic version of ORDER BY my_field DESC, NULLS FIRST:
                // ORDER BY (CASE WHEN my_field IS NULL THEN 0 ELSE 1 END), my_field DESC
                if (orderBy.getNulls() != null) {
                    String nullValue = orderBy.getNulls().equals("NULLS_FIRST") ? "0" : "1";
                    String nonNullValue = orderBy.getNulls().equals("NULLS_FIRST") ? "1" : "0";
                    sql.append("( CASE WHEN ").append(fieldName)
                            .append(" IS NULL THEN ").append(nullValue)
                            .append(" ELSE ").append(nonNullValue)
                            .append(" END ), ");
                }
                
                sql.append(fieldName);
                if (orderBy.getDirection() != null) {
                    sql.append(" ");
                    sql.append(orderBy.getDirection());
                }
                if (iterator.hasNext()) {
                    sql.append(", ");
                }
            }
        }
        else {
            if (vendor.equals(SqlServerDatabaseProvider.VENDOR) && (limit != null || offset != null)) {
                // SQL Server requires ORDER BY for limit/fetch
                sql.append(" ORDER BY (SELECT NULL)");
            }
        }

        // OFFSET
        if (offset != null) {
            sql.append(" OFFSET ");
            sql.append(offset);
            sql.append(" ROWS");
        }

        // LIMIT
        if (limit != null) {
            // FETCH NEXT is in SQL standard while LIMIT is not. Functionally the same
            sql.append(" FETCH NEXT ");
            sql.append(limit);
            sql.append(" ROWS ONLY");
        }
        
        preparedStatement = sql.toString();
    }

    public static class WhereClause {
        private Map<String, Map<String, Object>> filter;
        private List<WhereClause> and;
        private List<WhereClause> or;
        private WhereClause not;
        
        private Map<String, GraphQlFieldType> fieldTypes;
        private String vendor;

        public void addClause(StringBuilder sql, List<Object> parameters, Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
            boolean added = false;
            if (filter != null && !filter.isEmpty()) {
                added = true;
                Iterator<String> columnNames = filter.keySet().iterator();
                while (columnNames.hasNext()) {
                    String columnName = columnNames.next();
                    Map<String, Object> columnFilter = filter.get(columnName);
                    if (columnFilter != null) {
                        WhereClauseFilter columnFilterClause = new WhereClauseFilter(columnFilter, fieldTypes, vendor);
                        columnFilterClause.addFilter(sql, columnName, parameters, graphQLFieldsInfo);
                    }
                    if (columnNames.hasNext()) {
                        sql.append(" AND ");
                    }
                }
            }

            if (and != null) {
                added = added(added);
                addBooleanClauses(sql, " AND ", and, parameters, graphQLFieldsInfo);
            }

            if (or != null) {
                added = added(added);
                addBooleanClauses(sql, " OR ", or, parameters, graphQLFieldsInfo);
            }

            if (not != null) {
                added(added);
                sql.append("NOT (");
                not.addClause(sql, parameters, graphQLFieldsInfo);
                sql.append(")");
            }
        }

        private void addBooleanClauses(StringBuilder sql, String booleanOperator, List<WhereClause> booleanClauses, List<Object> parameters,  Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
            Iterator<WhereClause> filterClauses = booleanClauses.iterator();
            while (filterClauses.hasNext()) {
                sql.append('(');
                WhereClause filterClause = filterClauses.next();
                filterClause.addClause(sql, parameters, graphQLFieldsInfo);
                sql.append(')');

                if (filterClauses.hasNext()) {
                    sql.append(booleanOperator);
                }
            }
        }

        private boolean added(boolean added) {
            if (added) {
                throw new IllegalArgumentException("Invalid GraphQL where clause query expression");
            }
            return true;
        }

        public Map<String, Map<String, Object>> getFilter() {
            return filter;
        }

        public void setFilter(Map<String, Map<String, Object>> filter) {
            this.filter = filter;
        }

        public List<WhereClause> getAnd() {
            return and;
        }

        public void setAnd(List<WhereClause> and) {
            this.and = and;
        }

        public List<WhereClause> getOr() {
            return or;
        }

        public void setOr(List<WhereClause> or) {
            this.or = or;
        }

        public WhereClause getNot() {
            return not;
        }

        public void setNot(WhereClause not) {
            this.not = not;
        }

        public Map<String, GraphQlFieldType> getFieldTypes() {
            return fieldTypes;
        }

        public void setFieldTypes(Map<String, GraphQlFieldType> fieldTypes) {
            this.fieldTypes = fieldTypes;
        }

        public String getVendor() {
            return vendor;
        }

        public void setVendor(String vendor) {
            this.vendor = vendor;
        }
    }

    public static class OrderBy {
        private String direction;
        private String nulls;

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getNulls() {
            return nulls;
        }

        public void setNulls(String nulls) {
            this.nulls = nulls;
        }
    }

    public enum FilterOperator {
        in("IN"),
        eq("="),
        ge(">="),
        gt(">"),
        le("<="),
        lt("<"),
        ne("<>"),
        like("LIKE");
        FilterOperator(String sqlOperator) {
            this.sqlOperator = sqlOperator;
        }

        private final String sqlOperator;
    }

    public static class WhereClauseFilter {
        private final Map<String, Object> filter;
        private final Map<String, GraphQlFieldType> fieldTypes;
        private final String vendor;

        public WhereClauseFilter(Map<String, Object> filter, Map<String, GraphQlFieldType> fieldTypes, String vendor) {
            this.filter = filter;
            this.fieldTypes = fieldTypes;
            this.vendor = vendor;
        }

        public void addFilter(StringBuilder sql, String columnName, List<Object> parameters, Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
            if (filter.size() != 1) {
                throw new IllegalArgumentException("Invalid GraphQL where clause filter query expression");
            }
            FilterOperator filterOperator = FilterOperator.valueOf(filter.keySet().iterator().next());
            Object value = filter.values().iterator().next();
            addFilter(sql, columnName, filterOperator, value, parameters, graphQLFieldsInfo);
        }

        private void addFilter(StringBuilder sql, String columnName, FilterOperator filterOperator,
                               Object value, List<Object> parameters, Map<String, GraphQlFieldDefinition> graphQLFieldsInfo) {
            if (value == null && filterOperator != FilterOperator.eq && filterOperator != FilterOperator.ne) {
                throw new IllegalArgumentException("Invalid GraphQL where clause filter query: " +
                        "null isn't allowed for filter operator: " + filterOperator);
            }
            
            boolean onPostgresJson = false;

            // very hacky, needed to handle JSON data type in PostgreSQL
            // cannot compare JSON types directly, need to cast to JSONB
            if (vendor.equals(PostgreSqlDatabaseProvider.VENDOR) && 
                    fieldTypes.get(Util.sqlToGraphQlName(columnName,graphQLFieldsInfo)).equals(GraphQlFieldType.AWSJSON)) {
                onPostgresJson = true;
                columnName += "::jsonb";
            }
            
            sql.append(columnName);
            sql.append(" ");
            switch (filterOperator) {
                case in:
                    sql.append(filterOperator.sqlOperator);
                    sql.append(" (");
                    Iterator<Object> values = ((Collection<Object>)value).iterator();
                    while (values.hasNext()) {
                        addValue(sql, values.next(), parameters, onPostgresJson);
                        if (values.hasNext()) {
                            sql.append(", ");
                        }
                    }
                    sql.append(")");
                    break;
                case like:
                    sql.append(filterOperator.sqlOperator).append(' ');
                    addValue(sql, value, parameters, onPostgresJson);
                    break;
                case eq:
                case ne:
                    if (value == null) {
                        sql.append("IS ");
                        if (filterOperator == FilterOperator.ne) {
                            sql.append("NOT ");
                        }
                        sql.append("NULL");
                        break;
                    }
                case ge:
                case gt:
                case le:
                case lt:
                    sql.append(filterOperator.sqlOperator).append(' ');
                    addValue(sql, value, parameters, onPostgresJson);
                    break;
            }
        }

        private void addValue(StringBuilder sql, Object value, List<Object> parameters, boolean onPostgresJson) {
            sql.append("?");
            if (onPostgresJson) {
                sql.append("::jsonb");
            }
            parameters.add(value);
        }
    }
}

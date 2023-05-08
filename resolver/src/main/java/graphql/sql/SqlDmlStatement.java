package graphql.sql;

import graphql.GraphQlAdapterException;

import java.util.*;

public class SqlDmlStatement implements SqlStatement {
    private final String tableName;
    private final SqlStatementType statementType;
    private final LinkedHashMap<String, Object> newValues;
    private final LinkedHashMap<String, Object> where;
    
    private String preparedStatement;
    private List<Object> parameters = new ArrayList<>();

    public SqlDmlStatement(SqlStatementType statementType, String tableName, LinkedHashMap<String, Object> newValues, LinkedHashMap<String, Object> where) {
        this.statementType = statementType;
        this.tableName = tableName;
        this.newValues = newValues;
        this.where = where;
    }

    public String getPreparedStatement() {
        build();
        return preparedStatement;
    }

    public List<Object> getParameters() {
        build();
        return new ArrayList<>(parameters);
    }

    private void build() {
        if (preparedStatement != null) {
            return;
        }
        parameters = new ArrayList<>();

        switch (statementType) {
            case INSERT:
                buildInsert();
                break;
            case UPDATE:
                buildUpdate();
                break;
            case DELETE:
                buildDelete();
                break;
        }
    }
    
    private void buildInsert() {
        StringBuilder sql = new StringBuilder();

        // INSERT
        sql.append("INSERT INTO ").append(tableName);

        // specify column names and values
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        Iterator<Map.Entry<String, Object>> iterator = newValues.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            parameters.add(entry.getValue());
            columns.append(entry.getKey());
            values.append("?");
            if (iterator.hasNext()) {
                columns.append(", ");
                values.append(", ");
            }
        }

        sql.append(" (").append(columns).append(") ");
        sql.append("VALUES ").append("(").append(values).append(") ");
        
        preparedStatement = sql.toString();
    }

    private void buildUpdate() {
        StringBuilder sql = new StringBuilder();

        // UPDATE
        sql.append("UPDATE ").append(tableName);

        // build SET portion
        sql.append(" SET ");
        Iterator<Map.Entry<String, Object>> columnValueIt = newValues.entrySet().iterator();
        while (columnValueIt.hasNext()) {
            Map.Entry<String, Object> entry = columnValueIt.next();
            String columnName = entry.getKey();
            Object columnValue = entry.getValue();

            sql.append(columnName).append(" = ?");
            parameters.add(columnValue);

            if (columnValueIt.hasNext()) {
                sql.append(", ");
            }
        }
        buildWhereClause(sql);
        
        preparedStatement = sql.toString();
    }

    private void buildDelete() {
        StringBuilder sql = new StringBuilder();

        // DELETE
        sql.append("DELETE FROM ").append(tableName);
        buildWhereClause(sql);

        preparedStatement = sql.toString();
    }

    private void buildWhereClause(StringBuilder sql) {
        sql.append(" WHERE ");
        Iterator<Map.Entry<String, Object>> iterator = where.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Object> entry = iterator.next();
            if (entry.getValue() == null) {
                throw new GraphQlAdapterException("null values in key column are not allowed");
            }
            sql.append("(").append(entry.getKey()).append(" = ?) ");
            parameters.add(entry.getValue());
            if (iterator.hasNext()) {
                sql.append("AND ");
            }
        }
    }
}

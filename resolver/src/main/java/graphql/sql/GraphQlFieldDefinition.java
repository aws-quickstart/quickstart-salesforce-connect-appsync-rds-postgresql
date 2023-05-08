package graphql.sql;

import graphql.GraphQlFieldType;

/**
 * Define the fieldType and sqlColumnName defined in the System Manager 'graphQLFields' parameter:
 * "graphQLFields": {
 *     "OrderId": {
 *       "fieldType": "String",
 *       "columnName": "order_id"
 *     },
 *     "OrderDate": {
 *       "fieldType": "AWSDateTime",
 *       "columnName": "order_date"
 *     },
 *     ...
 *     ...
 */
public class GraphQlFieldDefinition {
    private GraphQlFieldType fieldType;
    private String columnName;

    public GraphQlFieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(GraphQlFieldType fieldType) {
        this.fieldType = fieldType;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }
}

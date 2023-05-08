package graphql;

import graphql.sql.SqlStatementType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface GraphQlResolverRunner {
    QueryResultSet query(String objectName, Map<String, Object> arguments, 
                         List<String> selectedFields) throws GraphQlAdapterException;

    QueryResultSet mutation(String objectName, LinkedHashMap<String, Object> newValues, 
                            LinkedHashMap<String, Object> arguments, List<String> select,
                            SqlStatementType statementType) throws GraphQlAdapterException;
}

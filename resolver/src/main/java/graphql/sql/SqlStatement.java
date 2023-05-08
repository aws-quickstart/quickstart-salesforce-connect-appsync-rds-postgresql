package graphql.sql;

import java.util.List;

public interface SqlStatement {
    String getPreparedStatement();
    List<Object> getParameters();
}

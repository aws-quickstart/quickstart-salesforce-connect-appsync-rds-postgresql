package graphql.sql.db;

import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;


public interface SqlDatabaseProvider {
    static Connection getConnection(String url, Properties props) {
        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException e) {
            throw new GraphQlAdapterException("Can't get connection for: " + url, e);
        }
    }

    String getVendor();
    Connection newConnection(DatabaseConnectionParameters parameters);
}

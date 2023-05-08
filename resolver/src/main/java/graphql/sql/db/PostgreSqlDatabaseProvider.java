package graphql.sql.db;

import com.google.common.annotations.VisibleForTesting;
import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;

import java.sql.*;
import java.util.*;

public class PostgreSqlDatabaseProvider implements SqlDatabaseProvider {
    public static final String VENDOR = "postgres";

    @Override
    public String getVendor() {
        return VENDOR;
    }

    @Override
    public Connection newConnection(DatabaseConnectionParameters parameters) {
        return getConnection(parameters);
    }

    @VisibleForTesting
    public static Connection getConnection(DatabaseConnectionParameters parameters) throws GraphQlAdapterException {
        String url = "jdbc:postgresql://" + parameters.getHost() + ":" + parameters.getPort() + "/" + parameters.getDbname();
        Properties props = new Properties();
        props.setProperty("user", parameters.getUsername());
        props.setProperty("password", parameters.getPassword());
        props.setProperty("stringtype", "unspecified");
        
        Connection connection = SqlDatabaseProvider.getConnection(url, props);
        try {
            // prevents JDBC from converting time zones
            connection.createStatement().execute("set time zone 'UTC'");
        } catch (SQLException e) {
            throw new GraphQlAdapterException(e);
        }
        return connection;
    }
}

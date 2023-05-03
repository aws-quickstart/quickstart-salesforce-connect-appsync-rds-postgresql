package graphql.sql.db;

import com.google.common.annotations.VisibleForTesting;
import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public class OracleDatabaseProvider implements SqlDatabaseProvider {
    public static final String VENDOR = "Oracle";

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
        String url = "jdbc:oracle:thin:@" + parameters.getHost() + ":" + parameters.getPort() + ":" + parameters.getDbname();
        Properties props = new Properties();
        props.setProperty("user", parameters.getUsername());
        props.setProperty("password", parameters.getPassword());
        Connection connection = SqlDatabaseProvider.getConnection(url, props);
        try {
            // prevents JDBC from converting time zones
            connection.createStatement().execute("ALTER SESSION SET TIME_ZONE='utc'");
        } catch (SQLException e) {
            throw new GraphQlAdapterException(e);
        }
        return connection;
    }
}

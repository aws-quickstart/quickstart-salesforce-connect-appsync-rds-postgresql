package graphql.sql.db;

import com.google.common.annotations.VisibleForTesting;
import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;

import java.sql.Connection;
import java.util.Properties;

public class SqlServerDatabaseProvider implements SqlDatabaseProvider {
    public static final String VENDOR = "MSSQLServer";

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
        String url = String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true",
                parameters.getHost(), parameters.getPort(), parameters.getDbname());
        Properties props = new Properties();
        props.setProperty("user", parameters.getUsername());
        props.setProperty("password", parameters.getPassword());
        return SqlDatabaseProvider.getConnection(url, props);
    }
}

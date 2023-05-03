package graphql.sql.db;

import graphql.DatabaseConnectionParameters;

import java.sql.Connection;

public class InMemoryDatabaseProvider implements SqlDatabaseProvider {
    public static final String VENDOR = "inmemory";
    private final String vendor;

    public InMemoryDatabaseProvider(String vendor) {
        this.vendor = vendor;
    }

    @Override
    public String getVendor() {
        return VENDOR;
    }

    @Override
    public Connection newConnection(DatabaseConnectionParameters parameters) {
        // vendor-specific
        String url = String.format("jdbc:h2:mem:%s;MODE=%s", parameters.getDbname(), vendor);
        return SqlDatabaseProvider.getConnection(url, null);
    }

    public static Connection getVendorAgnosticConnection(DatabaseConnectionParameters parameters) {
        String url = String.format("jdbc:h2:mem:%s", parameters.getDbname());
        return SqlDatabaseProvider.getConnection(url, null);
    }
}

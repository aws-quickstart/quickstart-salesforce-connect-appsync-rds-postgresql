package graphql.sql.db;

import com.google.common.annotations.VisibleForTesting;
import graphql.GraphQlAdapterException;

import java.util.HashMap;
import java.util.Map;

/**
 * Manual dependency injection. DI libraries add too much overhead for Lambda, and usage is very simple
 */
public class SqlDatabaseProviderFactory {
    private static Map<String, SqlDatabaseProvider>  vendorToProvider;
    static {
        resetProviders();
    }
    
    @VisibleForTesting
    public static void resetProviders() {
        vendorToProvider = new HashMap<>();
        vendorToProvider.put(PostgreSqlDatabaseProvider.VENDOR, new PostgreSqlDatabaseProvider());
        vendorToProvider.put(OracleDatabaseProvider.VENDOR, new OracleDatabaseProvider());
        vendorToProvider.put(SqlServerDatabaseProvider.VENDOR, new SqlServerDatabaseProvider());
    }
    
    public static void setProvider(String vendor, SqlDatabaseProvider provider) {
        vendorToProvider.put(vendor, provider);
    } 
    
    public static SqlDatabaseProvider getProvider(String vendor) {
        if (vendorToProvider.get(vendor) == null) {
            throw new GraphQlAdapterException("unknown database vendor: " + vendor);
        }
        return vendorToProvider.get(vendor);
    }
}

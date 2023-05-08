package graphql.sql.db;

import graphql.DatabaseConnectionParameters;
import graphql.GraphQlAdapterException;
import graphql.GraphQlTestRunner;
import graphql.appsync.DummySystemsManagerRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.provider.Arguments;
import software.amazon.awssdk.utils.Pair;
import util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseDatabaseTest {
    public static final String inMemoryPrefix = "inmemory_";
    static {
        // H2 time zone is JVM time zone during initialization
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
    
    private DummySystemsManagerRetriever systemsManagerRetriever;
    protected GraphQlTestRunner runner;
    private Connection connection;
    private String databaseName;
    
    @BeforeEach
    public void setup(TestInfo testInfo) {
        systemsManagerRetriever = new DummySystemsManagerRetriever();
        runner =  new GraphQlTestRunner(systemsManagerRetriever);
        
        databaseName = testInfo.getDisplayName().split(" ")[2];
        if (databaseName.startsWith(inMemoryPrefix)) {
            String vendor = databaseName.replace(inMemoryPrefix, "");
            SqlDatabaseProviderFactory.setProvider(InMemoryDatabaseProvider.VENDOR, new InMemoryDatabaseProvider(vendor));
        }
        setupConnection(testInfo);
        init();
    }
    
    @AfterEach
    public void teardown() {
        cleanup();
        SqlDatabaseProviderFactory.resetProviders();
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        systemsManagerRetriever = null;
        runner = null;
        connection = null;
        databaseName = null;
    }

    /**
     * This gets a direct connection to the database, for setting up the database prior to the test
     */
    private void setupConnection(TestInfo testInfo) {
        DatabaseConnectionParameters params = getDatabaseConnectionParameters(testInfo, databaseName);
        switch (params.getEngine()) {
            case InMemoryDatabaseProvider.VENDOR:
                connection = InMemoryDatabaseProvider.getVendorAgnosticConnection(params);
                break;
            case SqlServerDatabaseProvider.VENDOR:
                connection = SqlServerDatabaseProvider.getConnection(params);
                break;
            case PostgreSqlDatabaseProvider.VENDOR:
                connection = PostgreSqlDatabaseProvider.getConnection(params);
                break;
            case OracleDatabaseProvider.VENDOR:
                connection = OracleDatabaseProvider.getConnection(params);
                break;
            default:
                throw new RuntimeException("unsupported vendor " + params.getEngine());
        }
    }
    
    private void init() {
        String suffix = databaseName.startsWith(inMemoryPrefix) ? "inmemory" : databaseName;
        suffix += ".init.sql";
        findAndExecuteSqlFiles(suffix);
    }

    private void cleanup() {
        String suffix = databaseName.startsWith(inMemoryPrefix) ? "inmemory" : databaseName;
        suffix += ".teardown.sql";
        findAndExecuteSqlFiles(suffix);
    }
    
    private void findAndExecuteSqlFiles(String suffix) {
        // load tables + data into in memory database
        String testDataRoot = "src/test/resources/test_data/";
        try (Stream<Path> stream = Files.walk(Path.of(testDataRoot), 2)) {
            Statement statement = connection.createStatement();
            // order of sql file execution matters
            List<Path> testSqlPaths = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(suffix))
                    .sorted().collect(Collectors.toList());

            for (Path path : testSqlPaths) {
                String sqlString = Files.readString(path, StandardCharsets.UTF_8);
                List<String> sqlStatements = List.of(sqlString.split(";"));
                for (String sqlStatement : sqlStatements) {  // Oracle JDBC doesn't allow multiple queries at once
                    sqlStatement = sqlStatement.trim();
                    if (!sqlStatement.isEmpty()) {
                        statement.addBatch(sqlStatement);
                    }
                }
                statement.executeBatch();
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Set and return database connection parameters for this test
     */
    private DatabaseConnectionParameters getDatabaseConnectionParameters(TestInfo testInfo, String databaseName) {
        if (databaseName.contains(inMemoryPrefix)) {
            DatabaseConnectionParameters params = new DatabaseConnectionParameters();
            params.setEngine(InMemoryDatabaseProvider.VENDOR);
            String testName = testInfo.getDisplayName().split(" ")[1].replace(",", "");

            // each in memory test will have its own database
            String inMemoryDatabaseName = String.format("%s+%s", testName, databaseName);
            params.setDbname(inMemoryDatabaseName);
            systemsManagerRetriever.setSecret(inMemoryDatabaseName);
            return params;
        }
        
        String credentialRoot = System.getProperty("user.home") + "/resolver/" + databaseName + ".json";
        systemsManagerRetriever.setSecret(databaseName);
        try {
            String paramJson = Files.readString(Path.of(credentialRoot), StandardCharsets.UTF_8);
            return Util.GSON.fromJson(paramJson, DatabaseConnectionParameters.class);
        } catch (NoSuchFileException e) {
            throw new GraphQlAdapterException("missing database credentials for local test at " + credentialRoot);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper to get the test arguments for the parameterized tests in the subclasses
     */
    public static Stream<Arguments> testCaseArgs(String queryFolderName) {
        List<String> baseTestCaseNames = new ArrayList<>();
        String queriesPath = "src/test/resources/appsync/" + queryFolderName;
        try (Stream<Path> stream = Files.walk(Path.of(queriesPath), 1)) {
            baseTestCaseNames = stream.filter(file -> !Files.isDirectory(file))
                    .map(file -> file.getFileName().toString())
                    .filter(fileName -> fileName.endsWith(".appsync.json"))
                    .map(fileName -> fileName.replace(".appsync.json", ""))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        List<String> databasesToTestAgainst = new ArrayList<>(Arrays.asList(
                inMemoryPrefix + PostgreSqlDatabaseProvider.VENDOR,  // H2 has various compatibility modes
                inMemoryPrefix + OracleDatabaseProvider.VENDOR,
                inMemoryPrefix + SqlServerDatabaseProvider.VENDOR,
                PostgreSqlDatabaseProvider.VENDOR.toLowerCase(),
                OracleDatabaseProvider.VENDOR.toLowerCase(),
                SqlServerDatabaseProvider.VENDOR.toLowerCase()
        ));
        
        Set<Pair<String, String>> testsToIgnore = Set.of(
                // Oracle doesn't support date time as ID
                Pair.of("testCreateReturnGlobalIdDateTime", OracleDatabaseProvider.VENDOR.toLowerCase()),
                Pair.of("testDeleteReturnGlobalId", OracleDatabaseProvider.VENDOR.toLowerCase()),
                Pair.of("testUpdateReturnGlobalId", OracleDatabaseProvider.VENDOR.toLowerCase()),
                Pair.of("testQueryNodeDateTimeId", OracleDatabaseProvider.VENDOR.toLowerCase()),
                // Oracle doesn't have TIME field
                Pair.of("testQueryWhereEq", OracleDatabaseProvider.VENDOR.toLowerCase()),
                Pair.of("testLookupResultDateTime", OracleDatabaseProvider.VENDOR.toLowerCase())
        );
        List<Arguments> parameterizedTestCases = new ArrayList<>();
        for (String database : databasesToTestAgainst) {
            for (String testCaseName : baseTestCaseNames) {
                if (testsToIgnore.contains(Pair.of(testCaseName, database))) {
                    continue;
                }
                parameterizedTestCases.add(Arguments.of(testCaseName, database));
            }
        }
        return parameterizedTestCases.stream();
    }
}

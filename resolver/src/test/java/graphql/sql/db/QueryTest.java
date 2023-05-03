package graphql.sql.db;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

public class QueryTest extends BaseDatabaseTest {
    private static final String queryFolder = "query";

    @ParameterizedTest
    @MethodSource("testCases")
    void testQueryMultiple(String testName, String databaseName) throws IOException {
        runner.run(testName, queryFolder);
    }

    private static Stream<Arguments> testCases() {
        return BaseDatabaseTest.testCaseArgs(queryFolder);
    }
}

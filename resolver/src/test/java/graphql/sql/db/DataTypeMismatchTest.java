package graphql.sql.db;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

public class DataTypeMismatchTest extends BaseDatabaseTest {
    private static final String queryFolder = "data_type_mismatch";

    @ParameterizedTest
    @MethodSource("testCases")
    void testCreate(String testName, String databaseName) throws IOException {
        runner.run(testName, queryFolder);
    }

    private static Stream<Arguments> testCases() {
        return testCaseArgs(queryFolder);
    }
}

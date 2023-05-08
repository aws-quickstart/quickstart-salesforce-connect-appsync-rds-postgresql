package graphql.sql.db;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

public class DeleteTest extends BaseDatabaseTest {
    private static final String queryFolder = "delete";

    @ParameterizedTest
    @MethodSource("testCases")
    void testDelete(String testName, String databaseName) throws IOException {
        runner.run(testName, queryFolder);
    }

    private static Stream<Arguments> testCases() {
        return testCaseArgs(queryFolder);
    }
}

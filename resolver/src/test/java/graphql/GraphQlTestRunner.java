package graphql;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import graphql.appsync.AppSyncSqlResolverInput;
import graphql.appsync.AppSyncSqlResolverLambdaRequestHandler;
import graphql.appsync.DummySecretsManagerRetriever;
import graphql.appsync.DummySystemsManagerRetriever;
import util.Util;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class GraphQlTestRunner {
    public static final String TEST_DATA_ROOT = "/appsync/";
    
    private final DummySystemsManagerRetriever systemsManagerRetriever;
    public GraphQlTestRunner(DummySystemsManagerRetriever systemsManagerRetriever) {
        this.systemsManagerRetriever = systemsManagerRetriever;
    }

    public void run(String testName, String testDataFolder) throws IOException, GraphQlAdapterException {
        final String appSyncQueryResourcePath = getAppSyncQueryResourcePath(testDataFolder, testName);
        
        InputStream appSyncQueryResource = getClass().getResourceAsStream(appSyncQueryResourcePath);
        if (appSyncQueryResource == null) {
            throw new IOException("can't find AppSync query resource to test: " + appSyncQueryResourcePath);
        }
        final String appSyncQuery = new String(appSyncQueryResource.readAllBytes(), StandardCharsets.UTF_8);

        final String expectedResultPath = getExpectedResultSetResourcePath(testDataFolder, testName);
        InputStream expectedResultResource = getClass().getResourceAsStream(expectedResultPath);
        if (expectedResultResource == null) {
            throw new IllegalArgumentException("can't find expected query result for test: " + expectedResultPath);
        }
        String expectedResultSet = new String(expectedResultResource.readAllBytes(), StandardCharsets.UTF_8);
        
        runAndAssertAppSyncRequest(appSyncQuery, expectedResultSet);
    }

    private void runAndAssertAppSyncRequest(String appSyncQuery, String testCaseString) {
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> appSyncQueryMap = Util.GSON.fromJson(appSyncQuery, mapType);
        
        ExpectedResult expectedResult = Util.GSON.fromJson(testCaseString, ExpectedResult.class);
        AppSyncSqlResolverLambdaRequestHandler requestHandler = new AppSyncSqlResolverLambdaRequestHandler(
                DummySecretsManagerRetriever.INSTANCE, systemsManagerRetriever);
        
        if (expectedResult.getExpectedErrorMessage() == null) {
            Object actualResultSet = requestHandler.handleRequest(appSyncQueryMap, null);
            AppSyncSqlResolverInput input = new AppSyncSqlResolverInput(appSyncQueryMap, false);
            assertResult(input.getFieldName(), actualResultSet, expectedResult);
        } else {
            try {
                requestHandler.handleRequest(appSyncQueryMap, null);
                fail("expected request to fail, but it did not");
            } catch (GraphQlAdapterException e) {
                // better than AssertEquals because it gives the stack trace of the original exception
                if (!expectedResult.getExpectedErrorMessage().equals(e.getMessage())) {
                    throw new AssertionError(
                            String.format("unexpected error message.\nexpected: %s.\nactual  : %s", 
                                    expectedResult.getExpectedErrorMessage(), 
                                    e.getMessage()), 
                                    e);
                }
            }
        }
    }

    private void assertResult(String queryOperationName, Object actualResult, ExpectedResult expectedResult) {
        Object expectedObjects;
        
        if (expectedResult.getExpectedResultSingular() != null) {
            // query by ID, mutation
            expectedObjects = expectedResult.getExpectedResultSingular();
        } else {  // multi result from standard query
            // wrap expected result in node/edges form
            List<LinkedHashMap<String, Object>> nodeWrapped = expectedResult.getExpectedResultMulti().stream()
                    .map(item -> {
                        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                        map.put("node", item);
                        return map;
                    }).collect(Collectors.toList());
            LinkedHashMap<String, Object> formattedResult = new LinkedHashMap<>();
            formattedResult.put("edges", nodeWrapped);
            LinkedHashMap<String, Object> mapPageInfo = new LinkedHashMap<>();
            mapPageInfo.put("hasNextPage",false);
            mapPageInfo.put("endCursor", null);
            formattedResult.put("pageInfo", mapPageInfo);
            expectedObjects = formattedResult;
        }
        // Normalizing data types to JSON
        JsonElement actualResultJson = Util.GSON.toJsonTree(actualResult);
        Map<String, Object> actualResultNormalized = (Map<String, Object>) Util.GSON.fromJson(actualResultJson, Object.class);
        JsonElement expectedResultJson = Util.GSON.toJsonTree(expectedObjects);
        Map<String, Object> expectedResultNormalized = (Map<String, Object>) Util.GSON.fromJson(expectedResultJson, Object.class);
        
        assertEquals(expectedResultNormalized, actualResultNormalized, 
                "unexpected result for GraphQL query: " + queryOperationName);
    }

    private String getAppSyncQueryResourcePath(String schemaName, String methodName) {
        return TEST_DATA_ROOT + schemaName + "/" + methodName + ".appsync.json";
    }

    private String getExpectedResultSetResourcePath(String schemaName, String methodName) {
        return TEST_DATA_ROOT + schemaName + "/" + methodName + ".result.json";
    }
}

package graphql;

import java.util.LinkedHashMap;
import java.util.List;

public class ExpectedResult {
    private List<LinkedHashMap<String, Object>> expectedResultMulti;
    private LinkedHashMap<String, Object> expectedResultSingular;
    private String expectedErrorMessage;
    
    public List<LinkedHashMap<String, Object>> getExpectedResultMulti() {
        return this.expectedResultMulti;
    }

    public void setExpectedResultMulti(List<LinkedHashMap<String, Object>> expectedResultMulti) {
        this.expectedResultMulti = expectedResultMulti;
    }

    public LinkedHashMap<String, Object> getExpectedResultSingular() {
        return expectedResultSingular;
    }

    public void setExpectedResultSingular(LinkedHashMap<String, Object> expectedResultSingular) {
        this.expectedResultSingular = expectedResultSingular;
    }

    public String getExpectedErrorMessage() {
        return expectedErrorMessage;
    }

    public void setExpectedErrorMessage(String expectedErrorMessage) {
        this.expectedErrorMessage = expectedErrorMessage;
    }
}

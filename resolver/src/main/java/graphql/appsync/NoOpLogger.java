package graphql.appsync;


import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class NoOpLogger implements LambdaLogger {
    @Override
    public void log(String s) {
        // NO OP
    }

    @Override
    public void log(byte[] bytes) {
        // NO OP
    }
}

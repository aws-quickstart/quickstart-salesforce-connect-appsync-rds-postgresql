import com.google.gson.reflect.TypeToken;
import graphql.appsync.AppSyncSqlResolverLambdaRequestHandler;
import util.Util;

import java.util.Map;

/**
 * Test resolver with a hardcoded input. Useful for localhost testing where Appsync isn't available to call the resolver.
 */
public class ManualTest {
    public static void main(String[] args) {
        String request = "<your JSON lambda request from AppSync>";
        Map<String, Object> mapInput = Util.GSON.fromJson(request, new TypeToken<Map<String, Object>>(){}.getType());

        AppSyncSqlResolverLambdaRequestHandler handler = new AppSyncSqlResolverLambdaRequestHandler();
        handler.handleRequest(mapInput, null);
    }
}

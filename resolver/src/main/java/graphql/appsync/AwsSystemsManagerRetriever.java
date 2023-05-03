package graphql.appsync;

import graphql.GraphQlAdapterException;
import graphql.sql.GraphQlTypeMetadata;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import util.Util;

public class AwsSystemsManagerRetriever implements SystemsManagerRetriever {
    @Override
    public GraphQlTypeMetadata lookupSystemParameter(String parameterName) {
        parameterName = "/appsync/typemetadata/" + parameterName;

        // Lambda function will always have this set
        String regionEnvVar = System.getenv("AWS_REGION");
        if (regionEnvVar == null) {
            throw new GraphQlAdapterException("AWS_REGION environment variable not set");
        }
        Region region = Region.of(System.getenv("AWS_REGION"));
        SsmClient ssmClient = SsmClient.builder()
                .region(region)
                .build();

        try (ssmClient) {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            return Util.GSON.fromJson(parameterResponse.parameter().value(), GraphQlTypeMetadata.class);
        }
    }
}

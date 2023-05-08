package graphql.appsync;

import graphql.GraphQlAdapterException;

public class SecretsManagerSecret {
    private final String name;
    private final String region;

    public SecretsManagerSecret(String name, String region) {
        if (name == null) {
            throw new GraphQlAdapterException("AWS SecretsManager Secret must have non-null name");
        }
        if (region == null) {
            throw new GraphQlAdapterException("AWS SecretsManager Secret must have non-null region");
        }
        this.name = name;
        this.region = region;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }
}

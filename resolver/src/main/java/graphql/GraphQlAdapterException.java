package graphql;

public class GraphQlAdapterException extends RuntimeException {
    public GraphQlAdapterException(String message) {
        super(message);
    }
    
    public GraphQlAdapterException(Exception ex) {
        super(ex);
    }

    public GraphQlAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}

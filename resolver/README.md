# AppSync GraphQL SQL Resolver

The AppSync GraphQL SQL Resolver takes GraphQL queries parsed by AppSync, converts them into SQL queries, and executes them against a SQL database.

This is a sample AppSync resolver implementation for AWS RDS as data source for integration with the Salesforce Connect GraphQL connector.

## Usage

Deploy as an AWS Lambda function. It interacts with other AWS products such as AppSync, SystemsManager and SecretsManager. The Lambda function's entry point is `graphql.appsync.AppSyncSqlResolverLambdaRequestHandler.java`.

To compile, run `mvn package -Dmaven.test.skip`. The deployable jar `graphql.resolvers.sql-1.0.0.jar` will be created in the target folder.

To debug, you can run the resolver locally. Debug an AppSync query using `ManualTest.java`. The resolver integrates with AWS SecretsManager and SystemsManager. Set up your AWS SDK credentials `~/.aws/credentials` accordingly. Your local machine must have network access to the database.

This resolver handles AppSync-parsed GraphQL queries. To debug, you can retrieve the AppSync-parsed GraphQL query from the Lambda function's CloudWatch logs.

## Testing

Tests are executed against an in-memory database called H2 as well as the databases PostgreSQL, Oracle and Microsoft SQL Server. The in-memory database tests do not require any special configuration and are tested in syntax compatibility mode for each of the supported databases. 

Tests against a database require that you provide database credentials in your `~/resolver/` directory in `postgresql.json`, `oracle.json` or `mssqlserver.json` respectively for your database vendor. You only need to run the tests for the database engines you are using.

Setup instructions for each database vendor:
PostgreSQL: https://postgresapp.com/
Oracle with Docker: https://hub.docker.com/r/gvenzl/oracle-xe
Microsoft SQL Server with Docker: https://learn.microsoft.com/en-us/sql/linux/quickstart-install-connect-docker


The files should be in this form:
```
{
    "username":"*DB user*",
    "password":"*DB password*",
    "vendor":"*PostgreSQL, Oracle, or MSSQLServer*",
    "host":"*DB host*",
    "databaseName": "*DB database name, SID name for Oracle", 
    "port":*DB port*
}
```

## Special Considerations

### Timezones

The resolver only accepts unzoned AWSTime and zoned AWSDateTime inputs. However, the SQL database could be using a zoned time or unzoned date-time data type, and it is ambiguous if a time zone conversion needs to be done.

The resolver makes these assumptions:
- If your SQL database contains a zoned time data type, this resolver assumes unzoned input data as UTC+0. Zoned SQL time data are converted to UTC+0 before output.
- The Salesforce DateTime data type is always zoned. If your SQL database contains an unzoned date-time data type, the resolver assumes all existing time data as UTC+0. A time zone of UTC+0 will be added before output.

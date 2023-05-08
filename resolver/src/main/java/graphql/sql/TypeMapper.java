package graphql.sql;

import com.google.common.base.CharMatcher;
import com.google.gson.reflect.TypeToken;
import graphql.GraphQlAdapterException;
import graphql.GraphQlFieldType;
import graphql.sql.db.SqlServerDatabaseProvider;
import microsoft.sql.DateTimeOffset;
import oracle.sql.TIMESTAMP;
import oracle.sql.TIMESTAMPLTZ;
import oracle.sql.TIMESTAMPTZ;
import org.postgresql.util.PGobject;
import util.Util;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TypeMapper {
    private static final String  SEPARATOR = ";";

    /**
     * Converts a GraphQL value into a JDBC-compliant value 
     */
    public static Object convertToJdbcReadyType(Object graphQlValue, GraphQlFieldType graphQlType, String vendor) {
        if (graphQlValue == null) {
            return null;
        }
        if (graphQlType == null) {
            throw new GraphQlAdapterException("field type not defined for: " + graphQlValue);
        }
        if (GraphQlFieldType.AWSJSON.equals(graphQlType)) {
            String jsonString = Util.GSON.toJson(graphQlValue);
            return jsonString;
        }
        if (graphQlValue instanceof String) {
            String castedValue = (String) graphQlValue;
            try {
                switch (graphQlType) {
                    case INT:
                        return Long.parseLong(castedValue);  // int can happen if global ID (string) is made of int
                    case FLOAT:
                        return Float.parseFloat(castedValue);  // float in PK, technically allowed in some vendors but terrible practice
                    case AWSDATETIME:
                        if (!castedValue.toUpperCase().contains("Z") || castedValue.contains("+") || CharMatcher.is('-').countIn(castedValue) > 2) {
                            throw new GraphQlAdapterException("DateTime values must be at timezone UTC+0 / Z, e.g. 2022-05-10T10:12:13Z. Got: " + castedValue);
                        }
                        return Instant.parse(castedValue).atOffset(ZoneOffset.UTC);
                    case AWSTIME:  // time must not be zoned
                        try {
                            LocalTime parsed = LocalTime.parse(castedValue);
                            if (vendor.equals(SqlServerDatabaseProvider.VENDOR)) {
                                return castedValue;  // SQL Server JDBC driver cannot handle Time types... just submit as string
                            }
                            return parsed;
                        } catch (DateTimeParseException e) {
                            throw new GraphQlAdapterException(String.format("could not parse local time: %s. zoned time values are not supported", castedValue));
                        }
                    case AWSDATE:
                        return LocalDate.parse(castedValue);
                    case ENUMERATION:
                        return castedValue;
                }
            } catch (GraphQlAdapterException e) {
                throw e;
            } catch (Exception e) {
                throw new GraphQlAdapterException(
                        String.format("could not parse GraphQL %s input with value %s", graphQlType, castedValue));
            }
        }
        if (graphQlType == GraphQlFieldType.ENUMMULTISELECT) {
            try {
                Collection<String> collection = (Collection<String>) graphQlValue;
                return String.join(SEPARATOR, collection);
            } catch (ClassCastException e) {
                throw new GraphQlAdapterException(
                        String.format("could not parse GraphQL %s input with value %s", graphQlType, graphQlValue));
            }
        }
        return graphQlValue;
    }

    /**
     * Helper for multiple conversions
     */
    public static LinkedHashMap<String, Object> convertToJdbcReadyType(LinkedHashMap<String, Object> graphQlTypes,
                                                                       Map<String, GraphQlFieldType> fieldTypeMappings,
                                                                       String vendor) {
        List<String> missingFieldTypes = graphQlTypes.keySet().stream()
                .filter(key -> !fieldTypeMappings.containsKey(key)).collect(Collectors.toList());
        if (!missingFieldTypes.isEmpty()) {
            throw new GraphQlAdapterException("all field types must be specified in a SystemsManager parameter. missing types: " + missingFieldTypes);
        }
        LinkedHashMap<String, Object> sqlTypes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : graphQlTypes.entrySet()) {
            Object convertedValue = TypeMapper.convertToJdbcReadyType(
                    entry.getValue(), fieldTypeMappings.get(entry.getKey()), vendor);
            sqlTypes.put(entry.getKey(), convertedValue);
        }
        return sqlTypes;
    }

    /**
     * convert JDBC result type to AppSync-ready result. each driver does things a little differently
     */
    public static Object convertFromJdbcResult(Object sqlObj, GraphQlFieldType graphQlType, String vendor) {
        if (sqlObj == null) {
            return null;
        }
        try {
            if (sqlObj instanceof Date) {
                return sqlObj.toString();
            } else if (sqlObj instanceof Time) {
                return LocalTime.MIDNIGHT.plus(((Time)sqlObj).getTime(), ChronoUnit.MILLIS).toString();
            } else if (sqlObj instanceof Timestamp) {
                Timestamp casted = (Timestamp) sqlObj;

                // Oracle DATE returns Timestamp
                if (graphQlType.equals(GraphQlFieldType.AWSDATE)) {
                    return LocalDate.ofInstant(casted.toInstant(), ZoneId.of("UTC")).toString();
                } else if (graphQlType.equals(GraphQlFieldType.AWSTIME)) {
                    return LocalTime.ofInstant(casted.toInstant(), ZoneId.of("UTC")).toString();
                }
                return casted.toInstant().toString();
            }
            
            else if (GraphQlFieldType.AWSJSON.equals(graphQlType)) {
                Object stringValue = sqlObj.toString();
                if (sqlObj instanceof PGobject) {
                    // Postgres json type comes out as PGobject
                    stringValue = ((PGobject) sqlObj).getValue();
                }
                return Util.GSON.fromJson(stringValue.toString(), new TypeToken<Map<String, Object>>(){}.getType());
            }

            // Postgres-specific
            else if (sqlObj instanceof PGobject) {
                // Postgres json type comes out as PGobject
                return Util.GSON.fromJson(((PGobject) sqlObj).getValue(), new TypeToken<Map<String, Object>>(){}.getType());
            }

            // SQL Server-specific
            else if (sqlObj instanceof DateTimeOffset) {
                return ((DateTimeOffset)sqlObj).getOffsetDateTime().toString();
            }

            // Oracle-specific
            else if (sqlObj instanceof TIMESTAMPTZ) {
                return ((TIMESTAMPTZ) sqlObj).toOffsetDateTime().toString();
            } else if (sqlObj instanceof TIMESTAMPLTZ) {
                throw new GraphQlAdapterException("Oracle data type TIMESTAMP WITH LOCAL TIME ZONE is not supported");
            } else if (sqlObj instanceof TIMESTAMP) {
                return ((TIMESTAMP) sqlObj).toLocalDateTime().toString();
            }

            // H2 in-memory DB-specific
            else if (sqlObj instanceof OffsetTime) {
                // time is always inserted as local time, even if database allows time zone
                return ((OffsetTime)sqlObj).toLocalTime().toString();
            } else if (sqlObj instanceof OffsetDateTime) {
                return ((OffsetDateTime)sqlObj).toString();
            }

            else if (sqlObj instanceof BigDecimal) {
                BigDecimal casted = (BigDecimal) sqlObj;
                // Oracle does not have boolean type, people typically use NUMBER(1)
                if (graphQlType.equals(GraphQlFieldType.BOOLEAN)) {
                    return casted.compareTo(BigDecimal.ZERO) > 0;
                    // Oracle returns BigDecimal even if it's an integer
                } else if (graphQlType.equals(GraphQlFieldType.INT)) {
                    return casted.intValue();
                }
                // BigDecimal standardizing, Oracle returns "100" while others return "100.0" for float
                return casted.stripTrailingZeros();
            }
            else if (sqlObj instanceof String) {
                String castedValue = (String) sqlObj;
                if (graphQlType == GraphQlFieldType.ENUMMULTISELECT) {
                    return castedValue.split(SEPARATOR);
                }
            }
        } catch (Exception e) {
            throw new GraphQlAdapterException(String.format("error mapping %s database result type to GraphQL response", vendor), e);
        }
        return sqlObj;
    }
}

package graphql;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;


@JsonAdapter(GraphQlFieldType.Deserializer.class)
public enum GraphQlFieldType {
    @SerializedName("Int")
    INT,
    
    @SerializedName("Float")
    FLOAT,

    @SerializedName("String")
    STRING,
    
    @SerializedName("ID")
    ID,

    @SerializedName("Boolean")
    BOOLEAN,

    @SerializedName("AWSDate")
    AWSDATE,

    @SerializedName("AWSTime")
    AWSTIME,

    @SerializedName("AWSDateTime")
    AWSDATETIME,

    @SerializedName("AWSTimestamp")
    AWSTIMESTAMP,

    @SerializedName("AWSEmail")
    AWSEMAIL,

    @SerializedName("AWSPhone")
    AWSPHONE,

    @SerializedName("AWSURL")
    AWSURL,

    @SerializedName("AWSJSON")
    AWSJSON,
    
    @SerializedName("AWSIPAddress")
    AWSIPADDRESS,

    @SerializedName("Enumeration")
    ENUMERATION,

    @SerializedName("EnumMultiSelect")
    ENUMMULTISELECT;
    
    // override the deserializer because we want to throw an error if an invalid field type is provided
    // GSON default behavior is to set null
    static class Deserializer implements JsonDeserializer<GraphQlFieldType> {
        @Override
        public GraphQlFieldType deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return GraphQlFieldType.valueOf(jsonElement.getAsString().toUpperCase());
        }
    }
}

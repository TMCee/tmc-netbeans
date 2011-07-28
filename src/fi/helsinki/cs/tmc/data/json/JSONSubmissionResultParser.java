package fi.helsinki.cs.tmc.data.json;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import fi.helsinki.cs.tmc.data.SubmissionResult;
import java.lang.reflect.Type;

public class JSONSubmissionResultParser {
    
    public static SubmissionResult parseJson(String json) {
        try {
            Gson gson = new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .registerTypeAdapter(SubmissionResult.Status.class, new StatusDeserializer())
                    .create();

            return gson.fromJson(json, SubmissionResult.class);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to parse submission result: " + e.getMessage(), e);
        }
    }
    
    private static class StatusDeserializer implements JsonDeserializer<SubmissionResult.Status> {
        @Override
        public SubmissionResult.Status deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            String s = json.getAsJsonPrimitive().getAsString();
            try {
                return SubmissionResult.Status.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Unknown submission status: " + s);
            }
        }
    }
}
package com.redhat.cloud.notifications;

import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Config source that is using the ClowderAppConfig
 */

public class ClowderConfigSource implements ConfigSource {

    private final Map<String, ConfigValue> existingValues;
    JsonObject root;

    public ClowderConfigSource(Map<String, ConfigValue> exProp) {

        existingValues = exProp;
        // TODO We need to read that from a mounted filesystem location at /cdappconfig/cdappconfig.json
        InputStream in = getClass().getResourceAsStream("/cdappconfig.json");
        if (in==null) {
            throw new IllegalStateException("Can't get cdappconfig.json"); // TODO better
        }
        JsonReader reader = Json.createReader(in);
        root = reader.readObject();
    }

    @Override
    public Map<String, String> getProperties() {

        Map<String,String> props = new HashMap<>();
        Set<Map.Entry<String, ConfigValue>> entries = existingValues.entrySet();
        for (Map.Entry<String,ConfigValue> entry : entries) {
            String newVal = getValue(entry.getKey());
            if (newVal == null) {
                newVal = entry.getValue().getValue();
            }
            props.put(entry.getKey(),newVal);
        }

        return props;
    }

    @Override
    public Set<String> getPropertyNames() {
        return existingValues.keySet();
    }

    @Override
    public int getOrdinal() {
        return 270;
    }

    @Override
    public String getValue(String s) {

        // This matches against the property as in application.properties
        // For profiles != prod, values are requested first like
        // %<profile>.property. E.g. %dev.quarkus.http.port


        if (s.equals("quarkus.http.port")) {
            JsonNumber webPort = root.getJsonNumber("webPort");
            return webPort.toString();
        }
        if (s.equals("kafka.bootstrap.servers")) {
            JsonArray brokers = root.getJsonObject("kafka").getJsonArray("brokers");
            JsonObject broker = brokers.getJsonObject(0);
            String b = broker.getString("hostname") + ":" + broker.getJsonNumber("port").toString();
            return b;
        }

        if (s.startsWith("mp.messaging") && s.endsWith(".topic")) {
            // We need to find the replaced topic by first finding
            // the requested name and then getting the replaced name
            String requested = existingValues.get(s).getValue();
            JsonArray topics = root.getJsonObject("kafka").getJsonArray("topics");
            for (int i = 0 ; i < topics.size(); i++) {
                JsonObject aTopic = topics.getJsonObject(i);
                if (aTopic.getString("requestedName").equals(requested)) {
                    String name = aTopic.getString("name");
                    return name;
                }
            }
            return requested;
        }

        if (s.startsWith("quarkus.database")) {
            String item = s.substring("quarkus.database.".length());
            JsonObject dbObject = root.getJsonObject("database");
            if (item.equals("username")) {
                return dbObject.getString("username");
            }
            if (item.equals("password")) {
                return dbObject.getString("password");
            }
            if (item.equals("jdbc.url")) {
                String host = dbObject.getString("hostname");
                int port = dbObject.getJsonNumber("port").intValue();
                String dbName = dbObject.getString("name");
                // TODO determine tracing from existing
                String jdbcUrl = String.format("jdbc:tracing:postgresql://%s:%d/%s",
                        host, port, dbName);
                return jdbcUrl;
            }
        }


        if (existingValues.containsKey(s)) {
            return existingValues.get(s).getValue();
        }
        else {
            return null;
        }
    }

    @Override
    public String getName() {
        return "ClowderConfigSource";
    }

}

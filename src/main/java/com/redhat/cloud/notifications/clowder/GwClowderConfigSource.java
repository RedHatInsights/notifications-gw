package com.redhat.cloud.notifications.clowder;

import com.redhat.cloud.common.clowder.configsource.ClowderConfig;
import com.redhat.cloud.common.clowder.configsource.ClowderConfigSource;
import io.quarkus.logging.Log;
import io.smallrye.config.ConfigValue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;
import static com.redhat.cloud.common.clowder.configsource.ClowderConfigSource.CLOWDER_CONFIG_SOURCE;

/**
 * A Config source that is using the ClowderAppConfig
 */
public class GwClowderConfigSource implements ConfigSource {

    public static final String KAFKA_SASL_JAAS_CONFIG_KEY = "kafka.sasl.jaas.config";
    public static final String KAFKA_SASL_MECHANISM_KEY = "kafka.sasl.mechanism";
    public static final String KAFKA_SECURITY_PROTOCOL_KEY = "kafka.security.protocol";
    public static final String KAFKA_SSL_TRUSTSTORE_LOCATION_KEY = "kafka.ssl.truststore.location";
    public static final String KAFKA_SSL_TRUSTSTORE_TYPE_KEY = "kafka.ssl.truststore.type";
    private static final String KAFKA_SASL_JAAS_CONFIG = "kafka.sasl.jaas.config";
    private static final String KAFKA_SASL_MECHANISM = "kafka.sasl.mechanism";
    private static final String KAFKA_SECURITY_PROTOCOL = "kafka.security.protocol";
    private static final String KAFKA_SSL_TRUSTSTORE_LOCATION = "kafka.ssl.truststore.location";
    private static final String KAFKA_SSL_TRUSTSTORE_TYPE = "kafka.ssl.truststore.type";


    private static List<String> KAFKA_SASL_KEYS = List.of(
            KAFKA_SASL_JAAS_CONFIG_KEY,
            KAFKA_SASL_MECHANISM_KEY,
            KAFKA_SECURITY_PROTOCOL_KEY,
            KAFKA_SSL_TRUSTSTORE_LOCATION_KEY,
            KAFKA_SSL_TRUSTSTORE_TYPE_KEY
    );


    Logger log = Logger.getLogger(getClass().getName());
    private static final Map<String,String> props = new HashMap<>();

    private final Map<String, ConfigValue> existingValues;

    public GwClowderConfigSource(ClowderConfigSource conf, Map<String, ConfigValue> exProp) {
        existingValues = exProp;
        for(String key : KAFKA_SASL_KEYS) {
            addIfDefined(conf, key);
        }
    }

    private void addIfDefined(ClowderConfigSource conf, String key) {
        String value = conf.getValue(key);
        if (value != null && value.trim().length()>0) {
            props.put(key, value);
            Log.infof("%s has been set", key);
        }
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
        Set<String> keys = new HashSet<>(props.keySet());
        keys.addAll(existingValues.keySet());
        return keys;
    }

    @Override
    public int getOrdinal() {
        // Provide a value higher than 250 to it overrides application.properties
        return 265;
    }

    @Override
    public String getValue(String s) {
        return props.get(s);
    }


    @Override
    public String getName() {
        return CLOWDER_CONFIG_SOURCE;
    }

}

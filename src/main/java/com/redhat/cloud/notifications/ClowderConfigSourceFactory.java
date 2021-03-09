package com.redhat.cloud.notifications;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalInt;

/**
 *
 */
public class ClowderConfigSourceFactory  implements ConfigSourceFactory {
    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext configSourceContext) {

        // Check if the ClowderSource should be used at all.
        ConfigValue cv = configSourceContext.getValue("use-clowder-source");
        if (cv != null) {
            boolean useClowderSource = Boolean.parseBoolean(cv.getValue());
            if (!useClowderSource) {
                return Collections.EMPTY_LIST;
            }
        }

        // It should be used, so get the existing key-values and
        // Supply them to our source.
        Map<String, ConfigValue> exProp = new HashMap<>();
        Iterator<String> stringIterator = configSourceContext.iterateNames();
        while (stringIterator.hasNext()) {
            String key = stringIterator.next();
            ConfigValue value = configSourceContext.getValue(key);
            exProp.put(key,value);
        }

        return Collections.singletonList(new ClowderConfigSource(exProp));
    }

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(270); // TODO like in C-source
    }
}

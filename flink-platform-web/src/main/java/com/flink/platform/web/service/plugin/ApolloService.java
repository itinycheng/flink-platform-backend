package com.flink.platform.web.service.plugin;

import com.flink.platform.plugin.apollo.NamespaceBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Lazy
@Service
public class ApolloService {

    private static final String CLAZZ = "com.flink.platform.plugin.apollo.ApolloConf";

    private final List<NamespaceBean> namespaceBeans;

    @Autowired
    public ApolloService(List<NamespaceBean> namespaceBeans) {
        checkPluginEnabled();
        if (namespaceBeans == null || namespaceBeans.isEmpty()) {
            throw new IllegalArgumentException("No apollo namespace found");
        }

        this.namespaceBeans = namespaceBeans;
    }

    public String getConfig(String namespace, String key) {
        return namespaceBeans.stream()
                .filter(ns -> ns.namespace().equals(namespace))
                .map(ns -> ns.getConfig(key))
                .filter(Objects::nonNull)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Apollo config not found: namespace: " + namespace + ", key: " + key));
    }

    private void checkPluginEnabled() {
        try {
            Class.forName(CLAZZ);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Apollo plugin isn't included, Please repackage with profile apollo, e.g. `./mvnw clean package -Papollo`",
                    e);
        }
    }
}

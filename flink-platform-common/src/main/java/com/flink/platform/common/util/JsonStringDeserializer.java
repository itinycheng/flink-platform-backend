package com.flink.platform.common.util;

import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.StringNode;

/**
 * warning: this isn't an efficient implementation, improve the code in the future.
 *
 * <p>keep a json as string when use jackson to deSerde a string value trim double quotes when a
 * TextNode appears
 */
@SuppressWarnings("unused")
public class JsonStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser jp, DeserializationContext ctx) {
        TreeNode treeNode = jp.objectReadContext().readTree(jp);
        String origin = treeNode.toString();
        if (treeNode instanceof StringNode && origin.startsWith("\"")) {
            origin = origin.substring(1, origin.length() - 1);
        }
        return origin;
    }
}

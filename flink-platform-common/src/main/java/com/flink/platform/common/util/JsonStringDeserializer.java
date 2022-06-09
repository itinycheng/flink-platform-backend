package com.flink.platform.common.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;

/**
 * warning: this isn't an efficient implementation, improve the code in the future.
 *
 * <p>keep a json as string when use jackson to deSerde a string value trim double quotes when a
 * TextNode appears
 */
public class JsonStringDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser jp, DeserializationContext ctx) throws IOException {
        TreeNode treeNode = jp.getCodec().readTree(jp);
        String origin = treeNode.toString();
        if (treeNode instanceof TextNode && origin.startsWith("\"")) {
            origin = origin.substring(1, origin.length() - 1);
        }
        return origin;
    }
}

package com.flink.platform.web.parser;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

/**
 * @author tiny.wang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CompositeSqlWhere.class, name = "composite"),
        @JsonSubTypes.Type(value = SimpleSqlWhere.class, name = "simple")
})
public class SqlWhere {

    private String type;

    public List<SqlIdentifier> exhaustiveSqlIdentifiers() {
        Set<SqlIdentifier> identifierSet = new HashSet<>();
        if (this instanceof SimpleSqlWhere) {
            SimpleSqlWhere simpleSqlWhere = (SimpleSqlWhere) this;
            SqlIdentifier column = simpleSqlWhere.getColumn();
            identifierSet.add(column);
        } else if (this instanceof CompositeSqlWhere) {
            CompositeSqlWhere compositeSqlWhere = (CompositeSqlWhere) this;
            List<SqlIdentifier> identifiers = compositeSqlWhere.getConditions().stream()
                    .flatMap(sqlWhere -> sqlWhere.exhaustiveSqlIdentifiers().stream())
                    .collect(Collectors.toList());
            identifierSet.addAll(identifiers);
        }
        return new ArrayList<>(identifierSet);
    }

}

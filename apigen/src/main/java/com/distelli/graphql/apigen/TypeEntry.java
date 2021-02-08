package com.distelli.graphql.apigen;

import java.util.Collections;

import graphql.language.*;
import graphql.Scalars;

import java.util.List;
import java.net.URL;

public class TypeEntry {
    private final URL source;
    private final Definition<?> definition;
    private final String packageName;

    public TypeEntry(Definition<?> definition, URL source, String defaultPackageName) {
        this.source = source;
        this.definition = definition;
        this.packageName = getPackageName(getDirectives(definition), defaultPackageName);
    }

    // Return nice formatted string for source location:
    public String getSourceLocation() {
        SourceLocation sourceLocation = this.definition.getSourceLocation();

        return String.format("%s:[%s, %s]", source, sourceLocation.getLine(), sourceLocation.getColumn());
    }

    public String getName() {
        if (definition instanceof TypeDefinition) {
            return ((TypeDefinition<?>) definition).getName();
        }
        return "";
    }

    public boolean hasIdField() {
        if (definition instanceof ObjectTypeDefinition) {
            return ((ObjectTypeDefinition) definition).getFieldDefinitions()
                    .stream()
                    .anyMatch((field) -> "id".equals(field.getName()));
        }
        return false;
    }

    private static List<Directive> getDirectives(Definition<?> def) {
        if (def instanceof DirectivesContainer<?>) {
            return ((DirectivesContainer<?>) def).getDirectives();
        }

        return Collections.emptyList();
    }

    private static String getPackageName(List<Directive> directives, String defaultPackageName) {
        String packageName = null;

        for (Directive directive : directives) {
            if ("java".equals(directive.getName())) {
                for (Argument arg : directive.getArguments()) {
                    if ("package".equals(arg.getName())) {
                        packageName = (String) Scalars.GraphQLString.getCoercing().parseLiteral(arg.getValue());
                        break;
                    }
                }
            }
            break;
        }

        return (null == packageName) ? defaultPackageName : packageName;
    }

    public URL getSource() {
        return source;
    }

    public String getPackageName() {
        return packageName;
    }

    public Definition<?> getDefinition() {
        return definition;
    }
}

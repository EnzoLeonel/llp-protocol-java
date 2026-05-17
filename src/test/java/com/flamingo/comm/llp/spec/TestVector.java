package com.flamingo.comm.llp.spec;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record TestVector(
    String specVersion,
    String type,
    String name,
    String description,
    JsonNode input,
    JsonNode expected,
    List<String> flags
) {}

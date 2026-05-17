package com.flamingo.comm.llp.util;

/**
 * Utility class containing LLP protocol rules related to layer identifiers.
 *
 * <p>
 * In the LLP protocol, each layer is identified by an unsigned byte (0–255).
 * The identifier encodes behavioral semantics used by the core parser.
 * </p>
 *
 * <h2>Layer Categories</h2>
 * <ul>
 *     <li><b>Final Layer (ID = 0)</b>:
 *         <ul>
 *             <li>Represents the innermost payload.</li>
 *             <li>Does not contain metadata length or metadata.</li>
 *             <li>Terminates the parsing process.</li>
 *             <li>Does not modify payload.</li>
 *         </ul>
 *     </li>
 *     <li><b>Skippable Layers (ID 1–127)</b>:
 *         <ul>
 *             <li>Do not modify the payload.</li>
 *             <li>Can be safely skipped if no parser is available.</li>
 *         </ul>
 *     </li>
 *     <li><b>Non-skippable / Transform Layers (ID 128–254)</b>:
 *         <ul>
 *             <li>Modify the payload (e.g., encryption, compression).</li>
 *             <li>Must be parsed to correctly interpret subsequent layers.</li>
 *         </ul>
 *     </li>
 *     <li><b>Reserved Layer (ID = 255)</b>:
 *         <ul>
 *             <li>Reserved for future use.</li>
 *             <li>Parsers should treat as unknown and skip if possible.</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p>
 * This class centralizes protocol rules to avoid scattering "magic numbers"
 * throughout the codebase and to improve readability and maintainability.
 * </p>
 */
public final class LayerIds {

    /**
     * Identifier of the final (innermost) layer.
     */
    static final int FINAL_LAYER_ID = 0;

    /**
     * Threshold from which layers are considered non-skippable (transform).
     */
    private static final int NON_SKIPPABLE_THRESHOLD = 128;

    /**
     * Reserved layer identifier. Per the LLP specification, parsers should
     * treat this ID as unknown and skip if possible.
     */
    static final int RESERVED_LAYER_ID = 255;

    private LayerIds() {
        // Utility class (no instances)
    }

    /**
     * Checks whether the given layer ID represents the final layer.
     *
     * <p>The final layer terminates parsing and contains only raw payload.</p>
     *
     * @param id layer identifier
     * @return {@code true} if this is the final layer (ID = 0), otherwise {@code false}
     */
    public static boolean isFinal(int id) {
        return id == FINAL_LAYER_ID;
    }

    /**
     * Checks whether the given layer ID is skippable.
     *
     * <p>
     * Skippable layers do not modify the payload, meaning that parsing can continue
     * even if no parser is available for this layer.
     * </p>
     *
     * <p>
     * Note: The final layer (ID = 0) is also considered non-modifying, but it is
     * excluded from this method since it has special structural semantics.
     * The reserved ID (255) is considered skippable per the LLP specification.
     * </p>
     *
     * @param id layer identifier
     * @return {@code true} if the layer is skippable (ID 1–127 or 255), otherwise {@code false}
     */
    public static boolean isSkippable(int id) {
        return (id > FINAL_LAYER_ID && id < NON_SKIPPABLE_THRESHOLD)
                || id == RESERVED_LAYER_ID;
    }

    /**
     * Checks whether the given layer ID is non-skippable.
     *
     * <p>
     * Non-skippable layers modify the payload (e.g., encryption or compression),
     * therefore they must be successfully parsed before continuing to inner layers.
     * The reserved ID (255) is <em>not</em> considered non-skippable per the
     * LLP specification, which states it should be skipped if possible.
     * </p>
     *
     * @param id layer identifier
     * @return {@code true} if the layer is non-skippable (ID 128–254), otherwise {@code false}
     */
    public static boolean isNonSkippable(int id) {
        return id >= NON_SKIPPABLE_THRESHOLD && id != RESERVED_LAYER_ID;
    }

    /**
     * Checks whether the given layer ID is reserved.
     *
     * <p>
     * The reserved ID (255) is set aside for future use per the LLP specification.
     * Parsers should treat it as unknown and skip if possible — it is not
     * considered a transform (non-skippable) layer.
     * </p>
     *
     * @param id layer identifier
     * @return {@code true} if the layer ID is reserved (255), otherwise {@code false}
     */
    public static boolean isReserved(int id) {
        return id == RESERVED_LAYER_ID;
    }

    /**
     * Checks whether the given layer ID is within the valid LLP range.
     *
     * <p>Valid values are unsigned byte range: 0–255.</p>
     *
     * @param id layer identifier
     * @return {@code true} if valid, otherwise {@code false}
     */
    public static boolean isValid(int id) {
        return id >= 0 && id <= 255;
    }
}

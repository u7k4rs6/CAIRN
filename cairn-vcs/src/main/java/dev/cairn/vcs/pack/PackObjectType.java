package dev.cairn.vcs.pack;

import dev.cairn.vcs.object.ObjectKind;

/** Git's pack entry type codes (pack-format spec); REF_DELTA is the only delta encoding this pack writer produces. */
enum PackObjectType {
    COMMIT(1),
    TREE(2),
    BLOB(3),
    TAG(4),
    REF_DELTA(7);

    final int code;

    PackObjectType(int code) {
        this.code = code;
    }

    static PackObjectType of(ObjectKind kind) {
        return switch (kind) {
            case COMMIT -> COMMIT;
            case TREE -> TREE;
            case BLOB -> BLOB;
            case TAG -> TAG;
        };
    }

    static ObjectKind toObjectKind(PackObjectType type) {
        return switch (type) {
            case COMMIT -> ObjectKind.COMMIT;
            case TREE -> ObjectKind.TREE;
            case BLOB -> ObjectKind.BLOB;
            case TAG -> ObjectKind.TAG;
            case REF_DELTA -> throw new IllegalArgumentException("REF_DELTA has no direct ObjectKind");
        };
    }

    static PackObjectType fromCode(int code) {
        for (PackObjectType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported pack object type code: " + code);
    }
}

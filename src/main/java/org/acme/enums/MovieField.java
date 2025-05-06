package org.acme.enums;

import java.util.*;

public enum MovieField {
    CODE("代码", "Code", "コード"),
    RELEASE_DATE("发布日期", "Release Date", "リリース日"),
    DURATION("时长", "Duration", "再生時間"),
    ACTRESSES("女演员", "Actress", "女優"),
    GENRE("类型", "Genre", "ジャンル"),
    MAKER("制作人", "Maker", "メーカー"),
    LABEL("标签", "Label", "ラベル"),
    TAG("标签", "Tag", "タグ"); 

    private final Set<String> aliases;

    MovieField(String... aliases) {
        this.aliases = new HashSet<>(Arrays.asList(aliases));
    }

    public static Optional<MovieField> fromLabel(String label) {
        for (MovieField field : values()) {
            if (field.aliases.contains(label.trim())) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }
}

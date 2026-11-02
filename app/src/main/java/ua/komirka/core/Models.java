package ua.komirka.core;

import java.util.*;

/** Storage-neutral model. Column keys never depend on editable display names. */
public final class Models {
    private Models() {}
    public enum Kind {
        STRING("Текст", "String", "Імена, нотатки та будь-який текст", "Aa"),
        INT("Ціле число", "int", "Кількість без дробової частини", "12"),
        FLOAT("Дробове число", "float", "Вимірювання; крапка або кома", "1,2"),
        CATEGORY("Категорія", "category", "Один варіант із вашого списку", "≡"),
        ORDINAL("Впорядковані категорії", "ordinal", "Варіанти від меншого до більшого", "↗"),
        BOOL("Так / Ні", "bool", "Два зрозумілі варіанти", "✓"),
        DATE("Дата", "date", "Вибір у календарі", "▦");
        public final String title, code, description, mark;
        Kind(String title, String code, String description, String mark) {
            this.title=title; this.code=code; this.description=description; this.mark=mark;
        }
        public boolean options() { return this == CATEGORY || this == ORDINAL; }
    }
    public enum IdMode {
        NONE("Без окремого ID", "Лише ваші стовпці"),
        AUTO("Автоматичний UUID", "Унікальний код для кожного нового запису"),
        INCREMENT("Автоінкремент", "Послідовні числа: 1, 2, 3…"),
        MANUAL("Вводити ID вручну", "Ваш власний унікальний текстовий код");
        public final String title, description;
        IdMode(String title, String description) { this.title=title; this.description=description; }
    }
    public static final class Column {
        public String key=UUID.randomUUID().toString(), name="";
        public Kind kind=Kind.STRING;
        public List<String> options=new ArrayList<>();
        public int sourceIndex=-1;
        public Column() {}
        public Column(String name, Kind kind) { this.name=name; this.kind=kind; }
        public Column copy() {
            Column c=new Column(name,kind); c.key=key; c.sourceIndex=sourceIndex;
            c.options.addAll(options); return c;
        }
    }
    public static final class Table {
        public String id=UUID.randomUUID().toString(), name="", description="", idName="id";
        public IdMode idMode=IdMode.NONE;
        public List<Column> columns=new ArrayList<>();
        public long createdAt=System.currentTimeMillis(), updatedAt=createdAt, nextId=1, rowCount;
        public int color;
    }
    public static final class Revision {
        public Object value;
        public long at;
        public Revision(Object value, long at) { this.value=value; this.at=at; }
    }
    public static final class Row {
        public String id=UUID.randomUUID().toString(), tableId, externalId;
        public LinkedHashMap<String,Object> values=new LinkedHashMap<>();
        public LinkedHashMap<String,List<Revision>> history=new LinkedHashMap<>();
        public long createdAt=System.currentTimeMillis(), updatedAt=createdAt;
        public int version=0;
    }
    public static final class Draft {
        public String tableId, rowId, externalId="";
        public int step=0, baseVersion=0;
        public LinkedHashMap<String,String> raw=new LinkedHashMap<>();
        public Set<String> answered=new HashSet<>(), nulls=new HashSet<>();
        public LinkedHashMap<String,List<Revision>> history=new LinkedHashMap<>();
    }
}


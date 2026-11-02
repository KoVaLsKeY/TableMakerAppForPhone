package ua.komirka.core;

import java.util.*;

/** Storage-neutral model. Column keys never depend on editable display names. */
public final class Models {
    private Models() {}
    public enum Kind {
        STRING("Text", "String", "Names, notes, and other text", "Aa"),
        INT("Integer", "int", "Whole numbers, such as counts", "12"),
        FLOAT("Decimal", "float", "Measurements with a decimal point or comma", "1.2"),
        CATEGORY("Category", "category", "One option from your list", "≡"),
        ORDINAL("Ordered categories", "ordinal", "Options arranged from lowest to highest", "↗"),
        BOOL("Yes / No", "bool", "A choice between two values", "✓"),
        DATE("Date", "date", "Choose a date from the calendar", "▦");
        public final String title, code, description, mark;
        Kind(String title, String code, String description, String mark) {
            this.title=title; this.code=code; this.description=description; this.mark=mark;
        }
        public boolean options() { return this == CATEGORY || this == ORDINAL; }
    }
    public enum IdMode {
        NONE("No ID column", "Use only the columns you add"),
        AUTO("Automatic UUID", "Generate a unique code for each record"),
        INCREMENT("Auto-increment", "Number records in order: 1, 2, 3…"),
        MANUAL("Manual ID", "Enter your own unique text code");
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


package ua.komirka.core;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import static ua.komirka.core.Models.*;

public final class Values {
    private Values() {}
    public static Object parse(Column column, String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String s=raw.trim();
        switch (column.kind) {
            case STRING: return raw;
            case INT:
                if (!s.matches("[+-]?\\d+")) throw new IllegalArgumentException("Enter a whole number, such as 42 or -3.");
                try { return Long.parseLong(s); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("The integer is outside the signed 64-bit range."); }
            case FLOAT:
                if (!s.matches("[+-]?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)(?:[eE][+-]?\\d+)?"))
                    throw new IllegalArgumentException("Enter a number, such as 3.14 or -0.5.");
                double number=Double.parseDouble(s.replace(',', '.'));
                if (!Double.isFinite(number)) throw new IllegalArgumentException("The number is too large. For a missing value, choose No value.");
                if (number == 0 && new java.math.BigDecimal(s.replace(',', '.')).signum() != 0)
                    throw new IllegalArgumentException("The number is too small for a decimal field.");
                return number;
            case BOOL:
                switch (s.toLowerCase(Locale.ROOT)) {
                    case "true": case "так": case "yes": case "1": return true;
                    case "false": case "ні": case "no": case "0": return false;
                    default: throw new IllegalArgumentException("Choose Yes or No.");
                }
            case CATEGORY: case ORDINAL:
                if (!column.options.contains(raw)) throw new IllegalArgumentException("Choose a category from the list.");
                return raw;
            case DATE:
                try { return LocalDate.parse(s).toString(); }
                catch (DateTimeParseException e) { throw new IllegalArgumentException("Enter the date in YYYY-MM-DD format."); }
            default: throw new IllegalArgumentException("Unknown data type.");
        }
    }
    public static String display(Object value) {
        if (value == null) return "No value";
        if (value instanceof Boolean) return (Boolean)value ? "Yes" : "No";
        return String.valueOf(value);
    }
    public static String raw(Object value) { return value == null ? "" : String.valueOf(value); }
    public static String noun(long number,String singular,String plural) { return number==1?singular:plural; }
    public static String records(long count){return count+" "+noun(count,"record","records");}
    public static String columns(long count){return count+" "+noun(count,"column","columns");}
    public static boolean same(Object a, Object b) {
        if (a instanceof Number && b instanceof Number)
            return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()))==0;
        return Objects.equals(a,b);
    }
    public static void validate(Table table) {
        if (table.name.trim().isEmpty()) throw new IllegalArgumentException("Enter a name for the table.");
        if (table.name.length()>120) throw new IllegalArgumentException("Table names can contain up to 120 characters.");
        if (table.columns.isEmpty()) throw new IllegalArgumentException("Add at least one column.");
        if (table.columns.size()+(table.idMode==IdMode.NONE?0:1)>100) throw new IllegalArgumentException("A table can have up to 100 columns, including its ID column.");
        Set<String> names=new HashSet<>(), keys=new HashSet<>();
        if (table.idMode != IdMode.NONE) {
            validateName(table.idName); names.add(table.idName.trim().toLowerCase(Locale.ROOT));
        }
        for (Column c: table.columns) {
            validateName(c.name);
            if (!names.add(c.name.trim().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("The name “"+c.name+"” is repeated. Each column needs a different name.");
            if (!keys.add(c.key)) throw new IllegalArgumentException("Duplicate column key.");
            if (c.kind.options()) {
                if (c.options.isEmpty()) throw new IllegalArgumentException("Add categories for “"+c.name+"”.");
                Set<String> options=new HashSet<>();
                for (String option:c.options) {
                    if (option.trim().isEmpty()) throw new IllegalArgumentException("A category cannot be empty.");
                    if (!options.add(option)) throw new IllegalArgumentException("The category “"+option+"” is repeated.");
                }
            }
        }
    }
    public static void validateName(String name) {
        if (name.trim().isEmpty() || name.length()>100)
            throw new IllegalArgumentException("Column names must contain 1 to 100 characters.");
    }
    public static Kind infer(List<String> samples) {
        List<String> filled=new ArrayList<>();
        for (String s:samples) if (!s.trim().isEmpty()) filled.add(s);
        if (filled.isEmpty()) return Kind.STRING;
        // Leading zeroes are usually identifiers, and must survive import unchanged.
        for (String s:filled) if (s.trim().matches("[+-]?0\\d+")) return Kind.STRING;
        boolean explicitBool=true;
        for (String s:filled) if (!s.trim().toLowerCase(Locale.ROOT).matches("true|false|так|ні|yes|no")) explicitBool=false;
        if (explicitBool) return Kind.BOOL;
        for (Kind k:new Kind[]{Kind.INT,Kind.FLOAT,Kind.DATE}) {
            Column c=new Column("",k); boolean all=true;
            for (String s:filled) { try { parse(c,s); } catch (IllegalArgumentException ex) { all=false; break; } }
            if (all) return k;
        }
        return Kind.STRING;
    }
    public static Map<String,Object> validateDraft(Table table, Draft draft) {
        LinkedHashMap<String,Object> result=new LinkedHashMap<>();
        for (Column c:table.columns) {
            if (!draft.answered.contains(c.key)) throw new IllegalArgumentException("Fill in “"+c.name+"” or choose No value.");
            try { result.put(c.key,draft.nulls.contains(c.key) ? null : parse(c,draft.raw.get(c.key))); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException(c.name+": "+e.getMessage()); }
        }
        if (table.idMode==IdMode.MANUAL && draft.externalId.trim().isEmpty()) throw new IllegalArgumentException("Enter an ID for the record.");
        return result;
    }
}


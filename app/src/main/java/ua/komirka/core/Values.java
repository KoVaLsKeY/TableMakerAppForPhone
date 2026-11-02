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
                if (!s.matches("[+-]?\\d+")) throw new IllegalArgumentException("Введіть ціле число, наприклад 42 або −3.");
                try { return Long.parseLong(s); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Ціле число завелике (потрібне 64-бітне число)."); }
            case FLOAT:
                if (!s.matches("[+-]?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+)(?:[eE][+-]?\\d+)?"))
                    throw new IllegalArgumentException("Введіть число, наприклад 3,14 або −0.5.");
                double number=Double.parseDouble(s.replace(',', '.'));
                if (!Double.isFinite(number)) throw new IllegalArgumentException("Число завелике. Для відсутнього значення оберіть «Немає значення».");
                if (number == 0 && new java.math.BigDecimal(s.replace(',', '.')).signum() != 0)
                    throw new IllegalArgumentException("Число замале для типу float.");
                return number;
            case BOOL:
                switch (s.toLowerCase(Locale.ROOT)) {
                    case "true": case "так": case "yes": case "1": return true;
                    case "false": case "ні": case "no": case "0": return false;
                    default: throw new IllegalArgumentException("Оберіть «Так» або «Ні».");
                }
            case CATEGORY: case ORDINAL:
                if (!column.options.contains(raw)) throw new IllegalArgumentException("Оберіть категорію зі списку.");
                return raw;
            case DATE:
                try { return LocalDate.parse(s).toString(); }
                catch (DateTimeParseException e) { throw new IllegalArgumentException("Дата має бути у форматі РРРР-ММ-ДД."); }
            default: throw new IllegalArgumentException("Невідомий тип.");
        }
    }
    public static String display(Object value) {
        if (value == null) return "Немає значення";
        if (value instanceof Boolean) return (Boolean)value ? "Так" : "Ні";
        return String.valueOf(value);
    }
    public static String raw(Object value) { return value == null ? "" : String.valueOf(value); }
    public static String noun(long number,String one,String few,String many) {
        long lastTwo=Math.abs(number%100),last=Math.abs(number%10);
        if(lastTwo>=11&&lastTwo<=14)return many;
        return last==1?one:last>=2&&last<=4?few:many;
    }
    public static String records(long count){return count+" "+noun(count,"запис","записи","записів");}
    public static String columns(long count){return count+" "+noun(count,"стовпець","стовпці","стовпців");}
    public static boolean same(Object a, Object b) {
        if (a instanceof Number && b instanceof Number)
            return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()))==0;
        return Objects.equals(a,b);
    }
    public static void validate(Table table) {
        if (table.name.trim().isEmpty()) throw new IllegalArgumentException("Дайте таблиці назву.");
        if (table.name.length()>120) throw new IllegalArgumentException("Назва таблиці: до 120 символів.");
        if (table.columns.isEmpty()) throw new IllegalArgumentException("Додайте хоча б один стовпець.");
        if (table.columns.size()+(table.idMode==IdMode.NONE?0:1)>100) throw new IllegalArgumentException("Одна таблиця може містити до 100 стовпців разом з ID.");
        Set<String> names=new HashSet<>(), keys=new HashSet<>();
        if (table.idMode != IdMode.NONE) {
            validateName(table.idName); names.add(table.idName.trim().toLowerCase(Locale.ROOT));
        }
        for (Column c: table.columns) {
            validateName(c.name);
            if (!names.add(c.name.trim().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Назва «"+c.name+"» повторюється. Назви стовпців мають бути різними.");
            if (!keys.add(c.key)) throw new IllegalArgumentException("Повторний ключ стовпця.");
            if (c.kind.options()) {
                if (c.options.isEmpty()) throw new IllegalArgumentException("Додайте категорії для «"+c.name+"».");
                Set<String> options=new HashSet<>();
                for (String option:c.options) {
                    if (option.trim().isEmpty()) throw new IllegalArgumentException("Категорія не може бути порожньою.");
                    if (!options.add(option)) throw new IllegalArgumentException("Категорія «"+option+"» повторюється.");
                }
            }
        }
    }
    public static void validateName(String name) {
        if (name.trim().isEmpty() || name.length()>100)
            throw new IllegalArgumentException("Назва стовпця має містити від 1 до 100 символів.");
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
            if (!draft.answered.contains(c.key)) throw new IllegalArgumentException("Заповніть «"+c.name+"» або оберіть «Немає значення».");
            try { result.put(c.key,draft.nulls.contains(c.key) ? null : parse(c,draft.raw.get(c.key))); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException(c.name+": "+e.getMessage()); }
        }
        if (table.idMode==IdMode.MANUAL && draft.externalId.trim().isEmpty()) throw new IllegalArgumentException("Введіть ID запису.");
        return result;
    }
}


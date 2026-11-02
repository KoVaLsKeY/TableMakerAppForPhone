package ua.komirka.core;

import org.junit.Test;
import java.util.*;
import static ua.komirka.core.Models.*;
import static org.junit.Assert.*;

public class ValuesTest {
    private Column c(Kind kind){return new Column("Тест",kind);}
    @Test public void parsesSigned64BitIntegersWithoutRounding(){assertEquals(Long.MIN_VALUE,Values.parse(c(Kind.INT),"-9223372036854775808"));assertEquals(42L,Values.parse(c(Kind.INT),"+42"));}
    @Test public void rejectsFractionOverflowAndExponentForInt(){for(String s:Arrays.asList("1.2","1e3","9223372036854775808","3,2","abc"))assertThrows(IllegalArgumentException.class,()->Values.parse(c(Kind.INT),s));}
    @Test public void floatAcceptsLocalDecimalsAndExponent(){assertEquals(3.14,Values.parse(c(Kind.FLOAT),"3,14"));assertEquals(-.5,Values.parse(c(Kind.FLOAT),"-.5"));assertEquals(2000d,Values.parse(c(Kind.FLOAT),"2e3"));}
    @Test public void floatRejectsNanInfinityOverflowUnderflowAndHex(){for(String s:Arrays.asList("NaN","Infinity","1e999","1e-999","0x1.2p4","1,000.0","--1","1f"))assertThrows(IllegalArgumentException.class,()->Values.parse(c(Kind.FLOAT),s));}
    @Test public void booleansKeepFalseDifferentFromNull(){assertEquals(false,Values.parse(c(Kind.BOOL),"Ні"));assertEquals(true,Values.parse(c(Kind.BOOL),"true"));assertNull(Values.parse(c(Kind.BOOL),""));}
    @Test public void datesRejectInvalidDays(){assertEquals("2024-02-29",Values.parse(c(Kind.DATE),"2024-02-29"));assertThrows(IllegalArgumentException.class,()->Values.parse(c(Kind.DATE),"2025-02-29"));}
    @Test public void stringsKeepWhitespaceAndLiteralNull(){assertEquals("  Київ  ",Values.parse(c(Kind.STRING),"  Київ  "));assertEquals("null",Values.parse(c(Kind.STRING),"null"));assertEquals("NaN",Values.parse(c(Kind.STRING),"NaN"));assertNull(Values.parse(c(Kind.STRING),""));}
    @Test public void ordinalKeepsExplicitOrderAndRejectsUnknown(){Column c=c(Kind.ORDINAL);c.options.addAll(Arrays.asList("Низька","Середня","Висока"));assertEquals("Середня",Values.parse(c,"Середня"));assertEquals("Низька",c.options.get(0));assertThrows(IllegalArgumentException.class,()->Values.parse(c,"Інша"));}
    @Test public void inferProtectsLeadingZeroIdsAndMixedText(){assertEquals(Kind.STRING,Values.infer(Arrays.asList("001","002")));assertEquals(Kind.STRING,Values.infer(Arrays.asList("23","abc")));assertEquals(Kind.STRING,Values.infer(Arrays.asList("","")));}
    @Test public void inferUsesNumbersBooleansAndDatesConservatively(){assertEquals(Kind.INT,Values.infer(Arrays.asList("0","1","")));assertEquals(Kind.BOOL,Values.infer(Arrays.asList("true","false")));assertEquals(Kind.FLOAT,Values.infer(Arrays.asList("1,2","3.4")));assertEquals(Kind.DATE,Values.infer(Arrays.asList("2026-01-01","2026-12-31")));}
    @Test public void rejectsDuplicateReservedNamesAndEmptyCategories(){Table t=new Table();t.name="T";t.columns.add(new Column("ID",Kind.STRING));t.idMode=IdMode.AUTO;assertThrows(IllegalArgumentException.class,()->Values.validate(t));t.idMode=IdMode.NONE;t.columns.add(new Column("id",Kind.INT));assertThrows(IllegalArgumentException.class,()->Values.validate(t));t.columns.clear();t.columns.add(new Column("Категорія",Kind.CATEGORY));assertThrows(IllegalArgumentException.class,()->Values.validate(t));}
    @Test public void draftMustBeExplicitlyAnsweredButCanContainNull(){Table t=new Table();t.name="T";Column c=c(Kind.BOOL);t.columns.add(c);Draft d=new Draft();assertThrows(IllegalArgumentException.class,()->Values.validateDraft(t,d));d.answered.add(c.key);d.nulls.add(c.key);assertNull(Values.validateDraft(t,d).get(c.key));d.nulls.clear();d.raw.put(c.key,"false");assertEquals(false,Values.validateDraft(t,d).get(c.key));}
    @Test public void numericEqualityDoesNotCreateSpuriousAliases(){assertTrue(Values.same(1,1L));assertTrue(Values.same(1d,1L));assertFalse(Values.same(null,false));assertFalse(Values.same("1",1L));}
}

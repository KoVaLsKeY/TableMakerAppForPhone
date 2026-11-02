package ua.komirka.core;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class CsvTest {
    @Test public void multilineQuotesUnicodeAndBomRoundTrip() throws Exception {
        List<String> headers=Arrays.asList("Ім’я","Нотатка","Порожнє");
        List<Object> values=Arrays.asList("Олена, Київ","Рядок 1\r\n\"Цитата\"; далі",null);
        StringWriter out=new StringWriter();out.write('\uFEFF');Csv.writeRow(out,headers);Csv.writeRow(out,values);
        Csv.Document doc=Csv.read(out.toString());assertEquals(headers,doc.headers);assertEquals("Олена, Київ",doc.rows.get(0).get(0));assertEquals(values.get(1),doc.rows.get(0).get(1));assertEquals("",doc.rows.get(0).get(2));
    }
    @Test public void detectsSemicolonWithDecimalComma() {
        Csv.Document csv=Csv.read("Назва;Вага\r\nЯблуко;1,25\r\n");assertEquals(';',csv.separator);assertEquals("1,25",csv.rows.get(0).get(1));
    }
    @Test public void detectsTabOutsideQuotedHeaders() {
        Csv.Document csv=Csv.read("\"Ім’я, прізвище\"\tОцінка\nОлена\t5");assertEquals('\t',csv.separator);assertEquals(2,csv.headers.size());
    }
    @Test public void delimiterInsideQuotedHeaderDoesNotWin() {
        Csv.Document csv=Csv.read("\"a;b;c\",d\n1,2");assertEquals(',',csv.separator);assertEquals("a;b;c",csv.headers.get(0));
    }
    @Test public void missingHeadersReceiveUniqueNames() {
        Csv.Document csv=Csv.read("a,a,,\n1,2,3,4");assertEquals(Arrays.asList("a","a (2)","Стовпець 3","Стовпець 4"),csv.headers);
    }
    @Test public void emptyLastFieldAndBlankSingleFieldArePreserved() {
        assertEquals(Arrays.asList("1",""),Csv.read("a,b\n1,\n").rows.get(0));
        assertEquals(Arrays.asList(""),Csv.read("a\r\n\r\n").rows.get(0));
    }
    @Test public void supportsHeaderOnlyAndCrLineEndings() {
        assertTrue(Csv.read("a,b").rows.isEmpty());assertEquals(2,Csv.read("a,b\r1,2\r3,4\r").rows.size());
    }
    @Test public void quotesSemicolonAndTabWhenExportingSingleColumn() throws Exception {
        StringWriter out=new StringWriter();Csv.writeRow(out,Collections.singletonList("a;b\tc"));Csv.writeRow(out,Collections.singletonList("x"));
        assertEquals(1,Csv.read(out.toString()).headers.size());
    }
    @Test public void preservesSpacesAndFormulaTextVerbatim() throws Exception {
        StringWriter out=new StringWriter();Csv.writeRow(out,Arrays.asList("a","b"));Csv.writeRow(out,Arrays.asList("  a  ","=1+1"));
        assertEquals(Arrays.asList("  a  ","=1+1"),Csv.read(out.toString()).rows.get(0));
    }
    @Test public void rejectsMalformedQuotesAndRaggedRows() {
        for(String invalid:Arrays.asList("a,b\n\"unclosed,2","a,b\n\"x\"oops,2","a,b\n1","a,b\n1,2,3","a\nfoo\"bar"," \n"))
            assertThrows(invalid,IllegalArgumentException.class,()->Csv.read(invalid));
    }
    @Test public void rejectsExcessiveColumnCount() {
        String input=String.join(",",Collections.nCopies(101,"x"));assertThrows(IllegalArgumentException.class,()->Csv.read(input));
    }
    @Test public void randomEscapedCellsRoundTrip() throws Exception {
        Random random=new Random(71);String alphabet="aБї,;\t\"\r\n 09";
        for(int sample=0;sample<150;sample++) {
            StringWriter out=new StringWriter();Csv.writeRow(out,Arrays.asList("a","b","c"));List<String> expected=new ArrayList<>();
            for(int col=0;col<3;col++){StringBuilder value=new StringBuilder();for(int i=0;i<random.nextInt(40);i++)value.append(alphabet.charAt(random.nextInt(alphabet.length())));expected.add(value.toString());}
            Csv.writeRow(out,expected);assertEquals(expected,Csv.read(out.toString()).rows.get(0));
        }
    }
}

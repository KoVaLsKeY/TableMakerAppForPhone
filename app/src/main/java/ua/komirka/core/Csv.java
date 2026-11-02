package ua.komirka.core;

import java.io.*;
import java.util.*;

/** RFC 4180 quoting, UTF-8 BOM, comma/semicolon/tab detection; no lossy line splitting. */
public final class Csv {
    public static final int MAX_ROWS=50000, MAX_COLUMNS=100, MAX_CHARS=10*1024*1024;
    private Csv() {}
    public static final class Document {
        public final List<String> headers;
        public final List<List<String>> rows;
        public final char separator;
        Document(List<String> headers,List<List<String>> rows,char separator) {
            this.headers=headers; this.rows=rows; this.separator=separator;
        }
    }
    public static Document read(String input) {
        if (input.length()>MAX_CHARS) throw new IllegalArgumentException("The CSV is too large. The text limit is 10 MB.");
        if (input.startsWith("\uFEFF")) input=input.substring(1);
        if (input.trim().isEmpty()) throw new IllegalArgumentException("The CSV file is empty.");
        char sep=detect(input);
        List<List<String>> all=parse(input,sep);
        if (all.isEmpty()) throw new IllegalArgumentException("The CSV has no column headers.");
        List<String> headers=new ArrayList<>(all.remove(0));
        Set<String> seen=new HashSet<>();
        for(int i=0;i<headers.size();i++) {
            String h=headers.get(i).trim();
            if(h.isEmpty()) h="Column "+(i+1);
            if(h.length()>100) throw new IllegalArgumentException("Column name "+(i+1)+" exceeds 100 characters.");
            String base=h; int suffix=2;
            while(!seen.add(h.toLowerCase(Locale.ROOT))) h=base+" ("+(suffix++)+")";
            headers.set(i,h);
        }
        for(int i=0;i<all.size();i++) {
            List<String> row=all.get(i);
            if(row.size()!=headers.size()) throw new IllegalArgumentException("Record "+(i+2)+": "+row.size()+" cells instead of "+headers.size()+". Check the delimiter and quotation marks.");
        }
        return new Document(headers,all,sep);
    }
    static char detect(String s) {
        int[] counts=new int[3]; char[] separators={',',';','\t'};
        boolean quoted=false;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='"') { if(quoted && i+1<s.length() && s.charAt(i+1)=='"') i++; else quoted=!quoted; }
            if(!quoted) {
                if(c=='\r'||c=='\n') break;
                for(int j=0;j<3;j++) if(c==separators[j]) counts[j]++;
            }
        }
        int best=0; for(int j=1;j<3;j++) if(counts[j]>counts[best]) best=j;
        return separators[best];
    }
    static List<List<String>> parse(String s,char separator) {
        List<List<String>> rows=new ArrayList<>(); List<String> row=new ArrayList<>();
        StringBuilder field=new StringBuilder(); boolean quoted=false, closed=false, started=false;
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(quoted) {
                if(c=='"') {
                    if(i+1<s.length() && s.charAt(i+1)=='"') { field.append('"'); i++; }
                    else { quoted=false; closed=true; }
                } else field.append(c);
                continue;
            }
            if(c==separator || c=='\r'||c=='\n') {
                row.add(field.toString()); field.setLength(0); closed=false; started=false;
                if(row.size()>MAX_COLUMNS) throw new IllegalArgumentException("The CSV contains more than 100 columns.");
                if(c!=separator) {
                    rows.add(row); row=new ArrayList<>();
                    if(rows.size()>MAX_ROWS+1) throw new IllegalArgumentException("The CSV contains more than 50,000 records.");
                    if(c=='\r' && i+1<s.length() && s.charAt(i+1)=='\n') i++;
                }
            } else if(c=='"' && !started && field.length()==0 && !closed) { quoted=true; started=true; }
            else {
                if(closed || c=='"') throw new IllegalArgumentException("Invalid quotation marks near CSV record "+(rows.size()+1)+".");
                field.append(c); started=true;
            }
        }
        if(quoted) throw new IllegalArgumentException("The CSV contains an unclosed quotation mark.");
        if(started || closed || field.length()>0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        if(rows.size()>MAX_ROWS+1) throw new IllegalArgumentException("The CSV contains more than 50,000 records.");
        for(List<String> r:rows) if(r.size()>MAX_COLUMNS) throw new IllegalArgumentException("The CSV contains more than 100 columns.");
        return rows;
    }
    public static void writeRow(Writer writer,List<?> cells) throws IOException {
        for(int i=0;i<cells.size();i++) {
            if(i>0) writer.write(',');
            Object value=cells.get(i);
            if(value==null) continue;
            String s=String.valueOf(value);
            if(s.indexOf(',')>=0 || s.indexOf('"')>=0 || s.indexOf('\n')>=0 || s.indexOf('\r')>=0 || s.indexOf(';')>=0 || s.indexOf('\t')>=0 || s.startsWith(" ") || s.endsWith(" ")) {
                writer.write('"'); writer.write(s.replace("\"","\"\"")); writer.write('"');
            } else writer.write(s);
        }
        writer.write("\r\n");
    }
}

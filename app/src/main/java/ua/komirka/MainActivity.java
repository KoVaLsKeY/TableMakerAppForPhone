package ua.komirka;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Insets;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.*;
import java.nio.charset.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import ua.komirka.core.*;
import ua.komirka.data.*;
import ua.komirka.ui.*;
import static ua.komirka.core.Models.*;
import static ua.komirka.ui.Design.*;

public final class MainActivity extends Activity {
    public Design ui;
    public Store store;
    public Table active;
    public SchemaEditor schema;
    public EntryEditor entry;
    public LinearLayout body, footer;
    public ScrollView scroll;
    public String screen="home";
    public int screenToken;
    private SharedPreferences state;
    private final Handler main=new Handler(Looper.getMainLooper());
    private String search="";
    private int offset=0;
    private boolean grid=false, columnsTab=false;
    private LinearLayout recordsPanel;
    private Runnable searchTask;
    private static final int IMPORT_CSV=11, EXPORT_FILE=12;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static volatile Job job;
    private static final class Job {
        String title, kind;
        volatile Object result;
        volatile Exception error;
        volatile boolean done;
        WeakReference<MainActivity> owner;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui=new Design(this); store=new Store(getApplicationContext()); state=getSharedPreferences("ui",MODE_PRIVATE);
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        if(Build.VERSION.SDK_INT>=33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
        schema=new SchemaEditor(this);
        String savedSchema=state.getString("schema",null);
        try { if(savedSchema!=null) schema.restore(savedSchema); }
        catch(RuntimeException e) { toast("Налаштування нової таблиці не вдалося відновити."); }
        String id=state.getString("table",null); if(id!=null) active=store.table(id);
        screen=state.getString("screen","home");
        if(job!=null) {
            job.owner=new WeakReference<>(this); if(job.done) finishJob(job); else showBusy(job.title); return;
        }
        restoreScreen();
    }
    private void restoreScreen() {
        if(screen.startsWith("setup") && schema.table!=null) {
            Table already=store.table(schema.table.id);
            if(already!=null) { schema.clear(); showTable(already.id); return; }
            if(schema.isImport) {
                startJob("Відновлюємо імпорт…","restore",()->Csv.read(readInternalCsv())); return;
            }
            schema.render(); return;
        }
        if(screen.equals("entry") && active!=null && store.draft(active.id)!=null) { openDraft(); return; }
        if(screen.equals("table") && active!=null) { showTable(active.id); return; }
        if(screen.equals("help")) { showHelp(); return; }
        showHome();
    }
    @Override protected void onPause() { super.onPause(); persistUi(); }
    @Override protected void onSaveInstanceState(Bundle out) { persistUi(); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if(entry!=null) entry.detach();
        if(job!=null && job.owner.get()==this) job.owner=new WeakReference<>(null);
        // A task may still be using this helper; close it on the same serial worker afterwards.
        Store old=store; WORKER.execute(old::close); super.onDestroy();
    }
    public void persistUi() {
        if(entry!=null && screen.equals("entry")) entry.flush();
        SharedPreferences.Editor editor=state.edit().putString("screen",screen).putString("table",active==null?null:active.id);
        editor.putString("schema",schema==null?null:schema.snapshot()); editor.commit();
    }
    public void page(String name,String title,Runnable goBack) {
        hideKeyboard(); if(name!=null) screen=name; screenToken++;
        if(searchTask!=null) main.removeCallbacks(searchTask);
        FrameLayout frame=new FrameLayout(this); frame.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this) {
            @Override protected void onMeasure(int w,int h) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(w),ui.dp(760)),MeasureSpec.EXACTLY),h);
            }
        };
        root.setOrientation(LinearLayout.VERTICAL); root.setFocusableInTouchMode(true);
        frame.addView(root,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER_HORIZONTAL));
        if(Build.VERSION.SDK_INT>=30) frame.setOnApplyWindowInsetsListener((v,insets)-> {
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            Insets ime=insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom)); return WindowInsets.CONSUMED;
        });
        else frame.setFitsSystemWindows(true);
        LinearLayout toolbar=ui.row(); toolbar.setPadding(ui.dp(12),ui.dp(4),ui.dp(16),ui.dp(4));
        if(goBack!=null) toolbar.addView(ui.icon("←","Назад",goBack),new LinearLayout.LayoutParams(ui.dp(48),-2));
        else {
            TextView logo=ui.badge("▦",0); toolbar.addView(logo,new LinearLayout.LayoutParams(ui.dp(42),ui.dp(42))); ui.gap(toolbar,12);
        }
        ui.weight(toolbar,ui.text(title,18,INK,true));
        if("home".equals(name)) toolbar.addView(ui.icon("?","Довідка",this::showHelp),new LinearLayout.LayoutParams(ui.dp(48),-2));
        if("table".equals(name)) toolbar.addView(ui.icon("⋯","Налаштування таблиці",this::tableMenu),new LinearLayout.LayoutParams(ui.dp(48),-2));
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body=ui.column(); body.setPadding(ui.dp(22),ui.dp(18),ui.dp(22),ui.dp(24)); scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        footer=ui.column(); footer.setPadding(ui.dp(22),ui.dp(10),ui.dp(22),ui.dp(14));
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2)); footer.setVisibility(View.GONE);
        setContentView(frame); root.requestFocus(); frame.requestApplyInsets();
        if(Build.VERSION.SDK_INT>=30) frame.post(()->{
            WindowInsetsController controller=frame.getWindowInsetsController();
            if(controller!=null)controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
    }
    public void bottom(String text,Runnable action) {
        footer.setVisibility(View.VISIBLE); footer.removeAllViews(); ui.add(footer,ui.button(text,true,action));
    }
    public void showHome() {
        entry=null; active=null; page("home","Комірка",null);
        ui.add(body,ui.chip("ПРОСТІ ТАБЛИЦІ · БЕЗ ІНТЕРНЕТУ",GREEN,PALE));
        ui.add(body,ui.title("Ваші дані.\nУсе на місці."),18);
        ui.add(body,ui.muted("Збирайте спостереження, ведіть облік,\nзаписуйте важливе — у своєму темпі."),12);
        List<Table> tables=store.tables();
        if(!tables.isEmpty()) {
            long rows=0; for(Table t:tables) rows+=t.rowCount;
            LinearLayout stats=ui.row(); ui.weight(stats,stat(String.valueOf(tables.size()),Values.noun(tables.size(),"таблиця","таблиці","таблиць"))); ui.gap(stats,12); ui.weight(stats,stat(String.valueOf(rows),Values.noun(rows,"запис","записи","записів"))); ui.add(body,stats,22);
        }
        ui.add(body,ui.button("＋  Створити таблицю",true,()->{
            if(schema.table!=null) new AlertDialog.Builder(this).setTitle("Є незавершена таблиця").setMessage("Можна продовжити її налаштування або почати нову.")
                .setPositiveButton("Продовжити",(d,w)->schema.render()).setNeutralButton("Нова таблиця",(d,w)->schema.start()).setNegativeButton("Скасувати",null).show();
            else schema.start();
        }),24);
        ui.add(body,ui.button("↥  Імпортувати CSV",false,this::pickCsv),10);
        if(schema.table!=null) ui.add(body,ui.quiet("Продовжити налаштування «"+(schema.table.name.isEmpty()?"Нова таблиця":schema.table.name)+"»",schema::render),8);
        ui.section(body,"Мої таблиці",tables.isEmpty()?null:"Усі зміни зберігаються на цьому пристрої");
        if(tables.isEmpty()) {
            LinearLayout empty=ui.card(); ui.add(empty,ui.text("Місце для ваших ідей",21,INK,true));
            ui.add(empty,ui.muted("Створіть першу таблицю, додайте потрібні стовпці — і починайте з одного запису."),10);
            ui.add(empty,ui.quiet("Спробувати на прикладі",this::createExample),18); ui.add(body,empty,14);
        } else {
            EditText query=ui.input("Знайти таблицю…","",InputType.TYPE_CLASS_TEXT); ui.add(body,query,14);
            LinearLayout list=ui.column(); ui.add(body,list,12); fillTables(list,tables,"");
            ui.watch(query,s->fillTables(list,tables,s));
        }
        ui.add(body,ui.muted("Без реєстрації. Без реклами. Дані залишаються у вас."),22);
    }
    private View stat(String value,String label) {
        LinearLayout c=ui.card(); ui.add(c,ui.text(value,27,INK,true)); ui.add(c,ui.muted(label),6); return c;
    }
    private void fillTables(LinearLayout list,List<Table> tables,String query) {
        list.removeAllViews(); int count=0;
        for(Table t:tables) {
            if(!t.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            count++; LinearLayout card=ui.card(), top=ui.row();
            TextView icon=ui.badge("▦",t.color); top.addView(icon,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48))); ui.gap(top,14);
            LinearLayout titles=ui.column(); ui.add(titles,ui.text(t.name,19,INK,true)); ui.add(titles,ui.muted(Values.records(t.rowCount)+"  ·  "+Values.columns(t.columns.size()+(t.idMode==IdMode.NONE?0:1))),6); ui.weight(top,titles);
            ui.gap(top,8); top.addView(ui.text("›",28,MUTED,false)); ui.add(card,top);
            if(!t.description.isEmpty()) ui.add(card,ui.muted(t.description),12);
            Draft draft=store.draft(t.id);
            ui.add(card,ui.text(draft!=null?"●  Є чернетка":"Оновлено "+date(t.updatedAt),12,draft!=null?GREEN:MUTED,false),14);
            ui.clickable(card,()->showTable(t.id)); ui.add(list,card,10);
        }
        if(count==0) ui.add(list,ui.muted("Таблиць із такою назвою немає."),16);
    }
    private void createExample() {
        Table t=new Table(); t.name="Мої спостереження"; t.description="Приклад: можете змінити назву або видалити таблицю"; t.idMode=IdMode.INCREMENT;
        Column note=new Column("Спостереження",Kind.STRING), mood=new Column("Оцінка",Kind.ORDINAL), done=new Column("Перевірено",Kind.BOOL);
        mood.options.addAll(Arrays.asList("Низька","Середня","Висока")); t.columns.addAll(Arrays.asList(note,mood,done)); store.create(t); showTable(t.id);
    }
    public void showTable(String id) {
        active=store.table(id); if(active==null) { showHome(); return; }
        entry=null; search=""; offset=0; columnsTab=false; renderTable();
    }
    private void renderTable() {
        active=store.table(active.id); if(active==null) { showHome(); return; }
        page("table","Мої таблиці",this::showHome);
        ui.add(body,ui.title(active.name)); if(!active.description.isEmpty()) ui.add(body,ui.muted(active.description),10);
        ui.add(body,ui.chip(Values.records(active.rowCount)+"  ·  "+Values.columns(active.columns.size()+(active.idMode==IdMode.NONE?0:1)),GREEN,PALE),14);
        Draft draft=store.draft(active.id);
        if(draft!=null) {
            LinearLayout banner=ui.card(); ui.background(banner,PALE,18,0);
            ui.add(banner,ui.label(draft.rowId==null?"На вас чекає чернетка":"Незавершене редагування"));
            ui.add(banner,ui.muted("Запис ще не змінює таблицю. Продовжіть, коли буде зручно."),7);
            ui.add(banner,ui.button("Продовжити",true,this::openDraft),12);
            ui.add(banner,ui.quiet("Видалити чернетку",()->confirm("Видалити чернетку?","Збережені записи залишаться в таблиці.","Видалити",()->{store.clearDraft(active.id);renderTable();})),4);
            ui.add(body,banner,18);
        }
        LinearLayout tabs=ui.row(); ui.weight(tabs,ui.button("Записи",!columnsTab,()->{columnsTab=false;renderTable();})); ui.gap(tabs,8);
        ui.weight(tabs,ui.button("Стовпці",columnsTab,()->{columnsTab=true;renderTable();})); ui.add(body,tabs,22);
        if(columnsTab) {
            if(active.idMode!=IdMode.NONE) {
                LinearLayout card=ui.card(); ui.add(card,ui.label(active.idName+" · ID")); ui.add(card,ui.muted(active.idMode.title),6); ui.add(body,card,12);
            }
            for(Column c:active.columns) {
                LinearLayout card=ui.card(); ui.add(card,ui.label(c.name)); ui.add(card,ui.muted(c.kind.title+" · "+c.kind.code),6);
                if(c.kind.options()) ui.add(card,ui.muted(String.join(c.kind==Kind.ORDINAL?"  →  ":"  ·  ",c.options)),12); ui.add(body,card,12);
            }
            ui.info(body,"Типи стовпців визначаються під час створення. Щоб змінити структуру, експортуйте CSV та імпортуйте його з новими типами.");
        } else {
            EditText query=ui.input("Пошук у записах…",search,InputType.TYPE_CLASS_TEXT); ui.add(body,query,16);
            LinearLayout viewChoice=ui.row(); Button toggle=ui.quiet(grid?"☷  Списком":"▦  Таблицею",()->{}); toggle.setOnClickListener(v->{grid=!grid;toggle.setText(grid?"☷  Списком":"▦  Таблицею");renderRecords();}); ui.weight(viewChoice,toggle);
            ui.weight(viewChoice,ui.quiet("↧  Експорт",this::chooseExport)); ui.add(body,viewChoice,8);
            recordsPanel=ui.column(); ui.add(body,recordsPanel,8); renderRecords();
            ui.watch(query,s->{search=s;offset=0;if(searchTask!=null)main.removeCallbacks(searchTask);searchTask=this::renderRecords;main.postDelayed(searchTask,220);});
        }
        bottom("＋  Новий запис",this::newRecord);
    }
    private void renderRecords() {
        if(!screen.equals("table") || recordsPanel==null || columnsTab) return;
        recordsPanel.removeAllViews(); long count=store.countRows(active.id,search); List<Row> rows=store.rows(active.id,search,40,offset);
        if(rows.isEmpty()) {
            LinearLayout empty=ui.card(); ui.add(empty,ui.text(search.isEmpty()?"Перший запис — початок":"Нічого не знайдено",20,INK,true));
            ui.add(empty,ui.muted(search.isEmpty()?"Натисніть «Новий запис». Додаток проведе вас через кожне поле.":"Спробуйте інші слова або очистьте пошук."),12); ui.add(recordsPanel,empty,8); return;
        }
        ui.add(recordsPanel,ui.muted("Показано "+(offset+1)+"–"+(offset+rows.size())+" із "+count),4);
        if(grid) renderGrid(rows); else for(Row row:rows) {
            LinearLayout card=ui.card(), top=ui.row();
            ui.weight(top,ui.text(active.idMode==IdMode.NONE?"Запис · "+date(row.createdAt):active.idName+": "+row.externalId,13,GREEN,true)); top.addView(ui.text("↗",20,GREEN,false)); ui.add(card,top);
            int i=0; for(Column col:active.columns) {
                if(i++==3) break; ui.add(card,ui.muted(col.name),12); TextView value=ui.text(Values.display(row.values.get(col.key)),16,row.values.get(col.key)==null?MUTED:INK,i==1); value.setMaxLines(2); value.setEllipsize(android.text.TextUtils.TruncateAt.END); ui.add(card,value,4);
            }
            if(active.columns.size()>3) {int rest=active.columns.size()-3;ui.add(card,ui.muted("Ще "+rest+" "+Values.noun(rest,"поле","поля","полів")+" · натисніть, щоб відкрити"),12);}
            ui.clickable(card,()->openRecord(row)); ui.add(recordsPanel,card,12);
        }
        LinearLayout paging=ui.row(); Button prev=ui.button("← Назад",false,()->{offset=Math.max(0,offset-40);renderRecords();scroll.smoothScrollTo(0,0);}); prev.setEnabled(offset>0); prev.setAlpha(offset>0?1:.4f); ui.weight(paging,prev); ui.gap(paging,10);
        Button next=ui.button("Далі →",false,()->{offset+=40;renderRecords();scroll.smoothScrollTo(0,0);}); next.setEnabled(offset+rows.size()<count); next.setAlpha(next.isEnabled()?1:.4f); ui.weight(paging,next); ui.add(recordsPanel,paging,18);
    }
    private void renderGrid(List<Row> rows) {
        HorizontalScrollView horizontal=new HorizontalScrollView(this); LinearLayout table=ui.column();
        LinearLayout header=ui.row(); cell(header,"Запис",true); if(active.idMode!=IdMode.NONE) cell(header,active.idName,true); for(Column c:active.columns) cell(header,c.name,true); ui.add(table,header);
        for(Row row:rows) {
            LinearLayout line=ui.row(); Button edit=ui.button("Відкрити ↗",false,()->openRecord(row)); line.addView(edit,new LinearLayout.LayoutParams(ui.dp(145),-2));
            if(active.idMode!=IdMode.NONE) cell(line,row.externalId,false); for(Column c:active.columns) cell(line,Values.display(row.values.get(c.key)),false); ui.add(table,line,2);
        }
        horizontal.addView(table); ui.add(recordsPanel,horizontal,12);
    }
    private void cell(LinearLayout row,String text,boolean header) {
        TextView v=ui.text(text,14,header?GREEN:INK,header); v.setPadding(ui.dp(12),ui.dp(16),ui.dp(12),ui.dp(16)); v.setMinHeight(ui.dp(54)); v.setMaxLines(3); v.setEllipsize(android.text.TextUtils.TruncateAt.END); ui.background(v,header?PALE:WHITE,4,BORDER); row.addView(v,new LinearLayout.LayoutParams(ui.dp(145),-2));
    }
    public void newRecord() {
        if(store.draft(active.id)!=null) {
            new AlertDialog.Builder(this).setTitle("У вас є чернетка").setMessage("Продовжити її чи замінити новим записом?")
                .setPositiveButton("Продовжити",(d,w)->openDraft()).setNeutralButton("Замінити",(d,w)->{store.clearDraft(active.id);newRecord();}).setNegativeButton("Скасувати",null).show(); return;
        }
        Draft draft=new Draft(); draft.tableId=active.id; store.saveDraft(draft); entry=new EntryEditor(this,draft); entry.render();
    }
    public void openDraft() {
        Draft draft=store.draft(active.id); if(draft==null) { newRecord(); return; }
        entry=new EntryEditor(this,draft); entry.render();
    }
    private void openRecord(Row row) {
        Draft old=store.draft(active.id);
        Runnable open=()->{ Draft d=store.editDraft(active,row); d.step=active.columns.size()+(active.idMode==IdMode.MANUAL?1:0); store.saveDraft(d); entry=new EntryEditor(this,d); entry.render(); };
        if(old!=null && !row.id.equals(old.rowId)) {
            new AlertDialog.Builder(this).setTitle("Є інша чернетка").setMessage("Щоб редагувати цей запис, потрібно замінити поточну чернетку.")
                .setPositiveButton("Замінити чернетку",(d,w)->open.run()).setNegativeButton("Скасувати",null).show();
        } else if(old!=null) openDraft(); else open.run();
    }
    public void leaveEntry() { if(entry!=null) { if(!entry.flush())return;entry.detach();entry=null; } renderTable(); }
    private void tableMenu() {
        new AlertDialog.Builder(this).setTitle(active.name).setItems(new String[]{"Експортувати CSV / JSON","Змінити назву й опис","Видалити таблицю"},(d,which)->{
            if(which==0) chooseExport(); else if(which==1) rename(); else confirm("Видалити «"+active.name+"»?","Буде видалено всі записи, їхню історію та чернетку. Цю дію не можна скасувати.","Видалити таблицю",()->{store.deleteTable(active.id);showHome();});
        }).show();
    }
    private void rename() {
        LinearLayout panel=ui.column(); panel.setPadding(ui.dp(22),ui.dp(12),ui.dp(22),0);
        EditText name=ui.input("Назва таблиці",active.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES), desc=ui.input("Опис (необов’язково)",active.description,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ui.add(panel,name); ui.add(panel,desc,12); TextView error=ui.error(panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Назва й опис").setView(panel).setPositiveButton("Зберегти",null).setNegativeButton("Скасувати",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{try {store.rename(active,name.getText().toString(),desc.getText().toString());dialog.dismiss();renderTable();} catch(Exception e) {error.setText(e.getMessage());}}));dialog.show();
    }
    private void chooseExport() {
        new AlertDialog.Builder(this).setTitle("Зберегти таблицю як…").setItems(new String[]{"CSV · поточні значення, для Excel та аналізу","JSON · типи, категорії та історія значень"},(d,n)->{
            state.edit().putString("exportTable",active.id).putBoolean("exportJson",n==1).commit();
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(n==1?"application/json":"text/csv");
            String filename=active.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+"."+(n==1?"json":"csv"); intent.putExtra(Intent.EXTRA_TITLE,filename);
            try { startActivityForResult(intent,EXPORT_FILE); } catch(ActivityNotFoundException ex) { error("На пристрої немає системного засобу збереження файлів."); }
        }).setNegativeButton("Скасувати",null).show();
    }
    public void pickCsv() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { startActivityForResult(intent,IMPORT_CSV); } catch(ActivityNotFoundException ex) { error("На пристрої немає системного засобу вибору файлів."); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        if(request==IMPORT_CSV) {
            String filename=fileName(uri);
            startJob("Читаємо й перевіряємо CSV…","read",()->{
                String text=readCsv(uri); Csv.Document doc=Csv.read(text);
                try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(getFilesDir(),"pending-import.csv")),StandardCharsets.UTF_8)) {writer.write(text);}
                return new Object[]{doc,filename};
            });
        } else if(request==EXPORT_FILE) {
            Table t=store.table(state.getString("exportTable","")); boolean json=state.getBoolean("exportJson",false);
            if(t==null) { error("Таблицю для експорту не знайдено."); return; }
            startJob("Зберігаємо "+(json?"JSON":"CSV")+"…","export",()->{
                try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")) {
                    if(stream==null) throw new IOException("Не вдалося відкрити файл для запису.");
                    try(Writer writer=new BufferedWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8))) { store.export(t,writer,json); }
                }
                return t;
            });
        }
    }
    private String fileName(Uri uri) {
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) { if(c!=null && c.moveToFirst()) return c.getString(0); }
        catch(Exception ignored) {} return "Імпортована таблиця.csv";
    }
    private String readCsv(Uri uri) throws IOException {
        try(InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null) throw new IOException("Не вдалося відкрити CSV.");
            byte[] buffer=new byte[8192]; int size;
            while((size=in.read(buffer))!=-1) { if(out.size()+size>10*1024*1024) throw new IOException("Файл завеликий. Максимум — 10 МБ."); out.write(buffer,0,size); }
            byte[] bytes=out.toByteArray(); Charset charset=StandardCharsets.UTF_8;
            if(bytes.length>=2 && ((bytes[0]&255)==255 && (bytes[1]&255)==254 || (bytes[0]&255)==254 && (bytes[1]&255)==255)) charset=StandardCharsets.UTF_16;
            try { return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
            catch(CharacterCodingException e) { throw new IOException("Не вдалося прочитати кодування. Збережіть CSV як UTF-8 або UTF-16 із BOM."); }
        }
    }
    private String readInternalCsv() throws IOException {
        try(InputStream stream=new FileInputStream(new File(getFilesDir(),"pending-import.csv"));ByteArrayOutputStream result=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int read;while((read=stream.read(buffer))!=-1)result.write(buffer,0,read);
            return new String(result.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    public void resumeImportSchema() {
        startJob("Відновлюємо імпорт…","restore",()->Csv.read(readInternalCsv()));
    }
    public void startJob(String title,String kind,Callable<Object> action) {
        if(job!=null)return;
        persistUi(); Job current=new Job(); current.title=title;current.kind=kind;current.owner=new WeakReference<>(this);job=current;showBusy(title);
        WORKER.execute(()->{
            try { current.result=action.call(); } catch(Exception e) { current.error=e; }
            current.done=true;
            new Handler(Looper.getMainLooper()).post(()->{MainActivity owner=current.owner.get();if(owner!=null&&!owner.isDestroyed())owner.finishJob(current);});
        });
    }
    private void showBusy(String title) {
        page(null,"Комірка",null); ui.space(body,60); ProgressBar progress=new ProgressBar(this); ui.add(body,progress);
        ui.add(body,ui.title(title),24);ui.add(body,ui.muted("Це може зайняти кілька секунд."),14);
    }
    private void finishJob(Job current) {
        if(job!=current) return; job=null;
        if(current.error!=null) {
            if(current.kind.equals("create") && schema.table!=null) schema.columns(); else if(active!=null) showTable(active.id); else showHome();
            String extra=current.kind.equals("export")?" Файл може бути неповним; повторіть експорт.":"";
            error((current.error.getMessage()==null?"Операцію не вдалося завершити.":current.error.getMessage())+extra); return;
        }
        switch(current.kind) {
            case "read": Object[] read=(Object[])current.result; schema.fromCsv((Csv.Document)read[0],(String)read[1]);break;
            case "restore": schema.imported=(Csv.Document)current.result;schema.render();break;
            case "create": Table t=(Table)current.result;schema.clear();showTable(t.id);toast("Таблицю збережено");break;
            case "export": showTable(((Table)current.result).id);toast("Файл успішно збережено");break;
        }
        persistUi();
    }
    private void showHelp() {
        page("help","Коротко про Комірку",this::showHome); ui.add(body,ui.title("Менше дій.\nБільше порядку."));
        help("01  Створіть свою таблицю","Назвіть таблицю, оберіть режим ID і додайте стовпці. Для category та ordinal складіть свій список. Порядок ordinal змінюється стрілками.");
        help("02  Вводьте по одному полю","Текст відкриває клавіатуру, числа — цифрову панель. Категорії, дати та Так/Ні обираються натисканням. Повернутися до будь-якого поля можна на екрані перевірки.");
        help("03  Завершуйте, коли готові","До фінального підтвердження дані є чернеткою. Вийдіть із додатка та продовжіть пізніше. У кожної таблиці є одна чернетка.");
        help("04  Редагуйте з історією","Натисніть запис. Змінені значення залишаться в «Історії / aliases». Їх можна відновити, виправити або видалити. Зміни історії також підтверджуються фінальною кнопкою.");
        help("05  Обмінюйтесь даними","CSV містить заголовки та поточні значення. Порожня клітинка означає пропущене значення. JSON додатково містить схему, категорії, порядок ordinal та aliases. Імпорт підтримує CSV: кома, крапка з комою або табуляція; UTF-8 / UTF-16 із BOM.");
        help("Ваші дані — на вашому пристрої","Інтернет і обліковий запис не потрібні. Регулярно експортуйте важливі таблиці. Видалення додатка чи очищення його даних видаляє локальні таблиці. JSON — для експорту; повторний імпорт доступний для CSV.");
        ui.info(body,"Версія 1.0 · Android 8.0 і новіші\nДо 100 стовпців; імпорт до 50 000 записів і 10 МБ.");
    }
    private void help(String title,String content) { ui.section(body,title,content); }
    public void error(String message) { new AlertDialog.Builder(this).setTitle("Перевірте, будь ласка").setMessage(message).setPositiveButton("Зрозуміло",null).show(); }
    public void confirm(String title,String message,String action,Runnable yes) {
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(action,(dialog,w)->yes.run()).setNegativeButton("Скасувати",null).create();
        d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(RED)); d.show();
    }
    public void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    public void hideKeyboard() { View v=getCurrentFocus(); if(v!=null) ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0); }
    public void keyboard(EditText e) {
        int token=screenToken; e.postDelayed(()->{if(!isDestroyed()&&token==screenToken&&e.isAttachedToWindow()) {e.requestFocus();e.setSelection(e.length());((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(e,InputMethodManager.SHOW_IMPLICIT);}},200);
    }
    public String date(long value) { return new SimpleDateFormat("dd.MM.yyyy",Locale.forLanguageTag("uk-UA")).format(new Date(value)); }
    // API 33+ uses the platform OnBackInvokedDispatcher registered in onCreate.
    // Keep this override exclusively for the Android 8–12 fallback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { if(Build.VERSION.SDK_INT<33)back(); }
    public void back() {
        if(job!=null) { toast("Дочекайтеся завершення операції");return; }
        if(screen.equals("entry") && entry!=null) { if(entry.draft.step>0) entry.previous();else leaveEntry(); }
        else if(screen.equals("setup_column")) schema.columns();
        else if(screen.equals("setup_columns")) schema.basics();
        else if(screen.equals("home")) finish(); else showHome();
    }
}

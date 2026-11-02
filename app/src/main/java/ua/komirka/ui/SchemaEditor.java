package ua.komirka.ui;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import ua.komirka.MainActivity;
import ua.komirka.core.*;
import ua.komirka.data.Codec;
import static ua.komirka.core.Models.*;
import static ua.komirka.ui.Design.*;

public final class SchemaEditor {
    private final MainActivity a;
    private final Design u;
    public Table table;
    public Csv.Document imported;
    public boolean isImport=false;
    private String stage="basic";
    private Column editing;
    private int editingIndex=-1;
    private LinearLayout optionsPanel, optionsList;
    private EditText optionInput;
    private TextView columnError;
    private int optionsPage;
    public SchemaEditor(MainActivity activity) { a=activity;u=a.ui; }
    public void start() { clear();table=new Table();table.color=a.store.tables().size()%4;basics(); }
    public void clear() { table=null;imported=null;isImport=false;editing=null;stage="basic"; }
    public String snapshot() {
        if(table==null) return null;
        JSONObject o=new JSONObject(); Codec.put(o,"table",Codec.table(table));Codec.put(o,"import",isImport);Codec.put(o,"stage",stage);
        Codec.put(o,"editing",editing==null?null:Codec.column(editing));Codec.put(o,"index",editingIndex);return o.toString();
    }
    public void restore(String text) {
        JSONObject o=Codec.object(text);table=Codec.table(o.optJSONObject("table"));isImport=o.optBoolean("import");stage=o.optString("stage","basic");
        editing=o.isNull("editing")?null:Codec.column(o.optJSONObject("editing"));editingIndex=o.optInt("index",-1);
    }
    public void render() {
        if(table==null) {start();return;}
        if(isImport&&imported==null){a.resumeImportSchema();return;}
        if(stage.equals("column")&&editing!=null) column();else if(stage.equals("columns"))columns();else basics();
    }
    public void fromCsv(Csv.Document doc,String filename) {
        clear();isImport=true;imported=doc;table=new Table();table.color=a.store.tables().size()%4;
        table.name=filename.replaceFirst("(?i)\\.csv$",""); if(table.name.length()>120)table.name=table.name.substring(0,120);
        for(int i=0;i<doc.headers.size();i++) {
            List<String> values=new ArrayList<>();for(List<String> row:doc.rows) values.add(row.get(i));
            Column c=new Column(doc.headers.get(i),Values.infer(values));c.sourceIndex=i;table.columns.add(c);
        }
        basics();
    }
    public void basics() {
        stage="basic";a.page("setup_basic",isImport?"Імпорт CSV":"Нова таблиця",a::showHome);
        u.add(a.body,u.chip("КРОК 1 ІЗ 2 · ОСНОВНЕ",GREEN,PALE));
        u.add(a.body,u.title(isImport?"Знайомимося\nз вашими даними":"Почнімо з назви"),18);
        u.add(a.body,u.muted(isImport?"Перевірте назву. Далі можна змінити тип кожного стовпця.":"Що будете записувати? Назву й опис можна буде змінити пізніше."),12);
        u.section(a.body,"Назва таблиці",null);
        EditText name=u.input("Наприклад, Польові спостереження",table.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setMaxLines(2);u.watch(name,s->table.name=s);u.add(a.body,name,10);
        u.section(a.body,"Короткий опис",null);EditText description=u.input("Необов’язково",table.description,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        description.setMinLines(2);u.watch(description,s->table.description=s);u.add(a.body,description,10);
        if(isImport && imported!=null) u.info(a.body,"Знайдено "+Values.records(imported.rows.size())+" і "+Values.columns(imported.headers.size())+".\nПерший рядок використано як заголовки. Порожні клітинки → null.");
        u.section(a.body,"Окремий стовпець ID","Він допомагає однозначно розрізняти записи.");
        RadioGroup modes=new RadioGroup(a);modes.setOrientation(RadioGroup.VERTICAL);
        LinearLayout idField=u.column();EditText idName=u.input("Назва стовпця ID",table.idName,InputType.TYPE_CLASS_TEXT);u.watch(idName,s->table.idName=s);u.add(idField,idName);
        for(IdMode mode:IdMode.values()) {
            if(isImport&&mode==IdMode.MANUAL)continue;
            RadioButton radio=new RadioButton(a);radio.setId(View.generateViewId());radio.setText(mode.title+"\n"+mode.description);radio.setTextSize(15);radio.setTextColor(INK);radio.setPadding(u.dp(8),u.dp(12),u.dp(12),u.dp(12));radio.setMinHeight(u.dp(64));radio.setTag(mode);
            modes.addView(radio,new RadioGroup.LayoutParams(-1,-2));radio.setChecked(table.idMode==mode);
        }
        modes.setOnCheckedChangeListener((group,id)->{RadioButton radio=group.findViewById(id);if(radio!=null) {table.idMode=(IdMode)radio.getTag();idField.setVisibility(table.idMode==IdMode.NONE?View.GONE:View.VISIBLE);}});
        u.add(a.body,modes,10);u.add(a.body,idField,10);idField.setVisibility(table.idMode==IdMode.NONE?View.GONE:View.VISIBLE);
        if(isImport)u.info(a.body,"Наявний у CSV стовпець ID лишається звичайним стовпцем. Автоматичний ID, якщо його обрати, додається окремо з іншою назвою.");
        TextView error=u.error(a.body);
        a.bottom("Далі: стовпці →",()->{
            table.name=table.name.trim();table.description=table.description.trim();table.idName=table.idName.trim();
            if(table.name.isEmpty()||table.name.length()>120) {error.setText("Вкажіть назву таблиці — від 1 до 120 символів.");name.requestFocus();return;}
            try {if(table.idMode!=IdMode.NONE)Values.validateName(table.idName);columns();}catch(IllegalArgumentException ex){error.setText(ex.getMessage());}
        });
    }
    public void columns() {
        stage="columns";editing=null;a.page("setup_columns",isImport?"Перевірка імпорту":"Нова таблиця",this::basics);
        u.add(a.body,u.chip("КРОК 2 ІЗ 2 · СТОВПЦІ",GREEN,PALE));u.add(a.body,u.title("Що записуватимемо?"),18);
        u.add(a.body,u.muted(isImport?"Типи визначено автоматично. Натисніть стовпець, щоб перевірити або змінити їх.":"Додайте потрібні поля. Під час введення вони з’являтимуться в цьому порядку."),12);
        if(table.idMode!=IdMode.NONE)u.info(a.body,table.idName+" · "+table.idMode.title);
        if(table.columns.isEmpty()) {
            LinearLayout empty=u.card();u.add(empty,u.label("Кожне поле — окремий стовпець"));u.add(empty,u.muted("Наприклад: Назва → текст, Кількість → ціле число, Стан → категорія."),10);u.add(a.body,empty,22);
        }
        for(int index=0;index<table.columns.size();index++) {
            final int position=index;Column c=table.columns.get(index);LinearLayout card=u.card(),heading=u.row();
            TextView badge=u.badge(c.kind.mark,index);heading.addView(badge,new LinearLayout.LayoutParams(u.dp(44),u.dp(44)));u.gap(heading,12);
            LinearLayout labels=u.column();u.add(labels,u.label(c.name));u.add(labels,u.muted(c.kind.title+" · "+c.kind.code),5);u.weight(heading,labels);
            heading.addView(u.icon("✎","Налаштувати «"+c.name+"»",()->edit(position)),new LinearLayout.LayoutParams(u.dp(48),-2));u.add(card,heading);
            if(c.kind.options())u.add(card,u.muted(String.join(c.kind==Kind.ORDINAL?" → ":" · ",c.options)),12);
            if(isImport&&imported!=null&&c.sourceIndex>=0) {
                List<String> preview=new ArrayList<>();for(int r=0;r<Math.min(3,imported.rows.size());r++) {String value=imported.rows.get(r).get(c.sourceIndex);preview.add(value.isEmpty()?"null":value);}
                u.add(card,u.muted("Приклади: "+String.join(" · ",preview)),10);
            }
            LinearLayout tools=u.row();u.weight(tools,u.muted("Поле "+(index+1)));
            Button up=u.icon("↑","Перемістити «"+c.name+"» вище",()->{Collections.swap(table.columns,position,position-1);columns();});up.setEnabled(index>0);tools.addView(up,new LinearLayout.LayoutParams(u.dp(48),-2));
            Button down=u.icon("↓","Перемістити «"+c.name+"» нижче",()->{Collections.swap(table.columns,position,position+1);columns();});down.setEnabled(index<table.columns.size()-1);tools.addView(down,new LinearLayout.LayoutParams(u.dp(48),-2));
            tools.addView(u.icon("×","Прибрати «"+c.name+"»",()->a.confirm("Прибрати стовпець?",c.name+(isImport?" не буде імпортовано.":" буде прибрано зі структури."),"Прибрати",()->{table.columns.remove(position);columns();})),new LinearLayout.LayoutParams(u.dp(48),-2));
            u.add(card,tools,10);u.clickable(card,()->edit(position));u.add(a.body,card,14);
        }
        u.add(a.body,u.button("＋  Додати стовпець",false,()->{editingIndex=-1;editing=new Column();column();}),18);
        if(isImport)u.info(a.body,"Категорії можна вибрати у налаштуваннях стовпця. Для ordinal розташуйте варіанти від найменшого до найбільшого. Додані нові стовпці матимуть null в імпортованих записах.");
        else u.info(a.body,"Можна почати з кількох полів. Відсутнє значення завжди можна позначити як null.");
        a.bottom(isImport?"Імпортувати таблицю":"Створити таблицю",this::create);
    }
    private void edit(int index) { editingIndex=index;editing=table.columns.get(index).copy();column(); }
    private void column() {
        stage="column";a.page("setup_column",editingIndex<0?"Новий стовпець":"Налаштування стовпця",this::columns);
        u.add(a.body,u.title("Одне поле.\nПотрібний формат."));
        u.section(a.body,"Назва стовпця",null);EditText name=u.input("Наприклад, Температура",editing.name,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);u.watch(name,s->editing.name=s);u.add(a.body,name,10);
        u.section(a.body,"Тип даних","Оберіть, як вам буде зручно вводити це значення.");
        Spinner type=new Spinner(a);List<String> names=new ArrayList<>();for(Kind kind:Kind.values())names.add(kind.title+"  ·  "+kind.code);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(adapter);type.setSelection(editing.kind.ordinal());type.setMinimumHeight(u.dp(56));u.background(type,WHITE,14,BORDER);type.setContentDescription("Тип даних стовпця");u.add(a.body,type,10);
        TextView hint=u.muted(editing.kind.description);u.add(a.body,hint,10);
        optionsPanel=u.column();u.add(a.body,optionsPanel,16);
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id) {
                editing.kind=Kind.values()[position];hint.setText(editing.kind.description);
                if(editing.kind.options() && editing.options.isEmpty() && imported!=null && editing.sourceIndex>=0) {
                    LinkedHashSet<String> unique=new LinkedHashSet<>();for(List<String> row:imported.rows) {String s=row.get(editing.sourceIndex);if(!s.trim().isEmpty())unique.add(s);}
                    editing.options.addAll(unique);
                }
                renderOptions();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        renderOptions();columnError=u.error(a.body);
        a.bottom("Далі: зберегти стовпець",()->{
            try {
                if(editing.kind.options() && optionInput!=null && !optionInput.getText().toString().trim().isEmpty()) if(!addOption()) return;
                editing.name=editing.name.trim();Values.validateName(editing.name);
                if(table.idMode!=IdMode.NONE && editing.name.equalsIgnoreCase(table.idName))throw new IllegalArgumentException("Цю назву вже використовує стовпець ID.");
                for(int i=0;i<table.columns.size();i++)if(i!=editingIndex && table.columns.get(i).name.equalsIgnoreCase(editing.name))throw new IllegalArgumentException("Стовпець із такою назвою вже існує.");
                if(editing.kind.options()&&editing.options.isEmpty())throw new IllegalArgumentException("Додайте хоча б одну категорію.");
                if(editingIndex<0) {if(table.columns.size()>=100)throw new IllegalArgumentException("Максимум — 100 стовпців.");table.columns.add(editing.copy());}
                else table.columns.set(editingIndex,editing.copy());columns();
            }catch(IllegalArgumentException ex){columnError.setText(ex.getMessage());}
        });
    }
    private void renderOptions() {
        optionsPanel.removeAllViews();optionInput=null;optionsPage=0;
        if(!editing.kind.options()) {
            String info=editing.kind==Kind.BOOL?"Під час введення будуть дві великі кнопки: ✓ Так та ✕ Ні.":editing.kind==Kind.STRING?"Клавіатура відкриється автоматично. Поруч буде кнопка «Немає значення».":editing.kind==Kind.DATE?"Дату можна вибрати в календарі або ввести у форматі РРРР-ММ-ДД.":"Числова клавіатура відкриється автоматично. Додаток перевірить формат перед переходом далі.";
            u.info(optionsPanel,info);return;
        }
        u.add(optionsPanel,u.label(editing.kind==Kind.ORDINAL?"Категорії за зростанням":"Ваші категорії"));
        u.add(optionsPanel,u.muted(editing.kind==Kind.ORDINAL?"Перший варіант — найнижчий рівень. Змінюйте порядок стрілками.":"Додавайте варіанти по одному. Натисніть «Далі», коли список готовий."),8);
        LinearLayout add=u.row();optionInput=u.input("Нова категорія","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);optionInput.setSingleLine(true);u.weight(add,optionInput);u.gap(add,8);add.addView(u.icon("＋","Додати категорію",this::addOption),new LinearLayout.LayoutParams(u.dp(54),-2));u.add(optionsPanel,add,14);
        optionInput.setOnEditorActionListener((v,id,event)->{addOption();return true;});
        optionsList=u.column();u.add(optionsPanel,optionsList,12);fillOptions();
    }
    private boolean addOption() {
        String value=optionInput.getText().toString().trim();
        if(value.isEmpty()) {optionInput.setError("Введіть назву категорії");return false;}
        if(editing.options.contains(value)) {optionInput.setError("Така категорія вже є");return false;}
        editing.options.add(value);optionsPage=(editing.options.size()-1)/40;optionInput.setText("");fillOptions();return true;
    }
    private void fillOptions() {
        optionsList.removeAllViews();
        optionsPage=Math.min(optionsPage,Math.max(0,(editing.options.size()-1)/40));
        int from=optionsPage*40,to=Math.min(from+40,editing.options.size());
        if(editing.options.size()>40)u.add(optionsList,u.muted("Категорії "+(from+1)+"–"+to+" із "+editing.options.size()),8);
        for(int i=from;i<to;i++) {
            final int index=i;LinearLayout row=u.row();u.background(row,WHITE,12,BORDER);row.setPadding(u.dp(12),u.dp(4),u.dp(4),u.dp(4));
            u.weight(row,u.text((editing.kind==Kind.ORDINAL?(i+1)+".  ":"")+editing.options.get(i),15,INK,false));
            if(editing.kind==Kind.ORDINAL) {
                Button up=u.icon("↑","Підняти категорію",()->{Collections.swap(editing.options,index,index-1);fillOptions();});up.setEnabled(i>0);row.addView(up,new LinearLayout.LayoutParams(u.dp(48),-2));
                Button down=u.icon("↓","Опустити категорію",()->{Collections.swap(editing.options,index,index+1);fillOptions();});down.setEnabled(i<editing.options.size()-1);row.addView(down,new LinearLayout.LayoutParams(u.dp(48),-2));
            }
            row.addView(u.icon("×","Прибрати категорію «"+editing.options.get(i)+"»",()->{editing.options.remove(index);fillOptions();}),new LinearLayout.LayoutParams(u.dp(48),-2));u.add(optionsList,row,6);
        }
        if(editing.options.size()>40) {
            LinearLayout pages=u.row();Button prev=u.button("← Попередні",false,()->{optionsPage--;fillOptions();});prev.setEnabled(optionsPage>0);u.weight(pages,prev);u.gap(pages,8);
            Button next=u.button("Наступні →",false,()->{optionsPage++;fillOptions();});next.setEnabled(to<editing.options.size());u.weight(pages,next);u.add(optionsList,pages,12);
        }
    }
    private void create() {
        try {
            Values.validate(table);if(isImport&&imported==null)throw new IllegalArgumentException("Оберіть CSV-файл повторно.");
            Table copy=Codec.table(Codec.table(table));Csv.Document doc=imported;
            a.startJob(isImport?"Імпортуємо записи…":"Створюємо таблицю…","create",()->{if(isImport)a.store.importCsv(copy,doc);else a.store.create(copy);return copy;});
        }catch(IllegalArgumentException ex){a.error(ex.getMessage());}
    }
}

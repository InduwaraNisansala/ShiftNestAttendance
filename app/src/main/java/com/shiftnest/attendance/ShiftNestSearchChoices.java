package com.shiftnest.attendance;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import java.util.*;
import java.util.function.IntConsumer;

/** Search names while keeping each result bound to its original source index. */
public final class ShiftNestSearchChoices {
    private ShiftNestSearchChoices() {}
    public static void show(Context context,String title,List<String> source,int selected,IntConsumer choose) {
        show(context,title,source,selected,choose,false);
    }
    public static void show(Context context,String title,List<String> source,int selected,IntConsumer choose,boolean translateChoices) {
        List<String> names=new ArrayList<>(source);
        LinearLayout body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(16*context.getResources().getDisplayMetrics().density);body.setPadding(padding,padding,padding,padding);
        EditText search=new androidx.appcompat.widget.AppCompatEditText(context);search.setHint("Search by name...");search.setSingleLine(true);
        search.setTextColor(ContextCompat.getColor(context,R.color.shiftnest_text));search.setHintTextColor(ContextCompat.getColor(context,R.color.shiftnest_hint));
        if(context instanceof ShiftNestBaseActivity)((ShiftNestBaseActivity)context).styleEditable(search);
        body.addView(search,new LinearLayout.LayoutParams(-1,-2));
        TextView count=new TextView(context);count.setTextColor(ContextCompat.getColor(context,R.color.shiftnest_text));body.addView(count);
        ListView results=new ListView(context);
        int height=Math.min((int)(360*context.getResources().getDisplayMetrics().density),(int)(context.getResources().getDisplayMetrics().heightPixels*0.45f));
        body.addView(results,new LinearLayout.LayoutParams(-1,height));
        List<Integer> indices=new ArrayList<>();List<String> labels=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<>(context,R.layout.shiftnest_spinner_dropdown,labels);results.setAdapter(adapter);
        Runnable filter=()->{
            String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            indices.clear();labels.clear();
            for(int i=0;i<names.size();i++)if(names.get(i).toLowerCase(Locale.ROOT).contains(query)||(translateChoices&&ShiftNestLanguage.text(context,names.get(i)).toLowerCase(Locale.ROOT).contains(query))){indices.add(i);labels.add((i==selected?"✓  ":"")+(translateChoices?ShiftNestLanguage.text(context,names.get(i)):names.get(i)));}
            adapter.notifyDataSetChanged();count.setText(ShiftNestLanguage.text(context,indices.isEmpty()?"No matching locations/options. Try another name.":indices.size()+" of "+names.size()+" matches • Tap to select"));
        };
        AlertDialog dialog=ShiftNestLanguage.dialog(context).setTitle(title).setView(body).setNegativeButton("Cancel",null).create();
        results.setOnItemClickListener((parent,view,position,id)->{if(position>=0&&position<indices.size()){int original=indices.get(position);dialog.dismiss();choose.accept(original);}});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        filter.run();dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
}

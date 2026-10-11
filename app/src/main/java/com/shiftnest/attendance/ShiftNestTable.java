package com.shiftnest.attendance;
import android.content.Context;
import android.view.View;
import android.widget.*;
import androidx.core.content.ContextCompat;
import java.util.List;
public final class ShiftNestTable {
    private ShiftNestTable(){}
    public static View create(Context c,String[] headers,List<String[]> rows,java.util.function.IntConsumer click){
        List<String[]> snapshot=new java.util.ArrayList<>();
        for(String[] row:rows)snapshot.add(row.clone());
        LinearLayout panel=new LinearLayout(c);panel.setOrientation(LinearLayout.VERTICAL);
        TextView count=new TextView(c);count.setTextColor(ContextCompat.getColor(c,R.color.shiftnest_text));panel.addView(count);
        HorizontalScrollView scroll=new HorizontalScrollView(c);TableLayout table=new TableLayout(c);scroll.addView(table);panel.addView(scroll);
        LinearLayout navigation=new LinearLayout(c);
        Button previous=navButton(c,"Previous"),next=navButton(c,"Next");
        navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(next,new LinearLayout.LayoutParams(0,-2,1));panel.addView(navigation);
        int[] page={0};final int pageSize=50;
        Runnable draw=()->{
            table.removeAllViews();add(c,table,headers,headers,true,null);
            int first=page[0]*pageSize,last=Math.min(snapshot.size(),first+pageSize);
            for(int i=first;i<last;i++){final int index=i;add(c,table,snapshot.get(i),headers,false,click==null?null:()->click.accept(index));}
            count.setText(ShiftNestLanguage.text(c,snapshot.isEmpty()?"No matching records.":"Showing "+(first+1)+"–"+last+" of "+snapshot.size()+" • Swipe sideways for all columns"));
            previous.setEnabled(page[0]>0);next.setEnabled(last<snapshot.size());
            navigation.setVisibility(snapshot.size()>pageSize?View.VISIBLE:View.GONE);
        };
        previous.setOnClickListener(v->{if(page[0]>0){page[0]--;draw.run();}});
        next.setOnClickListener(v->{if((page[0]+1)*pageSize<snapshot.size()){page[0]++;draw.run();}});
        draw.run();return panel;
    }
    private static Button navButton(Context c,String label){
        Button button=new androidx.appcompat.widget.AppCompatButton(c);button.setText(label);
        button.setBackgroundResource(R.drawable.shiftnest_primary);button.setTextColor(ContextCompat.getColor(c,R.color.shiftnest_on_primary));
        button.setMinHeight((int)(48*c.getResources().getDisplayMetrics().density));return button;
    }
    private static String cell(Context c,String header,String value){
        if(value==null)return "";
        if(header.equals("Department")||header.equals("Status")||header.equals("Dept / Leave date")||header.equals("Applied / Status"))return ShiftNestLanguage.text(c,value);
        return value;
    }
    private static void add(Context c,TableLayout table,String[] values,String[] headers,boolean header,Runnable action){
        TableRow row=new TableRow(c);boolean outside=false;for(String v:values)if(v!=null&&(v.contains("OUTSIDE LOCATION")||v.contains("BLOCKED: FAKE GPS")))outside=true;
        for(int column=0;column<values.length;column++){String value=values[column];TextView t=new TextView(c);t.setText(header?ShiftNestLanguage.text(c,value):cell(c,headers[column],value));t.setTag("shiftnest_data");t.setTextSize(header?14:13);t.setMinWidth((int)(145*c.getResources().getDisplayMetrics().density));t.setMaxWidth((int)(205*c.getResources().getDisplayMetrics().density));t.setPadding(18,18,18,18);t.setTextColor(outside?0xFFF05454:ContextCompat.getColor(c,header?R.color.shiftnest_heading:R.color.shiftnest_text));if(header)t.setTypeface(null,android.graphics.Typeface.BOLD);row.addView(t);}
        if(action!=null){row.setOnClickListener(v->action.run());row.setBackgroundResource(android.R.drawable.list_selector_background);}table.addView(row);
        View line=new View(c);line.setBackgroundColor(ContextCompat.getColor(c,R.color.shiftnest_border));table.addView(line,new TableLayout.LayoutParams(-1,1));
    }
}

package com.shiftnest.attendance;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.SpinnerAdapter;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;

/** Choice dialog shared by every visible department, status, role and site selector. */
public class ShiftNestChoiceSpinner extends AppCompatSpinner {
    public ShiftNestChoiceSpinner(Context context) { super(context, android.widget.Spinner.MODE_DROPDOWN); }
    public ShiftNestChoiceSpinner(Context context, AttributeSet attrs) { super(context, attrs); }
    public ShiftNestChoiceSpinner(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }
    private Runnable choiceChanged;
    public void setOnChoiceChangedListener(Runnable listener){choiceChanged=listener;}
    @Override public boolean performClick() {
        if (!isEnabled()) return false;
        SpinnerAdapter source=getAdapter();
        if(source==null || source.getCount()==0) {
            android.widget.Toast.makeText(getContext(),ShiftNestLanguage.text(getContext(),"No choices loaded. Refresh this page."),android.widget.Toast.LENGTH_LONG).show();
            return true;
        }
        java.util.List<String> names=new java.util.ArrayList<>();
        for(int i=0;i<source.getCount();i++)names.add(String.valueOf(source.getItem(i)));
        ShiftNestSearchChoices.show(getContext(),getPrompt()==null?"Select an option":getPrompt().toString(),names,getSelectedItemPosition(),index->{setSelection(index);if(choiceChanged!=null)choiceChanged.run();},names.get(0).equals("All statuses")||names.toString().contains("Sales Officers"));
        return true;
    }
}

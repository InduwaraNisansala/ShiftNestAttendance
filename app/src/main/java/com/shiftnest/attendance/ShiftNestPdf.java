package com.shiftnest.attendance;
import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.util.*;
public final class ShiftNestPdf {
    private ShiftNestPdf(){}
    public static File create(Context context,String title,String[] headers,List<String[]> rows) throws IOException {
        PdfDocument doc=new PdfDocument();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(9);p.setColor(Color.rgb(24,43,73));float col=770f/headers.length;int pageNo=1;
        PdfDocument.Page page=start(doc,context,title,headers,col,pageNo,p);float y=110;
        try {
            for(String[] row:rows){List<List<String>> cells=new ArrayList<>();int lines=1;for(String cell:row){List<String>w=wrap(cell==null?"":cell,col-10,p);cells.add(w);lines=Math.max(lines,w.size());}float height=lines*13+12;
                int offset=0;
                while(offset<lines){
                    int fit=(int)((540-y-12)/13);
                    if(fit<1){doc.finishPage(page);page=start(doc,context,title,headers,col,++pageNo,p);y=110;fit=(int)((540-y-12)/13);}
                    int chunk=Math.min(fit,lines-offset);
                    for(int i=0;i<cells.size();i++){
                        float lineY=y+12;
                        for(int line=offset;line<Math.min(offset+chunk,cells.get(i).size());line++){
                            page.getCanvas().drawText(cells.get(i).get(line),36+i*col,lineY,p);lineY+=13;
                        }
                    }
                    y+=chunk*13+12;offset+=chunk;
                    p.setColor(Color.LTGRAY);page.getCanvas().drawLine(30,y,812,y,p);p.setColor(Color.rgb(24,43,73));
                    if(offset<lines){doc.finishPage(page);page=start(doc,context,title,headers,col,++pageNo,p);y=110;}
                }
            }
            doc.finishPage(page);File f=new File(context.getCacheDir(),"shiftnest-report-"+System.nanoTime()+".pdf");try(FileOutputStream out=new FileOutputStream(f)){doc.writeTo(out);}return f;
        }finally{doc.close();}
    }
    private static PdfDocument.Page start(PdfDocument doc,Context c,String title,String[] headers,float col,int number,Paint p){
        PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(842,595,number).create());Canvas canvas=page.getCanvas();
        Bitmap logo=BitmapFactory.decodeResource(c.getResources(),R.drawable.shiftnest_logo);Paint faded=new Paint();faded.setAlpha(16);canvas.drawBitmap(logo,null,new RectF(200,200,642,380),faded);
        p.setTextSize(18);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("SHIFTNEST",30,30,p);p.setTextSize(12);canvas.drawText(title,30,50,p);p.setTextSize(9);p.setTypeface(Typeface.DEFAULT);canvas.drawText("Generated "+ShiftNestData.format(new Date(),"yyyy-MM-dd HH:mm")+" | Page "+number,30,68,p);
        for(int i=0;i<headers.length;i++)canvas.drawText(headers[i],36+i*col,94,p);canvas.drawLine(30,102,812,102,p);return page;
    }
    private static List<String> wrap(String value,float width,Paint p){List<String>out=new ArrayList<>();for(String paragraph:value.split("\n",-1)){String left=paragraph;while(!left.isEmpty()){int count=p.breakText(left,true,width,null);if(count<1)count=1;out.add(left.substring(0,count));left=left.substring(count);}if(paragraph.isEmpty())out.add("");}return out;}
}

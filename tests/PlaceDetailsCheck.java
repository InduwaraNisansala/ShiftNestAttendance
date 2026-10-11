import java.util.*;
public class PlaceDetailsCheck {
 Map<String,AttendanceData> attendanceMap=new HashMap<>();String todayDate="2026-10-10";int currentPageNumber=3;Container roleContainer=new Container();
 static class AttendanceData {String employeeId="",employeeName="",employeeRole="",checkIn="",checkOut="",checkInSiteName="",checkInLocationName="",checkOutSiteName="",checkOutLocationName="";}
 static class Container {void addView(Object o){}}
 Object text(String s,int size){return s;}
 static class ReportTable {static List<String[]> rows;static Object create(Object c,String[]h,List<String[]>r,Object click){rows=r;return r;}}
 static class Typeface {static int DEFAULT_BOLD=1,DEFAULT=0;}
 static class Paint {static int ANTI_ALIAS_FLAG=1;Paint(int f){}void setTextSize(int n){}void setTypeface(int f){}int breakText(String s,boolean f,int w,Object o){return Math.min(60,s.length());}}
 static class Canvas {List<String> lines=new ArrayList<>();void drawText(String s,int x,int y,Paint p){lines.add(s);}}
 static class PdfDocument {List<Page> pages=new ArrayList<>();static class PageInfo {int n;PageInfo(int n){this.n=n;}static class Builder {int n;Builder(int w,int h,int n){this.n=n;}PageInfo create(){return new PageInfo(n);}}}static class Page {PageInfo info;Canvas canvas=new Canvas();boolean closed;Page(PageInfo i){info=i;}Canvas getCanvas(){return canvas;}}Page startPage(PageInfo i){Page p=new Page(i);pages.add(p);return p;}void finishPage(Page p){if(p.closed)throw new AssertionError("double close");p.closed=true;}}
    private String placeLabel(String value){return value==null||value.trim().isEmpty()?"--":value;}
    private List<AttendanceData> sortedPlaceDetails(){
        List<AttendanceData> list=new ArrayList<>(attendanceMap.values());
        list.sort(java.util.Comparator.comparing((AttendanceData a)->a.employeeName).thenComparing(a->a.employeeId));return list;
    }
    private void buildPlaceDetails(){
        roleContainer.addView(text("Check-in / Check-out site details • "+todayDate,20));
        List<String[]> rows=new ArrayList<>();
        for(AttendanceData a:sortedPlaceDetails())rows.add(new String[]{a.employeeName,placeLabel(a.checkInSiteName),placeLabel(a.checkInLocationName),placeLabel(a.checkOutSiteName),placeLabel(a.checkOutLocationName)});
        if(rows.isEmpty())roleContainer.addView(text("No saved attendance site details for this date.",16));
        else roleContainer.addView(ReportTable.create(this,new String[]{"Employee","In site","In location","Out site","Out location"},rows,null));
        roleContainer.addView(text("These are typed attendance labels. Older records may show --. The charts above count attendance status.",14));
    }
    private void drawPlaceDetailsPages(PdfDocument document){
        if(attendanceMap.isEmpty())return;
        List<String> lines=new ArrayList<>();Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(10);
        for(AttendanceData a:sortedPlaceDetails()){
            String block=a.employeeName+" • "+a.employeeRole+"\nCheck-in "+placeLabel(a.checkIn)+" | Site: "+placeLabel(a.checkInSiteName)+" | Location: "+placeLabel(a.checkInLocationName)+"\nCheck-out "+placeLabel(a.checkOut)+" | Site: "+placeLabel(a.checkOutSiteName)+" | Location: "+placeLabel(a.checkOutLocationName)+"\n";
            for(String paragraph:block.split("\n",-1)){if(paragraph.isEmpty()){lines.add("");continue;}String rest=paragraph;while(!rest.isEmpty()){int count=Math.max(1,p.breakText(rest,true,515,null));lines.add(rest.substring(0,count));rest=rest.substring(count);}}
        }
        int offset=0;while(offset<lines.size()){
            PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,++currentPageNumber).create());Canvas canvas=page.getCanvas();p.setTextSize(14);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("Attendance site details • "+todayDate,40,40,p);p.setTextSize(9);p.setTypeface(Typeface.DEFAULT);canvas.drawText("Typed check-in/out labels; -- means no value was recorded.",40,60,p);p.setTextSize(10);int y=88;
            while(offset<lines.size()&&y<790){canvas.drawText(lines.get(offset++),40,y,p);y+=15;}document.finishPage(page);
        }
    }
 public static void main(String[]args){
  PlaceDetailsCheck c=new PlaceDetailsCheck();PdfDocument empty=new PdfDocument();c.drawPlaceDetailsPages(empty);if(!empty.pages.isEmpty())throw new AssertionError("empty pages");
  AttendanceData a=new AttendanceData();a.employeeId="a";a.employeeName="Alice";a.employeeRole="Staff Officer";a.checkIn="08:00:00";a.checkOut="17:00:00";a.checkInSiteName="Urban Lux";a.checkInLocationName="Kandana";a.checkOutSiteName="Branch";a.checkOutLocationName="Colombo";c.attendanceMap.put("a",a);
  AttendanceData legacy=new AttendanceData();legacy.employeeId="b";legacy.employeeName="Bob";c.attendanceMap.put("b",legacy);c.buildPlaceDetails();
  if(ReportTable.rows.size()!=2||!ReportTable.rows.get(0)[1].equals("Urban Lux")||!ReportTable.rows.get(0)[2].equals("Kandana")||!ReportTable.rows.get(0)[3].equals("Branch")||!ReportTable.rows.get(0)[4].equals("Colombo")||!ReportTable.rows.get(1)[1].equals("--"))throw new AssertionError("UI labels / legacy missing");
  for(int i=0;i<120;i++){AttendanceData d=new AttendanceData();d.employeeId="id"+i;d.employeeName="Name"+i;d.checkInSiteName="S".repeat(120);d.checkInLocationName="L".repeat(120);c.attendanceMap.put(d.employeeId,d);}
  PdfDocument pdf=new PdfDocument();c.drawPlaceDetailsPages(pdf);if(pdf.pages.size()<2)throw new AssertionError("overflow not paginated");StringBuilder all=new StringBuilder();int num=4;
  for(PdfDocument.Page p:pdf.pages){if(!p.closed||p.info.n!=num++)throw new AssertionError("page lifecycle / numbering");for(String line:p.canvas.lines)all.append(line);}
  for(String v:new String[]{"Urban Lux","Kandana","Branch","Colombo","Name119","S".repeat(120),"L".repeat(120)})if(!all.toString().contains(v))throw new AssertionError("PDF loses "+v);
  System.out.println("PASS: actual daily site-detail UI/PDF methods: check-in/out labels, older records, 122 employees, full long values and pagination.");
 }
}

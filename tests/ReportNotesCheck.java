import java.util.*;import java.time.*;
public class ReportNotesCheck {
 List<DocumentSnapshot> records=new ArrayList<>(),notices=new ArrayList<>();List<String[]>rows=new ArrayList<>();String[]headers={"Date","Employee","Department","In","Out","Status","Late message"};
 LocalDate from=LocalDate.parse("2026-10-01"),to=LocalDate.parse("2026-10-09");boolean reportLoading;
 Table table=new Table();Filter filter=new Filter();Text summary=new Text();Button pdf=new Button();String rangeError(){return "";}
 static class DocumentSnapshot {Map<String,String> values;DocumentSnapshot(String...pairs){values=new HashMap<>();for(int i=0;i<pairs.length;i+=2)values.put(pairs[i],pairs[i+1]);}}
 static class ReportData {static String string(DocumentSnapshot d,String k){return d.values.getOrDefault(k,"");}static String department(String role){return role.contains("Sales")?"Sales":role.contains("Hotel")?"Hotel":"Office";}static String date(){return "2026-10-09";}}
 static class LeaveMath {static boolean completedDay(String d,String in,String out,String s,String today){return !in.isEmpty()&&!out.isEmpty()&&s.equals("Completed");}}
 static class Table {void removeAllViews(){}void addView(Object o){}}
 static class Filter {String selection="All Employees";Object getSelectedItem(){return selection;}}
 static class Text {String value;void setText(String s){value=s;}}
 static class Button {boolean enabled;void setEnabled(boolean e){enabled=e;}}
 static class ReportTable {static Object create(Object c,String[]h,List<String[]>r,Object click){return null;}}
    private void render(){
        if(table==null||filter==null||reportLoading)return;if(!rangeError().isEmpty()){summary.setText(rangeError());pdf.setEnabled(false);return;}
        rows.clear();String selected=String.valueOf(filter.getSelectedItem());int completed=0;
        Map<String,DocumentSnapshot> notes=new HashMap<>();for(DocumentSnapshot d:notices)notes.put(ReportData.string(d,"employeeId")+"_"+ReportData.string(d,"date"),d);
        Set<String> seen=new HashSet<>();
        for(DocumentSnapshot d:records){String dep=ReportData.department(ReportData.string(d,"employeeRole"));String key=ReportData.string(d,"employeeId")+"_"+ReportData.string(d,"date");seen.add(key);if(!"All Employees".equals(selected)&&!dep.equals(selected))continue;String out=ReportData.string(d,"checkOut");if(LeaveMath.completedDay(ReportData.string(d,"date"),ReportData.string(d,"checkIn"),out,ReportData.string(d,"workStatus"),ReportData.date()))completed++;DocumentSnapshot note=notes.get(key);rows.add(new String[]{ReportData.string(d,"date"),ReportData.string(d,"employeeName"),dep,ReportData.string(d,"checkIn"),out.isEmpty()?"--":out,ReportData.string(d,"status")+" / "+ReportData.string(d,"locationName")+" | In site: "+ReportData.string(d,"checkInSiteName")+" / "+ReportData.string(d,"checkInLocationName")+" | Out site: "+ReportData.string(d,"checkOutSiteName")+" / "+ReportData.string(d,"checkOutLocationName"),note==null?"":ReportData.string(note,"lateMessage")});}
        // Keep an explanation visible even when the employee has not checked in yet.
        for(Map.Entry<String,DocumentSnapshot> entry:notes.entrySet()){if(seen.contains(entry.getKey()))continue;DocumentSnapshot d=entry.getValue();String dep=ReportData.department(ReportData.string(d,"employeeRole"));if(!"All Employees".equals(selected)&&!dep.equals(selected))continue;rows.add(new String[]{ReportData.string(d,"date"),ReportData.string(d,"employeeName"),dep,"--","--","No check-in recorded",ReportData.string(d,"lateMessage")});}
        rows.sort(Comparator.comparing((String[] row)->row[0]).reversed().thenComparing(row->row[1]));summary.setText(from+" to "+to+"\nReport rows: "+rows.size()+" • Completed: "+completed+"\nLate messages are employee explanations; they do not mark attendance or grant approval. Date filters do not delete older records.");table.removeAllViews();table.addView(ReportTable.create(this,headers,rows,null));pdf.setEnabled(!rows.isEmpty());
    }
 public static void main(String[]args){
  ReportNotesCheck a=new ReportNotesCheck();String full="Delay reason "+"x".repeat(488);
  a.notices.add(new DocumentSnapshot("employeeId","a","employeeName","Alice","employeeRole","Sales Officer","date","2026-10-09","lateMessage",full));a.render();
  if(a.rows.size()!=1||!a.rows.get(0)[5].contains("No check-in")||!a.rows.get(0)[6].equals(full))throw new AssertionError("pre-check-in full message missing");
  a.records.add(new DocumentSnapshot("employeeId","a","employeeName","Alice","employeeRole","Sales Officer","date","2026-10-09","checkIn","09:00:00","checkOut","17:00:00","workStatus","Completed","checkInSiteName","Urban Lux","checkInLocationName","Kandana","checkOutSiteName","Branch","checkOutLocationName","Colombo"));a.render();
  if(!a.rows.get(0)[5].contains("Urban Lux / Kandana")||!a.rows.get(0)[5].contains("Branch / Colombo"))throw new AssertionError("typed site and location lost");
  if(a.rows.size()!=1||!a.rows.get(0)[3].equals("09:00:00")||!a.rows.get(0)[6].equals(full))throw new AssertionError("message lost after check-in or duplicated row");
  a.notices.add(new DocumentSnapshot("employeeId","b","employeeName","Bob","employeeRole","Staff Officer","date","2026-10-09","lateMessage","Traffic"));a.render();if(a.rows.size()!=2)throw new AssertionError("multiple employees");
  a.filter.selection="Sales";a.render();if(a.rows.size()!=1||!a.rows.get(0)[1].equals("Alice"))throw new AssertionError("department filtering");
  a.records.clear();a.notices.clear();a.render();if(a.pdf.enabled)throw new AssertionError("empty export");
  System.out.println("PASS: extracted report merge: late notice before check-in, full message after check-in, no duplicates, departments and empty exports.");
 }
}
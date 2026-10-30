package it.andreademartini.trading.backtest.reporting;
import org.apache.poi.ss.usermodel.*;

/** Same DTO as the API. Existing sheets are preserved. */
public final class PortfolioSummaryExcel {
 private PortfolioSummaryExcel(){}
 public static void write(Workbook book,Object run){
  var dto=PortfolioSummaryMapper.from(run);var sheet=book.createSheet("PORTFOLIO_SUMMARY");sheet.createFreezePane(0,1);
  var header=sheet.createRow(0);header.createCell(0).setCellValue("Metric");header.createCell(1).setCellValue("Value");header.createCell(2).setCellValue("Availability / convention");
  var bold=book.createCellStyle();var font=book.createFont();font.setBold(true);bold.setFont(font);header.forEach(c->c.setCellStyle(bold));
  var decimal=book.createCellStyle();decimal.setDataFormat(book.createDataFormat().getFormat("#,##0.000"));
  var money=book.createCellStyle();money.setDataFormat(book.createDataFormat().getFormat("#,##0.00"));
  var percent=book.createCellStyle();percent.setDataFormat(book.createDataFormat().getFormat("0.000\"%\""));
  int i=1;for(var m:dto.metrics()){var row=sheet.createRow(i++);row.createCell(0).setCellValue(m.label());var cell=row.createCell(1);
   if(m.value()==null)cell.setCellValue("N/A");else{cell.setCellValue(m.value());switch(m.format()){case "money"->cell.setCellStyle(money);case "percent"->cell.setCellStyle(percent);case "ratio"->cell.setCellStyle(decimal);default->{}}}if(m.reason()!=null)row.createCell(2).setCellValue(m.reason());
  }
  for(var e:dto.context().entrySet()){var row=sheet.createRow(i++);row.createCell(0).setCellValue(e.getKey());var cell=row.createCell(1);Object v=e.getValue();if(v instanceof Number n)cell.setCellValue(n.doubleValue());else if(v instanceof Boolean b)cell.setCellValue(b);else{String text=v==null?"N/A":v.toString();cell.setCellValue(text.substring(0,Math.min(32767,text.length())));}}
  sheet.setColumnWidth(0,11000);sheet.setColumnWidth(1,8500);sheet.setColumnWidth(2,16000);
 }
}

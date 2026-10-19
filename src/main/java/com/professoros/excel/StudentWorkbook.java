package com.professoros.excel;
import java.util.*;
/** Detached values only. Formula/error cells retain an error instead of a cached result. */
public record StudentWorkbook(List<Sheet> sheets) {
    public StudentWorkbook { sheets=List.copyOf(sheets); }
    public record Value(String text,String error,boolean numeric) { }
    public record Row(int number,List<Value> cells) {
        public Row { cells=List.copyOf(cells); }
        public Value cell(int index) { return index<0 || index>=cells.size()?new Value("","",false):cells.get(index); }
    }
    public record Sheet(String name,List<Row> rows) {
        public Sheet { rows=List.copyOf(rows); }
        @Override public String toString() { return name; }
    }
}

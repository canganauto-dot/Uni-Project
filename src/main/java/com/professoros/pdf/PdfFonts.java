package com.professoros.pdf;

import com.professoros.model.DomainValidationException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import com.ibm.icu.text.*;
import java.io.*;
import java.util.*;

final class PdfFonts {
    enum Style { BODY, HEADING, CODE }
    record Line(String visual,boolean rtl) { }
    private final PDType0Font regular,bold,mono,arabic;
    PdfFonts(PDDocument document) throws IOException {
        regular=load(document,"NotoSans-Regular.ttf"); bold=load(document,"NotoSans-Bold.ttf");
        mono=load(document,"NotoSansMono-Regular.ttf"); arabic=load(document,"NotoNaskhArabic-Regular.ttf");
    }
    private static PDType0Font load(PDDocument document,String name) throws IOException {
        try(InputStream stream=PdfFonts.class.getResourceAsStream("/fonts/"+name)) {
            if(stream==null) { throw new IOException("Bundled font missing: "+name); }
            return PDType0Font.load(document,stream,true);
        }
    }
    PDType0Font font(int cp,Style style) throws IOException {
        PDType0Font preferred=style==Style.CODE?mono:style==Style.HEADING?bold:regular;
        if(canEncode(preferred,cp)) { return preferred; }
        if(canEncode(arabic,cp)) { return arabic; }
        throw new DomainValidationException(String.format("PDF font does not support character U+%04X. Replace it or choose supported text before exporting.",cp));
    }
    private static boolean canEncode(PDType0Font font,int cp) throws IOException {
        // hasGlyph accepts a PDF character code, not a Unicode code point.
        try { font.encode(new String(Character.toChars(cp))); return true; }
        catch(IllegalArgumentException unsupported) { return false; }
    }
    Line visual(String logical,boolean forceLtr) {
        try {
            String shaped=new ArabicShaping(ArabicShaping.LETTERS_SHAPE).shape(logical);
            Bidi bidi=new Bidi();
            bidi.setPara(shaped,forceLtr?Bidi.LTR:Bidi.LEVEL_DEFAULT_LTR,null);
            return new Line(bidi.writeReordered(Bidi.DO_MIRRORING | Bidi.REMOVE_BIDI_CONTROLS),!forceLtr && !bidi.baseIsLeftToRight());
        } catch(ArabicShapingException failure) {
            throw new DomainValidationException("Arabic text could not be shaped for PDF. Review the text before exporting.");
        }
    }
    float width(String visual,Style style,float size) throws IOException {
        float result=0;
        for(int cp:visual.codePoints().toArray()) { result+=font(cp,style).getStringWidth(new String(Character.toChars(cp)))*size/1000f; }
        return result;
    }
    List<Line> wrap(String text,Style style,float size,float maxWidth) throws IOException {
        List<Line> result=new ArrayList<>();
        // Preserve explicit line breaks and code indentation; expand tabs predictably.
        for(String paragraph:text.replace("\r\n","\n").replace('\r','\n').replace("\t","    ").split("\n",-1)) {
            if(paragraph.isEmpty()) { result.add(new Line("",false)); continue; }
            int start=0;
            while(start<paragraph.length()) {
                BreakIterator boundaries=BreakIterator.getCharacterInstance(Locale.ROOT);
                boundaries.setText(paragraph);
                int end=start,lastSpace=-1,best=start;
                while((end=boundaries.following(end))!=BreakIterator.DONE) {
                    String candidate=paragraph.substring(start,end);
                    if(width(visual(candidate,style==Style.CODE).visual(),style,size)>maxWidth) { break; }
                    best=end;
                    if(Character.isWhitespace(paragraph.codePointBefore(end))) { lastSpace=end; }
                    if(end==paragraph.length()) { break; }
                }
                if(best==start) { throw new DomainValidationException("A text glyph is too wide for the PDF layout."); }
                int split=best<paragraph.length() && lastSpace>start?lastSpace:best;
                result.add(visual(paragraph.substring(start,split),style==Style.CODE));
                start=split;
            }
        }
        return result;
    }
}

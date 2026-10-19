package com.professoros.service;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
class ThemeContrastTest {
    @Test void bothPalettesMeetNormalTextContrastForSemanticPairs() throws Exception {
        String css;
        try(var in=getClass().getResourceAsStream("/css/palette.css")) { css=new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); }
        for(String block:css.split("}")) {
            var values=new HashMap<String,String>(); var match=Pattern.compile("(-app-[a-z0-9-]+):\\s*(#[a-fA-F0-9]{6})").matcher(block);
            while(match.find()) { values.put(match.group(1),match.group(2)); }
            if(values.isEmpty()) { continue; }
            for(String[] pair:new String[][]{{"text","surface"},{"muted","surface"},{"muted","background"},{"on-accent","accent"},{"selection-text","selection"},{"danger","danger-soft"},{"sidebar-text","sidebar"}}) {
                double a=luminance(values.get("-app-"+pair[0])),b=luminance(values.get("-app-"+pair[1]));
                assertTrue((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5,Arrays.toString(pair));
            }
        }
    }
    private static double luminance(String hex) {
        double[] rgb=new double[3]; for(int i=0;i<3;i++) { double c=Integer.parseInt(hex.substring(1+i*2,3+i*2),16)/255.0; rgb[i]=c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4); }
        return .2126*rgb[0]+.7152*rgb[1]+.0722*rgb[2];
    }
}

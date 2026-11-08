package io.github.whywood.deepdeep.ui.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.whywood.deepdeep.design.DeepDeepColors

internal object RemoteInjection {

    fun documentStartScript(colors: DeepDeepColors): String {
        val literal = quote(css(colors))
        return """
            (function(){try{
              var css=$literal;
              function apply(){
                var root=document.documentElement;
                if(!root)return;
                root.classList.add('dd-remote');
                if(document.getElementById('dd-remote-style'))return;
                var style=document.createElement('style');
                style.id='dd-remote-style';
                style.textContent=css;
                (document.head||root).appendChild(style);
              }
              if(document.documentElement){apply();}
              else{document.addEventListener('DOMContentLoaded',apply,{once:true});}
            }catch(e){}})();
        """.trimIndent()
    }

    private fun css(colors: DeepDeepColors): String = buildString {
        append("html.dd-remote{")
        append("-webkit-tap-highlight-color:transparent;")
        append("-webkit-text-size-adjust:100%;")
        append("color-scheme:").append(if (colors.dark) "dark" else "light").append(';')
        append("--dd-paper:").append(hex(colors.paper)).append(';')
        append("--dd-surface:").append(hex(colors.surface)).append(';')
        append("--dd-raised:").append(hex(colors.raised)).append(';')
        append("--dd-ink:").append(hex(colors.ink)).append(';')
        append("--dd-ink-secondary:").append(hex(colors.inkSecondary)).append(';')
        append("--dd-ink-tertiary:").append(hex(colors.inkTertiary)).append(';')
        append("--dd-accent:").append(hex(colors.accent)).append(';')
        append("--dd-on-accent:").append(hex(colors.onAccent)).append(';')
        append("--dd-accent-soft:").append(hex(colors.accentSoft)).append(';')
        append("--dd-danger:").append(hex(colors.danger)).append(';')
        append("--dd-hairline:").append(hex(colors.hairline)).append(';')
        append("--dd-wash:").append(hex(colors.wash)).append(';')
        append("}")
        append("html.dd-remote body{background:").append(hex(colors.paper)).append(";")
        append("overscroll-behavior-y:contain;}")
        append("html.dd-remote ::-webkit-scrollbar{display:none;width:0;height:0;}")
        append("html.dd-remote *{scrollbar-width:none;}")
        append("html.dd-remote textarea,html.dd-remote input{font-size:16px;}")
        append("html.dd-remote img,html.dd-remote video{max-width:100%;}")
        append("html.dd-remote [contenteditable]{-webkit-user-select:text;}")
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun hex(color: Color): String = "#%06X".format(0xFFFFFF and color.toArgb())
}

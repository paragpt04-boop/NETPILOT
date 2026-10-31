package org.jsus.netpilot

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*

internal val bg = Color(0xFF0C1218)
internal val surface = Color(0xFF151E26)
internal val panel2 = Color(0xFF1A2630)
internal val line = Color(0xFF2A3942)
internal val text = Color(0xFFE8F0ED)
internal val muted = Color(0xFF92A4AB)
internal val accent = Color(0xFF80D5AD)
internal val amber = Color(0xFFEFC082)
internal val danger = Color(0xFFEE858C)
internal val mono = FontFamily.Monospace
internal val rounded = RoundedCornerShape(14.dp)

@Composable internal fun T(value:String,size:Int=13,color:Color=text,weight:FontWeight=FontWeight.Normal,monoText:Boolean=false,modifier:Modifier=Modifier,maxLines:Int=Int.MAX_VALUE){
    Text(value,modifier=modifier,fontSize=size.sp,color=color,fontWeight=weight,lineHeight=(size+5).sp,
        fontFamily=if(monoText)mono else FontFamily.Default,overflow=TextOverflow.Ellipsis,maxLines=maxLines)
}
@Composable internal fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit){
    Column(modifier.fillMaxWidth().border(1.dp,line,rounded).background(surface,rounded).padding(15.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}
@Composable internal fun DividerLine(){ Spacer(Modifier.fillMaxWidth().height(1.dp).background(line)) }
@Composable internal fun PageHeading(title:String,subtitle:String,action:(@Composable ()->Unit)?=null){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            T(title,23,text,FontWeight.SemiBold);T(subtitle,12,muted)
        }
        action?.invoke()
    }
}
@Composable internal fun Pill(label:String,good:Boolean=true){
    Box(Modifier.background(if(good)accent.copy(alpha=.12f) else amber.copy(alpha=.12f),RoundedCornerShape(8.dp))
      .border(1.dp,if(good)accent.copy(alpha=.26f) else amber.copy(alpha=.28f),RoundedCornerShape(8.dp))
      .padding(horizontal=9.dp,vertical=5.dp)){
        T(label,10,if(good)accent else amber,FontWeight.Medium)
    }
}
@Composable internal fun SolidButton(label:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier){
    Button(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=42.dp),
      shape=RoundedCornerShape(9.dp),contentPadding=PaddingValues(horizontal=15.dp,vertical=9.dp),
      colors=ButtonDefaults.buttonColors(containerColor=accent,contentColor=bg,disabledContainerColor=line,disabledContentColor=muted)){
        T(label,12,if(enabled)bg else muted,FontWeight.Bold)
    }
}
@Composable internal fun SoftButton(label:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier){
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=modifier.heightIn(min=42.dp),
      shape=RoundedCornerShape(9.dp),border=BorderStroke(1.dp,line),
      contentPadding=PaddingValues(horizontal=14.dp,vertical=9.dp)){
        T(label,12,if(enabled)text else muted,FontWeight.Medium)
    }
}
@Composable internal fun Entry(label:String,value:String,change:(String)->Unit,secret:Boolean=false,numeric:Boolean=false){
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        T(label.uppercase(),10,muted,FontWeight.SemiBold,monoText=true)
        BasicTextField(value=value,onValueChange=change,singleLine=true,
          modifier=Modifier.fillMaxWidth().border(1.dp,line,RoundedCornerShape(9.dp))
            .background(panel2,RoundedCornerShape(9.dp)).padding(horizontal=12.dp,vertical=12.dp),
          textStyle=TextStyle(color=text,fontSize=14.sp,fontFamily=mono),
          cursorBrush=SolidColor(accent),
          keyboardOptions=KeyboardOptions(keyboardType=if(secret)KeyboardType.Password else if(numeric)KeyboardType.Decimal else KeyboardType.Text),
          visualTransformation=if(secret)PasswordVisualTransformation() else VisualTransformation.None,
          decorationBox={inner->
              Box{if(value.isBlank())T("—",13,muted);inner()}
          })
    }
}
@Composable internal fun EmptyState(title:String,description:String,action:String?=null,onAction:()->Unit={}){
    Panel{
        T("◌",28,accent,FontWeight.Light,monoText=true)
        T(title,16,text,FontWeight.SemiBold);T(description,12,muted)
        if(action!=null)SolidButton(action,onAction)
    }
}
@Composable internal fun Metric(label:String,value:String,foot:String,modifier:Modifier=Modifier,valueColor:Color=text){
    Column(modifier.background(surface,rounded).border(1.dp,line,rounded).padding(13.dp),
      verticalArrangement=Arrangement.spacedBy(5.dp)){
        T(label.uppercase(),10,muted,FontWeight.Medium,monoText=true)
        T(value,22,valueColor,FontWeight.SemiBold,monoText=true,maxLines=1)
        T(foot,10,muted,maxLines=1)
    }
}

package com.era.assistant.core.ui
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
class SearchPulseView @JvmOverloads constructor(context: Context, attrs: AttributeSet?=null): View(context,attrs) {
 private val orbit=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=dp(.8f); color=0xB8D8DCE2.toInt() }
 private val point=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFFE9EDF2.toInt() }
 private val core=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF171A1F.toInt() }
 private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=dp(.8f); color=0xB08D949D.toInt() }
 private val radar=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeWidth=dp(.8f); color=0xFFDDE1E6.toInt() }
 private val rect=RectF()
 private var running=false
 private var start=0L
 fun startAnimation(){ if(!running){running=true;start=SystemClock.uptimeMillis();postInvalidateOnAnimation()} }
 fun stopAnimation(){if(running){running=false;invalidate()}}
 override fun onDraw(c:Canvas){
  val x=width*.5f; val y=height*.5f; val r=(height.coerceAtMost(width)*.5f).coerceAtLeast(dp(18f)); val e=(SystemClock.uptimeMillis()-start).toFloat(); val t=e/1000f
  orbit(c,x,y,r*.82f,r*.35f,-18f); orbit(c,x,y,r*.62f,r*.24f,56f)
  dot(c,x,y,r*.82f,r*.35f,t*1.35f,-18f,dp(1.8f)); dot(c,x,y,r*.62f,r*.24f,t*-1.8f+1.3f,56f,dp(1.55f)); dot(c,x,y,r*.72f,r*.29f,t*.72f+3.4f,-18f,dp(1.25f))
  val p=(e%1800f)/1800f; radar.alpha=((1-p)*72).toInt(); c.drawCircle(x,y,r*(.25f+p*.9f),radar); radar.alpha=255
  core.alpha=(190+35*sin(t*1.7f)).toInt(); c.drawCircle(x,y,r*.16f,core); core.alpha=255; c.drawCircle(x,y,r*.16f,edge)
  if(running)postInvalidateOnAnimation()
 }
 private fun orbit(c:Canvas,x:Float,y:Float,w:Float,h:Float,a:Float){c.save();c.rotate(a,x,y);rect.set(x-w,y-h,x+w,y+h);c.drawOval(rect,orbit);c.restore()}
 private fun dot(c:Canvas,x:Float,y:Float,w:Float,h:Float,a:Float,rot:Float,pr:Float){val q=a.toDouble();val lx=(cos(q)*w).toFloat();val ly=(sin(q)*h).toFloat();val z=Math.toRadians(rot.toDouble());c.drawCircle(x+(lx*cos(z)-ly*sin(z)).toFloat(),y+(lx*sin(z)+ly*cos(z)).toFloat(),pr,point)}
 override fun onDetachedFromWindow(){running=false;super.onDetachedFromWindow()}
 private fun dp(v:Float)=v*resources.displayMetrics.density
}

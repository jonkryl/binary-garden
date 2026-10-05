package com.jonkryl.binarygarden

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.jonkryl.binarygarden.ads.BannerController
import com.jonkryl.binarygarden.core.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val background = Executors.newSingleThreadExecutor()
    private lateinit var ad: BannerController
    private lateinit var page: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var mode: TextView
    private lateinit var undo: Button
    private lateinit var hint: Button
    private lateinit var check: Button
    private lateinit var normal: Button
    private lateinit var daily: Button
    private var game: GameState? = null
    private var loading = false
    private var highlighted = emptySet<Int>()
    private val saveFeedback = SaveFeedback()
    private var statusMessage = ""
    private var pendingModeSelection: Boolean? = null
    private val prefs by lazy { getSharedPreferences("garden",MODE_PRIVATE) }
    private val cellIds=intArrayOf(R.id.cell_0,R.id.cell_1,R.id.cell_2,R.id.cell_3,R.id.cell_4,R.id.cell_5,R.id.cell_6,R.id.cell_7,R.id.cell_8,R.id.cell_9,R.id.cell_10,R.id.cell_11,R.id.cell_12,R.id.cell_13,R.id.cell_14,R.id.cell_15,R.id.cell_16,R.id.cell_17,R.id.cell_18,R.id.cell_19,R.id.cell_20,R.id.cell_21,R.id.cell_22,R.id.cell_23,R.id.cell_24,R.id.cell_25,R.id.cell_26,R.id.cell_27,R.id.cell_28,R.id.cell_29,R.id.cell_30,R.id.cell_31,R.id.cell_32,R.id.cell_33,R.id.cell_34,R.id.cell_35,R.id.cell_36,R.id.cell_37,R.id.cell_38,R.id.cell_39,R.id.cell_40,R.id.cell_41,R.id.cell_42,R.id.cell_43,R.id.cell_44,R.id.cell_45,R.id.cell_46,R.id.cell_47,R.id.cell_48,R.id.cell_49,R.id.cell_50,R.id.cell_51,R.id.cell_52,R.id.cell_53,R.id.cell_54,R.id.cell_55,R.id.cell_56,R.id.cell_57,R.id.cell_58,R.id.cell_59,R.id.cell_60,R.id.cell_61,R.id.cell_62,R.id.cell_63)
    private val buttons = mutableListOf<Button>()
    private val green = Color.rgb(33,100,77)
    private val navy = Color.rgb(34,51,47)
    private val cream = Color.rgb(245,248,243)
    private val gold = Color.rgb(248,216,125)
    private fun dp(value:Int) = (value*resources.displayMetrics.density).toInt()
    private fun key(daily:Boolean) = if(daily) "game_daily" else "game_normal"
    private fun day():String = SimpleDateFormat("yyyy-MM-dd",Locale.US).apply {timeZone=TimeZone.getTimeZone("UTC")}.format(Date())

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cream;window.navigationBarColor=cream
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(cream)}
        root.setOnApplyWindowInsetsListener {v,i -> v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i}
        val scroll=ScrollView(this).apply {isFillViewport=true}
        page=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(16))}
        scroll.addView(page);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val title=label(getString(R.string.app_name),26f,true);title.id=R.id.title;page.addView(title)
        page.addView(label(getString(R.string.tagline),14f))
        mode=label("",16f,true).apply {id=R.id.mode};page.addView(mode)
        val top=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        normal=button(R.string.new_game,R.id.new_game) {chooseNew()};daily=button(R.string.daily,R.id.daily){switchMode(!(game?.daily?:false))}
        top.addView(normal,LinearLayout.LayoutParams(0,-2,1f));top.addView(daily,LinearLayout.LayoutParams(0,-2,1f));page.addView(top)
        progress=label("",14f).apply{id=R.id.progress};page.addView(progress)
        val boardScroll=HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=true}
        grid=GridLayout(this).apply {id=R.id.board;setPadding(0,dp(8),0,dp(8))}
        boardScroll.addView(grid);page.addView(boardScroll)
        status=label("",16f).apply {id=R.id.status;accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE;setPadding(0,dp(8),0,dp(8))};page.addView(status)
        showStatus(R.string.loading)
        val actions=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        undo=button(R.string.undo,R.id.undo) {game?.let {if(it.undo()){highlighted=emptySet();save();render()}}}
        hint=button(R.string.hint,R.id.hint){showHint()};check=button(R.string.check,R.id.check){checkBoard()}
        for(b in listOf(undo,hint,check))actions.addView(b,LinearLayout.LayoutParams(0,-2,1f));page.addView(actions)
        val links=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        links.addView(button(R.string.rules,R.id.rules){rules()},LinearLayout.LayoutParams(0,-2,1f))
        links.addView(button(R.string.more,R.id.more){more()},LinearLayout.LayoutParams(0,-2,1f));page.addView(links)
        page.addView(label(getString(R.string.local_save),12f))
        val adHost=FrameLayout(this).apply {id=R.id.ad_host;minimumHeight=dp(50);setPadding(dp(8),dp(8),dp(8),0)}
        root.addView(adHost,LinearLayout.LayoutParams(-1,-2));setContentView(root)
        ad=BannerController(this);ad.attach(adHost)
        switchMode(prefs.getBoolean("daily_selected",false))
    }

    private fun label(text:String,size:Float,bold:Boolean=false)=TextView(this).apply {
        this.text=text;textSize=size;setTextColor(navy);if(bold)typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    private fun button(text:Int,id:Int,action:()->Unit)=Button(this).apply {
        this.id=id;setText(text);isAllCaps=false;textSize=13f;minimumHeight=dp(48);minHeight=dp(48);minimumWidth=0;minWidth=0
        setPadding(dp(6),dp(4),dp(6),dp(4));setTextColor(green);setOnClickListener{if(!loading)action()}
    }
    private fun switchMode(isDaily:Boolean) {
        if(loading)return
        save(isDaily)
        val stored=prefs.getString(key(isDaily),null)?.let{GameState.decode(it)}
        if(stored!=null && (!isDaily || stored.day==day())) {game=stored;highlighted=emptySet();render();return}
        generate(if(isDaily)8 else 6,if(isDaily)day().replace("-","").toLong() else System.currentTimeMillis(),isDaily)
    }
    private fun chooseNew() {
        AlertDialog.Builder(this).setTitle(R.string.choose_size)
            .setItems(arrayOf(getString(R.string.size_6),getString(R.string.size_8))) {dialog,which ->
                afterMenu(dialog) {
                    fun start(){generate(if(which==0)6 else 8,System.currentTimeMillis(),false)}
                    val existing=prefs.getString(key(false),null)?.let{GameState.decode(it)}
                    if(existing!=null && !existing.won)AlertDialog.Builder(this).setTitle(R.string.new_game).setMessage(R.string.replace_game)
                        .setPositiveButton(R.string.start){_,_->start()}.setNegativeButton(android.R.string.cancel,null).show() else start()
                }
            }.show()
    }
    // Close the originating window before opening another; Android 16 otherwise loses dialog focus.
    private fun afterMenu(dialog:android.content.DialogInterface,action:()->Unit) {
        dialog.dismiss()
        page.post { if(!isFinishing && !isDestroyed)action() }
    }
    private fun generate(size:Int,seed:Long,isDaily:Boolean) {
        if(loading)return
        save();loading=true;showStatus(R.string.loading);enable(false)
        val date=if(isDaily)day()else ""
        background.execute {
            val result=runCatching {GameState(PuzzleGenerator.generate(size,seed),isDaily,date)}
            runOnUiThread {
                if(isFinishing||isDestroyed)return@runOnUiThread
                loading=false;enable(true)
                result.onSuccess {game=it;highlighted=emptySet();save(isDaily);render()}
                    .onFailure {showStatus(R.string.generation_error)}
            }
        }
    }
    private fun enable(enabled:Boolean){for(b in listOf(normal,daily,undo,hint,check)+buttons)b.isEnabled=enabled}
    private fun save(selectedDaily:Boolean?=null):Boolean {
        if(selectedDaily!=null)pendingModeSelection=selectedDaily
        val g=game
        if(g==null && pendingModeSelection==null)return true
        val editor=prefs.edit()
        if(g!=null) {
            editor.putString(key(g.daily),g.encode())
            // Retry completion with its snapshot; an existing marker prevents counting the same puzzle twice.
            if(g.won && !prefs.getBoolean("completed:${g.puzzle.id}",false))editor.putBoolean("completed:${g.puzzle.id}",true)
                .putInt("solved",prefs.getInt("solved",0)+1).putInt("unassisted",prefs.getInt("unassisted",0)+if(g.hints==0)1 else 0)
        }
        pendingModeSelection?.let {editor.putBoolean("daily_selected",it)}
        val ok=saveFeedback.commit {editor.commit()}
        if(ok)pendingModeSelection=null
        if(::status.isInitialized)showStatus(statusMessage)
        return ok
    }
    private fun showStatus(message:Int)=showStatus(getString(message))
    private fun showStatus(message:String) {
        statusMessage=message
        status.text=saveFeedback.message(message,getString(R.string.save_error))
    }
    private fun render() {
        val g=game?:return
        mode.text=if(g.daily)getString(R.string.daily_mode,g.day) else getString(R.string.free_mode,g.puzzle.size,g.puzzle.size)
        daily.setText(if(g.daily)R.string.free_play else R.string.daily)
        progress.text=getString(R.string.progress,g.cells.count{it>=0},g.cells.size,g.moves)
        grid.removeAllViews();buttons.clear();grid.columnCount=g.puzzle.size
        val available=resources.displayMetrics.widthPixels-dp(32)
        val width=maxOf(dp(44),available/g.puzzle.size)
        for(i in g.cells.indices) {
            val fixed=g.puzzle.clues[i]>=0;val value=g.cells[i]
            val b=Button(this).apply {
                id=cellIds[i];text=if(value<0)"·" else value.toString();textSize=21f;isAllCaps=false
                typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);minimumWidth=0;minWidth=0;minimumHeight=0;minHeight=0
                setPadding(0,0,0,0);setTextColor(if(fixed)Color.WHITE else navy)
                contentDescription=getString(R.string.cell_description,i/g.puzzle.size+1,i%g.puzzle.size+1,
                    if(value<0)getString(R.string.empty) else value.toString(),getString(if(fixed)R.string.fixed else R.string.editable))
                background=GradientDrawable().apply {shape=GradientDrawable.RECTANGLE;cornerRadius=dp(10).toFloat()
                    setColor(when {i in highlighted->Color.rgb(255,204,195);fixed->green;value==0->gold;value==1->Color.rgb(191,220,204);else->Color.WHITE})
                    setStroke(dp(1),if(fixed)green else Color.rgb(221,229,219))}
                isEnabled=!fixed && !g.won && !loading
                setOnClickListener{if(g.cycle(i)){highlighted=emptySet();save();render();winIfComplete()}}
            }
            buttons.add(b);grid.addView(b,GridLayout.LayoutParams().apply {this.width=width-dp(4);height=width-dp(4);setMargins(dp(2),dp(2),dp(2),dp(2))})
        }
        undo.isEnabled=g.history.isNotEmpty() && !loading;hint.isEnabled=!g.won && !loading;check.isEnabled=!g.won && !loading
        showStatus(if(g.won)R.string.won else R.string.tap_help)
    }
    private fun winIfComplete() {
        val g=game?:return
        if(!g.solved()||g.won)return
        g.won=true;save()
        render()
        AlertDialog.Builder(this).setTitle(R.string.won).setMessage(getString(R.string.win_stats,g.moves,g.hints))
            .setPositiveButton(R.string.continue_game,null).setNeutralButton(R.string.new_game){_,_->chooseNew()}.show()
    }
    private fun checkBoard() {
        val g=game?:return;highlighted=g.wrong().toSet();render()
        if(highlighted.isEmpty()) {showStatus(R.string.no_errors);winIfComplete()}else showStatus(getString(R.string.errors,highlighted.size))
    }
    private fun showHint() {
        val g=game?:return
        if(g.wrong().isNotEmpty()){checkBoard();showStatus(R.string.fix_errors);return}
        val step=Logic.next(g.cells,g.puzzle.size)?:return
        val message=getString(R.string.hint_explanation,step.index/g.puzzle.size+1,step.index%g.puzzle.size+1,step.value,
            getString(if(step.row)R.string.row else R.string.column),step.line+1)
        AlertDialog.Builder(this).setTitle(R.string.hint).setMessage(message).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.apply_hint){_,_->if(g.set(step.index,step.value)){g.hints++;save();render();winIfComplete()}}.show()
    }
    private fun rules()=AlertDialog.Builder(this).setTitle(R.string.rules).setMessage(R.string.rules_text).setPositiveButton(android.R.string.ok,null).show()
    private fun more() {
        AlertDialog.Builder(this).setTitle(R.string.more).setItems(arrayOf(getString(R.string.statistics),getString(R.string.ad_privacy_title),getString(R.string.ad_policy),getString(R.string.support))) {dialog,index ->
            afterMenu(dialog) {
                when(index){0->AlertDialog.Builder(this).setTitle(R.string.statistics).setMessage(getString(R.string.statistics_text,prefs.getInt("solved",0),prefs.getInt("unassisted",0))).setPositiveButton(android.R.string.ok,null).show()
                    1->ad.showPrivacyChoice();2->open(Intent(Intent.ACTION_VIEW,Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
                    3->open(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:jonkryl@gmail.com")).putExtra(Intent.EXTRA_SUBJECT,"Binary Garden ${BuildConfig.VERSION_NAME}"))}
            }
        }.show()
    }
    private fun open(intent:Intent){try{startActivity(intent)}catch(_:ActivityNotFoundException){Toast.makeText(this,R.string.ad_no_browser,Toast.LENGTH_LONG).show()}}
    override fun onStart(){super.onStart();if(::ad.isInitialized)ad.onStart()}
    override fun onStop(){save();if(::ad.isInitialized)ad.onStop();super.onStop()}
    override fun onDestroy(){if(::ad.isInitialized)ad.destroy();background.shutdownNow();super.onDestroy()}
}

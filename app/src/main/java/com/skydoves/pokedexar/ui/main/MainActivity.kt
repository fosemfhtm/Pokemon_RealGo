package com.skydoves.pokedexar.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.app.Dialog
import android.text.Html
import android.view.View
import android.view.Window
import android.widget.*
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.Toolbar
import com.amn.easysharedpreferences.EasySharedPreference
import com.google.gson.Gson
import com.skydoves.bindables.BindingActivity
import com.skydoves.pokedexar.R
import com.skydoves.pokedexar.database.BoxData
import com.skydoves.pokedexar.database.DataIO
import com.skydoves.pokedexar.databinding.ActivityMainBinding
import com.skydoves.pokedexar.model.Pokemon
import com.skydoves.pokedexar.ui.adapter.PokemonAdapter
import com.skydoves.pokedexar.ui.details.DetailActivity
import com.skydoves.pokedexar.ui.home.GVAdapter
import com.skydoves.pokedexar.ui.home.HomeActivity
import com.skydoves.pokedexar.ui.room.SocketHandler
import com.skydoves.pokedexar.ui.login.LoginActivity
import com.skydoves.pokedexar.ui.scene.SceneActivity
import com.skydoves.pokedexar.ui.shop.ShopActivity
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray

@AndroidEntryPoint
class MainActivity : BindingActivity<ActivityMainBinding>(R.layout.activity_main) {

  @VisibleForTesting
  val viewModel: MainViewModel by viewModels()

  lateinit var dialog02 :Dialog

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    val mToolbar = findViewById<Toolbar>(R.id.main_toolbar)
    setSupportActionBar(mToolbar)
    getSupportActionBar()?.setDisplayShowTitleEnabled(false)

    // 아래처럼 사용하세요!
    showInformation()

    dialog02 = Dialog(this)

    DataIO.requestUserAndDo {
      mMyId = it.nickname
    }


    //println(EasySharedPreference.Companion.getString("token", "noToken"))
    binding {
      lifecycleOwner = this@MainActivity
      adapter = PokemonAdapter()
      vm = viewModel
      HomeBtn.setOnClickListener {
        HomeActivity.startActivity(this@MainActivity)
      }
      BattleBtn.setOnClickListener {
        showDialog02()
      }
      ShopBtn.setOnClickListener {
        ShopActivity.startActivity(this@MainActivity)
      }
    }

    initSocket()
  }

  fun initSocket() {
    SocketHandler.setSocket()
    SocketHandler.establishConnection()

    val socket: Socket = SocketHandler.getSocket()
    socket.on("battle_start", onBattleStart)
  }

  var onBattleStart = Emitter.Listener { args ->
    val obj = JSONObject(args[0].toString())
    // Enter SceneActivity (AR)
    EasySharedPreference.Companion.putString("roomId", mRoomId)
    EasySharedPreference.Companion.putString("myId", mMyId)
    EasySharedPreference.Companion.putString("startObject", obj.toString())
    SceneActivity.startActivity(this@MainActivity)
  }

  override fun onResume() {
   showInformation()
    super.onResume()
  }
  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    super.onCreateOptionsMenu(menu)
    getMenuInflater().inflate(R.menu.logout, menu)
    return true
  }

  // 메뉴 선택시 Barcode 스캔을 진행한다.
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.logout -> {
        EasySharedPreference.Companion.putString("token", "")
        intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
        return true
      }
      else -> return true
    }
  }

  lateinit var mRoomId: String
  lateinit var mMyId: String

  fun showInformation(){
    val loading = Dialog(this)
    loading.setContentView(R.layout.loading_dialog)
    loading.setCancelable(false)
    loading.show()

    //findViewById<LinearLayout>(R.id.main_status).bringToFront()

    DataIO.requestUserAndDo {
      findViewById<TextView>(R.id.main_name).text = Html.fromHtml("<b>이름</b>   | ${it.nickname}")
      findViewById<TextView>(R.id.main_money).text = Html.fromHtml("<b>돈</b>   | ${it.money}원") //"돈 : ${it.money}$"
      findViewById<TextView>(R.id.main_winlose).text = Html.fromHtml("<b>전적</b>   | ${it.win}승 ${it.lose}패") //"전적 : ${it.win}승 ${it.lose}패"
    }
    DataIO.requestBoxAndDo {
      findViewById<TextView>(R.id.main_have).text = Html.fromHtml("<b>보유 포켓몬</b>   | ${it.size}마리") //"보유 포켓몬 : ${it.size}마리"
      showSelectedPokemon(it)
      loading.dismiss()
    }
  }

  fun showDialog02(){
    dialog02.setContentView(R.layout.battleroom_dialog)
    dialog02.show()

    val progressBar = dialog02.findViewById<ProgressBar>(R.id.matching_progress)

    val enter_btn = dialog02.findViewById<Button>(R.id.enter_btn)
    enter_btn.setOnClickListener{
      progressBar.visibility = View.VISIBLE

      val obj = JSONObject()
      val playerObj = JSONObject()

      DataIO.requestUserAndDo {
        println(it)
        playerObj.put("id", it.nickname.toString())
        DataIO.requestSelectedBoxAndDo {
          var jsonArray: JSONArray = JSONArray(Gson().toJson(it))
          playerObj.put("pokemons", jsonArray)

          val roomId: String = dialog02.findViewById<EditText>(R.id.room_number).text.toString()
          mRoomId = roomId
          obj.put("roomId", roomId)

          obj.put("player", playerObj)

          SocketHandler.getSocket().emit("room", obj)
        }
      }
    }

    val cancel_btn = dialog02.findViewById<Button>(R.id.cancel_btn)
    cancel_btn.setOnClickListener {
      progressBar.visibility = View.INVISIBLE
      dialog02.dismiss()
    }


  }

  private fun showSelectedPokemon(boxList:Array<BoxData>){
    val selectedBoxList = arrayOf<BoxData?>(null, null, null, null)
    boxList.forEach {
      if(it.selected > 0) {
        selectedBoxList[it.selected-1] = it
      }
    }


    val pokemon1 = selectedBoxList[0]
    val pokemon1_img = findViewById<ImageView>(R.id.pokemon1_img)
    val resourceId1 = this.resources.getIdentifier("pokemon${pokemon1?.pokemon?.id ?:""}", "drawable", this.packageName)
    pokemon1_img.setImageResource(resourceId1)

    val pokemon2 = selectedBoxList[1]
    val pokemon2_img = findViewById<ImageView>(R.id.pokemon2_img)
    val resourceId2 = this.resources.getIdentifier("pokemon${pokemon2?.pokemon?.id ?:""}", "drawable", this.packageName)
    pokemon2_img.setImageResource(resourceId2)

    val pokemon3 = selectedBoxList[2]
    val pokemon3_img = findViewById<ImageView>(R.id.pokemon3_img)
    val resourceId3 = this.resources.getIdentifier("pokemon${pokemon3?.pokemon?.id ?:""}", "drawable", this.packageName)
    pokemon3_img.setImageResource(resourceId3)

    val pokemon4 = selectedBoxList[3]
    val pokemon4_img = findViewById<ImageView>(R.id.pokemon4_img)
    val resourceId4 = this.resources.getIdentifier("pokemon${pokemon4?.pokemon?.id ?: ""}", "drawable", this.packageName)
    pokemon4_img.setImageResource(resourceId4)
  }
}

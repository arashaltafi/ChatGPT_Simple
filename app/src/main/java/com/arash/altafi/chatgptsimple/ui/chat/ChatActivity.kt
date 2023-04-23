package com.arash.altafi.chatgptsimple.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arash.altafi.chatgptsimple.BuildConfig
import com.arash.altafi.chatgptsimple.R
import com.arash.altafi.chatgptsimple.databinding.ActivityChatBinding
import com.arash.altafi.chatgptsimple.model.Message
import com.arash.altafi.chatgptsimple.model.MessageState
import com.arash.altafi.chatgptsimple.ui.image.ImageSearchActivity
import com.arash.altafi.chatgptsimple.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ChatActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityChatBinding.inflate(layoutInflater)
    }

    private var messageList: ArrayList<Message> = arrayListOf()
    private lateinit var messageAdapter: MessageAdapter

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json: MediaType = "application/json; charset=utf-8".toMediaType()

    private var networkConnection: ((connected: Boolean) -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            networkConnection?.invoke(true)
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            networkConnection?.invoke(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        registerNetworkConnectivity(this)
        init()
    }

    private fun init() = binding.apply {
        //first time (before change network)
        changeIconStatus(checkNetWork())

        networkConnection = {
            CoroutineScope(Dispatchers.Main).launch {
                changeIconStatus(it)
            }
        }

        val background = if (isDarkTheme()) {
            ContextCompat.getDrawable(this@ChatActivity, R.drawable.chat_bg_dark)
        } else {
            ContextCompat.getDrawable(this@ChatActivity, R.drawable.chat_bg_light)
        }
        rlRoot.background = background

        messageAdapter = MessageAdapter(messageList)
        rvChat.adapter = messageAdapter
        addToChat("Hi, How may I assist you today?", MessageState.BOT)

        btnSend.setOnClickListener {
            if (checkNetWork()) {
                val question = edtMessage.text.toString().trim()
                edtMessage.setText("")
                if (question.isEmpty()) {
                    toast("Please Write Your Question")
                    edtMessage.error = "Please Write Your Question"
                } else {
                    addToChat(question, MessageState.ME)
                    callAPI(question)
                }
            } else {
                toast("Please Turn On Your Internet!!!")
            }
        }

        ivMore.setOnClickListener {
            popupWindow(it)
        }

        edtMessage.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_SEND -> {
                    btnSend.performClick()
                }
            }
            false
        }
    }

    private fun popupWindow(view: View) {
        PopupUtil.showPopup(
            view,
            listOf(
                PopupUtil.PopupItem(
                    R.drawable.ic_baseline_image_search_24,
                    getString(R.string.gpt_image)
                ) {
                    startActivity(Intent(this, ImageSearchActivity::class.java))
                }
            ),
            Gravity.BOTTOM.or(Gravity.END),
            setTint = false
        )
    }

    private fun checkNetWork() = NetworkUtils.isConnected(this@ChatActivity)

    @SuppressLint("ResourceAsColor")
    private fun changeIconStatus(isConnect: Boolean) {
        val red =
            ContextCompat.getColor(this, R.color.dark_red)
        val green =
            ContextCompat.getColor(this, R.color.green)
        val color = if (isConnect) green else red
        binding.ivStatus.setColorFilter(color)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addToChat(message: String, sentBy: MessageState) {
        runOnUiThread {
            messageList.add(Message(message, sentBy))
            messageAdapter.notifyDataSetChanged()
            binding.rvChat.smoothScrollToPosition(messageAdapter.itemCount)
        }
    }

    private fun addResponse(response: String) {
        messageList.removeAt(messageList.size - 1)
        addToChat(response, MessageState.BOT)
        runOnUiThread {
            binding.tvToolbarState.toGone()
            binding.btnSend.isClickable = true
        }
    }

    private fun callAPI(question: String?) {
        binding.btnSend.isClickable = false
        messageList.add(Message("Typing... ", MessageState.TYPING))
        binding.tvToolbarState.toShow()
        val jsonBody = JSONObject()
        try {
            jsonBody.put("model", "gpt-3.5-turbo")
            val messageArr = JSONArray()
            val obj = JSONObject()
            obj.put("role", "user")
            obj.put("content", question)
            messageArr.put(obj)
            jsonBody.put("messages", messageArr)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        val requestBody = jsonBody.toString().toRequestBody(json)
        val request: Request = Request.Builder()
            .url(BuildConfig.OPENAI_URL)
            .header("Authorization", "Bearer ${BuildConfig.TOKEN}")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                CoroutineScope(Dispatchers.Main).launch {
                    delay(500)
                    addResponse("Failed to load response due to " + e.message)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val jsonObject: JSONObject?
                    try {
                        jsonObject = JSONObject(response.body!!.string())
                        val jsonArray = jsonObject.getJSONArray("choices")
                        val result = jsonArray.getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        addResponse(result)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        addResponse("Failed to load response due to " + response.body.toString())
                    }
                }
            }
        })
    }

    private fun registerNetworkConnectivity(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val builder = NetworkRequest.Builder().build()
            connectivityManager.registerNetworkCallback(builder, networkCallback)
        }
    }

    private fun unregisterNetworkConnectivity(context: Context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkConnectivity(this)
    }

}
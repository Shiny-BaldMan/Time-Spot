package com.example.timecaptule

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.doOnPreDraw
import java.util.concurrent.Executors
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Offline prototype; real authentication and server access checks are not connected. */
class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("capsule_preview", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    private var timer: CountDownTimer? = null
    private var screen = "home"
    private val startupExecutor = Executors.newSingleThreadExecutor()
    private val dates = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    "auth", "splash" -> finish()
                    "home" -> auth()
                    else -> home()
                }
            }
        })
        splash()
    }

    private fun splash() {
        screen = "splash"
        setContentView(R.layout.activity_splash)
        val root = findViewById<View>(R.id.splashRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(32) + bars.left, dp(32) + bars.top, dp(32) + bars.right, dp(32) + bars.bottom)
            insets
        }
        root.doOnPreDraw {
            startupExecutor.execute {
                // Warm local storage off the UI thread; no artificial splash delay.
                prefs.all
                // Replace with a validated session result when authentication is connected.
                // The offline preview does not create a signed-in session.
                val hasAuthenticatedSession = false
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && screen == "splash") {
                        if (hasAuthenticatedSession) home() else auth()
                    }
                }
            }
        }
    }

    private fun auth() {
        timer?.cancel()
        screen = "auth"
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val nickname = findViewById<EditText>(R.id.nicknameInput)
        val password = findViewById<EditText>(R.id.passwordInput)
        val nicknameLayout = findViewById<TextInputLayout>(R.id.nicknameLayout)
        val passwordLayout = findViewById<TextInputLayout>(R.id.passwordLayout)
        val status = findViewById<TextView>(R.id.authStatus)
        val submit = findViewById<Button>(R.id.authSubmit)
        var signup = false
        fun message(text: String) {
            status.text = text
            status.visibility = View.VISIBLE
        }
        fun validNickname(): Boolean {
            val valid = nickname.text.toString().matches(Regex("[가-힣a-zA-Z0-9_]{2,20}"))
            nicknameLayout.error = if (valid) null else "한글, 영문, 숫자, 밑줄로 2~20자를 입력해 주세요."
            return valid
        }
        nickname.doAfterTextChanged {
            nicknameLayout.error = null
            status.visibility = View.GONE
        }
        password.doAfterTextChanged {
            passwordLayout.error = null
            status.visibility = View.GONE
        }
        findViewById<MaterialButtonToggleGroup>(R.id.authTabs).addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                signup = id == R.id.signupTab
                findViewById<TextView>(R.id.authTitle).text = if (signup) "나만의 이름으로 시작해요" else "다시 만나 반가워요"
                findViewById<TextView>(R.id.authDescription).text = if (signup) "닉네임과 비밀번호, 두 가지면 충분해요." else "닉네임과 비밀번호로 기억을 이어 가세요."
                findViewById<View>(R.id.checkNickname).visibility = if (signup) View.VISIBLE else View.GONE
                findViewById<View>(R.id.signupNote).visibility = if (signup) View.VISIBLE else View.GONE
                findViewById<View>(R.id.recoveryButton).visibility = if (signup) View.GONE else View.VISIBLE
                nicknameLayout.helperText = if (signup) "한글·영문·숫자·밑줄 2~20자" else null
                passwordLayout.helperText = if (signup) "비밀번호는 8자 이상 입력해 주세요." else null
                submit.text = if (signup) "가입하기" else "로그인"
                password.text.clear()
                nicknameLayout.error = null
                passwordLayout.error = null
                status.visibility = View.GONE
            }
        }
        findViewById<View>(R.id.checkNickname).setOnClickListener {
            if (validNickname()) message("중복 확인은 준비 중이에요. 아직 닉네임이 예약되지 않았어요.")
        }
        submit.setOnClickListener {
            val nameValid = if (signup) validNickname() else nickname.text.isNotBlank().also {
                nicknameLayout.error = if (it) null else "닉네임을 입력해 주세요."
            }
            val passwordValid = if (signup) password.text.length >= 8 else password.text.isNotEmpty()
            passwordLayout.error = if (passwordValid) null else if (signup) "8자 이상 입력해 주세요." else "비밀번호를 입력해 주세요."
            if (nameValid && passwordValid) {
                when {
                    signup -> message("회원가입은 준비 중이에요. 입력한 정보는 저장되지 않았어요.")
                    // Local dummy account for trying the main screen without a server.
                    nickname.text.toString() == "123" && password.text.toString() == "123" -> home()
                    else -> message("닉네임 또는 비밀번호가 일치하지 않아요.")
                }
            }
        }
        password.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                submit.performClick()
                true
            } else false
        }
        findViewById<View>(R.id.recoveryButton).setOnClickListener {
            message("비밀번호 복구는 준비 중이에요. 복구 코드를 이용하는 방식을 제공할 예정입니다.")
        }
        findViewById<View>(R.id.previewButton).setOnClickListener { home() }
    }

    private fun page(name: String, title: String, subtitle: String) {
        timer?.cancel()
        screen = name
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        scroll.addView(body)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        label("TIME SPOT", 13)
        label(title, 28)
        label(subtitle)
        if (name != "home") button("홈으로") { home() }
    }

    private fun home() {
        page("home", "기억을 묻는 곳", "오늘 남긴 작은 기록이 미래의 선물이 됩니다.")
        label("지도 연결 준비 중", 22)
        label("지도 연결 후 현재 위치와 캡슐 마커가 표시됩니다. 지금은 장소 이름을 직접 기록해 보세요.")
        button("+ 캡슐 묻기") { compose() }
        button("친구 · 계정") { settings() }
        label("내 캡슐", 22)
        val items = capsules()
        if (items.length() == 0) label("아직 묻은 캡슐이 없어요. 첫 번째 기억을 남겨 보세요.")
        for (index in items.length() - 1 downTo 0) {
            val item = items.getJSONObject(index)
            val state = if (System.currentTimeMillis() < item.getLong("opensAt")) "잠김" else "열람 가능"
            button("[$state] ${item.getString("title")}\n${item.getString("place")} · ${dates.format(item.getLong("opensAt"))}") { detail(item) }
        }
    }

    private fun compose() {
        page("compose", "미래에 보낼 기억", "이 기기에서만 보관되는 체험용 캡슐입니다.")
        val title = field("캡슐 제목 (최대 60자)", 60)
        val place = field("장소 이름 (최대 100자)", 100)
        val content = field("미래의 나에게 남길 이야기", 5000, true)
        val opensAt = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val selected = label("열람 시간: ${dates.format(opensAt.time)}")
        button("열람 날짜와 시간 선택") {
            DatePickerDialog(this, { _, year, month, day ->
                val candidate = opensAt.clone() as Calendar
                candidate.set(year, month, day)
                TimePickerDialog(this, { _, hour, minute ->
                    candidate.set(Calendar.HOUR_OF_DAY, hour)
                    candidate.set(Calendar.MINUTE, minute)
                    candidate.set(Calendar.SECOND, 0)
                    candidate.set(Calendar.MILLISECOND, 0)
                    opensAt.timeInMillis = candidate.timeInMillis
                    selected.text = "열람 시간: ${dates.format(opensAt.time)}"
                }, opensAt.get(Calendar.HOUR_OF_DAY), opensAt.get(Calendar.MINUTE), true).show()
            }, opensAt.get(Calendar.YEAR), opensAt.get(Calendar.MONTH), opensAt.get(Calendar.DAY_OF_MONTH)).show()
        }
        label("공개 범위: 나만 보기\n친구 공유와 사진 첨부는 추후 연결됩니다.")
        button("캡슐 저장") {
            when {
                title.text.isBlank() -> title.error = "제목을 입력해 주세요."
                place.text.isBlank() -> place.error = "장소를 입력해 주세요."
                content.text.isBlank() -> content.error = "내용을 입력해 주세요."
                opensAt.timeInMillis <= System.currentTimeMillis() -> Toast.makeText(this, "미래 시간을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                else -> {
                    val items = capsules()
                    items.put(JSONObject().put("title", title.text.toString().trim())
                        .put("place", place.text.toString().trim()).put("content", content.text.toString().trim())
                        .put("opensAt", opensAt.timeInMillis))
                    prefs.edit().putString("capsules", items.toString()).apply()
                    home()
                }
            }
        }
    }

    private fun detail(item: JSONObject) {
        val remaining = item.getLong("opensAt") - System.currentTimeMillis()
        page("detail", if (remaining > 0) "아직 잠긴 기억" else item.getString("title"),
            "열람 시간: ${dates.format(item.getLong("opensAt"))}")
        if (remaining > 0) {
            val countdown = label("", 24)
            fun update(milliseconds: Long) {
                val seconds = (milliseconds + 999) / 1000
                countdown.text = "${seconds / 86400}일 ${seconds / 3600 % 24}시간 ${seconds / 60 % 60}분 ${seconds % 60}초 남음"
            }
            update(remaining)
            label("약속한 시간이 되면 내용을 확인할 수 있어요.")
            timer = object : CountDownTimer(remaining, 1000) {
                override fun onTick(millisUntilFinished: Long) = update(millisUntilFinished)
                override fun onFinish() = detail(item)
            }.start()
        } else {
            label(item.getString("place"), 20)
            label(item.getString("content"), 18)
        }
    }

    private fun settings() {
        page("settings", "친구와 계정", "현재는 계정 없이 사용하는 오프라인 체험판입니다.")
        label("닉네임 · 비밀번호 가입", 22)
        button("로그인 · 회원가입") { auth() }
        label("인증 서버 연결 후 닉네임 중복 검사, 가입과 자동 로그인을 사용할 수 있습니다.")
        label("친구에게만 나누는 기억", 22)
        label("친구 검색·요청·수락과 선택한 친구에게 캡슐을 공유하는 기능을 준비할 예정입니다.")
        label("체험판의 시간 잠금은 기기 시간을 기준으로 합니다. 실제 서비스에서는 서버에서 열람 권한을 확인해야 합니다.")
    }

    private fun capsules() = JSONArray(prefs.getString("capsules", "[]") ?: "[]")
    private fun label(value: String, size: Int = 16): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setPadding(0, dp(10), 0, dp(10))
        body.addView(this)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { action() }
        body.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun field(hintText: String, limit: Int, multiline: Boolean = false) = EditText(this).apply {
        hint = hintText
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            (if (multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        minLines = if (multiline) 4 else 1
        filters = arrayOf(android.text.InputFilter.LengthFilter(limit))
        body.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        startupExecutor.shutdownNow()
        timer?.cancel()
        super.onDestroy()
    }
}

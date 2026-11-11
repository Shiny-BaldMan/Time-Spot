package com.example.timecaptule

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.graphics.Color
import android.view.View
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textfield.TextInputLayout
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.doOnPreDraw
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Firebase accounts with a local capsule preview. */
class MainActivity : AppCompatActivity() {
    private val accounts by lazy { AuthRepository() }
    private val social by lazy { SocialRepository() }
    private val capsuleStore by lazy { CapsuleRepository() }
    private val prefs get() = getSharedPreferences("capsules_${accounts.currentUser?.uid ?: "guest"}", MODE_PRIVATE)
    private lateinit var body: LinearLayout
    private var pageScroll: ScrollView? = null
    private var mapWebView: WebView? = null
    private var locationStatus: TextView? = null
    private var currentLocation: Location? = null
    private var lastRenderedLocation: Location? = null
    private var lastMapLoadAt = 0L
    private var locationUpdatesActive = false
    private var selectedTab = R.id.nav_map
    private val locationManager by lazy { getSystemService(LOCATION_SERVICE) as LocationManager }
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            renderCurrentLocation(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }
    private var timer: CountDownTimer? = null
    private var screen = "home"
    private val dates = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
    private val selectedFriendIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (screen) {
                    "auth", "splash", "map" -> finish()
                    "compose", "detail" -> showSelectedTab()
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
            if (accounts.currentUser == null) {
                auth()
            } else {
                accounts.ensureProfile().addOnCompleteListener { task ->
                    if (!isFinishing && !isDestroyed && screen == "splash") {
                        if (task.isSuccessful) home() else auth(accounts.errorMessage(task.exception))
                    }
                }
            }
        }
    }

    private fun auth(initialMessage: String? = null) {
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
        val tabs = findViewById<MaterialButtonToggleGroup>(R.id.authTabs)
        var signup = false
        var busy = false
        fun message(text: String) {
            status.text = text
            status.visibility = View.VISIBLE
        }
        fun validNickname(): Boolean {
            val valid = accounts.isValidNickname(accounts.normalizeNickname(nickname.text.toString()))
            nicknameLayout.error = if (valid) null else "영문, 숫자, 밑줄로 2~20자를 입력해 주세요."
            return valid
        }
        fun loading(value: Boolean) {
            busy = value
            nickname.isEnabled = !value
            password.isEnabled = !value
            submit.isEnabled = !value
            for (index in 0 until tabs.childCount) tabs.getChildAt(index).isEnabled = !value
            findViewById<View>(R.id.previewButton).isEnabled = !value
            submit.text = if (value) "처리 중…" else if (signup) "가입하기" else "로그인"
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
                findViewById<View>(R.id.signupNote).visibility = if (signup) View.VISIBLE else View.GONE
                findViewById<View>(R.id.recoveryButton).visibility = if (signup) View.GONE else View.VISIBLE
                nicknameLayout.helperText = if (signup) "영문·숫자·밑줄 2~20자 · 대소문자 구분 없음" else null
                passwordLayout.helperText = if (signup) "비밀번호는 8자 이상 입력해 주세요." else null
                submit.text = if (signup) "가입하기" else "로그인"
                password.text.clear()
                nicknameLayout.error = null
                passwordLayout.error = null
                status.visibility = View.GONE
            }
        }
        submit.setOnClickListener {
            if (busy) return@setOnClickListener
            val nameValid = validNickname()
            val passwordValid = if (signup) password.text.length >= 8 else password.text.isNotEmpty()
            passwordLayout.error = if (passwordValid) null else if (signup) "8자 이상 입력해 주세요." else "비밀번호를 입력해 주세요."
            if (nameValid && passwordValid) {
                loading(true)
                status.visibility = View.GONE
                val name = accounts.normalizeNickname(nickname.text.toString())
                val secret = password.text.toString()
                val request = if (signup) accounts.signUp(name, secret) else accounts.signIn(name, secret)
                request.addOnCompleteListener { task ->
                    if (isFinishing || isDestroyed || screen != "auth") return@addOnCompleteListener
                    loading(false)
                    if (task.isSuccessful) {
                        selectedTab = R.id.nav_map
                        val keyboard = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        keyboard.hideSoftInputFromWindow(password.windowToken, 0)
                        password.text.clear()
                        home()
                    } else {
                        message(accounts.errorMessage(task.exception))
                    }
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
            message("이메일을 등록하지 않는 방식이라 이메일 비밀번호 복구는 지원하지 않아요.")
        }
        findViewById<View>(R.id.previewButton).setOnClickListener {
            accounts.signOut()
            selectedTab = R.id.nav_map
            home()
        }
        initialMessage?.let { message(it) }
    }

    private fun page(name: String, title: String, subtitle: String) {
        timer?.cancel()
        stopLocationUpdates()
        mapWebView?.apply { stopLoading(); destroy() }
        mapWebView = null
        pageScroll = null
        locationStatus = null
        screen = name
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.spot_background))
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
            setBackgroundColor(getColor(R.color.spot_background))
        }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_capsule)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) })
        brand.addView(TextView(this).apply {
            text = "TIME SPOT"
            textSize = 15f
            setTextColor(getColor(R.color.spot_ink))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(52)))

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(16))
            setBackgroundColor(getColor(R.color.spot_background))
        }
        if (name == "map") {
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val scroll = ScrollView(this).apply { isFillViewport = true }
            scroll.clipToPadding = false
            scroll.addView(body)
            root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
            pageScroll = scroll
        }

        val nav = BottomNavigationView(this).apply {
            inflateMenu(R.menu.main_navigation)
            selectedItemId = selectedTab
            itemIconTintList = navigationTint()
            itemTextColor = navigationTint()
            labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
            itemIconSize = dp(23)
            itemActiveIndicatorColor = ColorStateList.valueOf(getColor(R.color.spot_nav_indicator))
            elevation = 0f
            setBackgroundColor(getColor(R.color.spot_surface))
            setOnItemSelectedListener { item ->
                selectedTab = item.itemId
                showSelectedTab()
                true
            }
            setOnItemReselectedListener { item ->
                if (screen !in setOf("map", "friends", "capsules", "settings")) {
                    selectedTab = item.itemId
                    showSelectedTab()
                }
            }
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, 0)
            nav.setPadding(nav.paddingLeft, nav.paddingTop, nav.paddingRight, bars.bottom)
            pageScroll?.setPadding(0, 0, 0, (ime.bottom - bars.bottom).coerceAtLeast(0))
            insets
        }
        label(title, 26)
        label(subtitle, 15)
    }

    private fun home() {
        selectedTab = R.id.nav_map
        lastRenderedLocation = null
        lastMapLoadAt = 0L
        page("map", "지도", "현재 위치를 중심으로 주변을 확인하세요.")
        locationStatus = label("현재 위치를 확인하고 있어요…", 14)
        button("현재 위치로 이동") { requestCurrentLocation() }
        val map = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            settings.userAgentString = "${settings.userAgentString} TimeSpot/1.0"
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    locationStatus?.text = "지도를 불러오는 중…"
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    locationStatus?.text = "현재 위치 주변 지도"
                }
                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) locationStatus?.text = "지도를 불러오지 못했어요. 인터넷 연결을 확인해 주세요."
                }
            }
            setBackgroundColor(Color.WHITE)
        }
        mapWebView = map
        body.addView(map, LinearLayout.LayoutParams(-1, 0, 1f))
        label("© OpenStreetMap contributors", 12)
        requestCurrentLocation()
    }

    private fun showSelectedTab() {
        when (selectedTab) {
            R.id.nav_map -> home()
            R.id.nav_friends -> friends()
            R.id.nav_capsules -> capsuleList()
            R.id.nav_settings -> settings()
        }
    }

    private fun capsuleList() {
        selectedTab = R.id.nav_capsules
        page("capsules", "캡슐", "내가 묻었거나 친구가 공유한 기억입니다.")
        button("+ 새 캡슐") { compose() }
        if (accounts.currentUser == null) {
            renderLocalCapsules()
        } else {
            label("캡슐을 불러오는 중…")
            capsuleStore.listVisible().addOnCompleteListener { task ->
                if (isFinishing || isDestroyed || screen != "capsules") return@addOnCompleteListener
                if (task.isSuccessful) renderCloudCapsules(task.result)
                else label(accounts.errorMessage(task.exception))
            }
        }
    }

    private fun requestCurrentLocation() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            locationStatus?.text = "현재 위치를 지도에 표시하려면 위치 권한이 필요해요."
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }
        startLocationUpdates(fine)
    }

    private fun startLocationUpdates(fine: Boolean = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        if (screen != "map" || mapWebView == null) return
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return
        stopLocationUpdates()
        val providers = mutableListOf<String>()
        if (runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)) {
            providers.add(LocationManager.NETWORK_PROVIDER)
        }
        if (fine && runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)) {
            providers.add(LocationManager.GPS_PROVIDER)
        }
        if (providers.isEmpty()) {
            locationStatus?.text = "위치 서비스를 켜면 현재 위치를 표시할 수 있어요."
            return
        }
        val lastKnown = providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
        lastKnown?.let {
            currentLocation = it
            renderCurrentLocation(it, force = true)
        }
        try {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(provider, 12_000L, 25f, locationListener, Looper.getMainLooper())
            }
            locationUpdatesActive = true
        } catch (_: SecurityException) {
            locationStatus?.text = "위치 권한을 확인할 수 없어요. 다시 허용해 주세요."
        }
    }

    private fun renderCurrentLocation(location: Location, force: Boolean = false) {
        locationStatus?.text = "현재 위치를 지도에 표시했어요."
        val map = mapWebView ?: return
        val previous = lastRenderedLocation
        if (!force && previous != null && previous.distanceTo(location) < 75f && System.currentTimeMillis() - lastMapLoadAt < 30_000L) return
        val lat = location.latitude.coerceIn(-85.0, 85.0)
        val lon = location.longitude.coerceIn(-180.0, 180.0)
        val latDelta = 0.006
        val lonDelta = (0.008 / kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(0.15)).coerceAtMost(0.08)
        val bbox = Uri.encode("${lon - lonDelta},${lat - latDelta},${lon + lonDelta},${lat + latDelta}")
        val marker = Uri.encode("$lat,$lon")
        map.loadUrl("https://www.openstreetmap.org/export/embed.html?bbox=$bbox&layer=mapnik&marker=$marker")
        lastRenderedLocation = Location(location)
        lastMapLoadAt = System.currentTimeMillis()
    }

    private fun stopLocationUpdates() {
        if (locationUpdatesActive) {
            runCatching { locationManager.removeUpdates(locationListener) }
            locationUpdatesActive = false
        }
    }

    private fun navigationTint(): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(getColor(R.color.spot_ink), getColor(R.color.spot_muted))
    )

    private fun compose() {
        page("compose", "미래에 보낼 기억", if (accounts.currentUser == null) "이 기기에 저장되는 체험용 캡슐입니다." else "친구와 공유할 캡슐을 만들 수 있어요.")
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
        selectedFriendIds.clear()
        var friendsReady = accounts.currentUser == null
        var saveButton: Button? = null
        if (accounts.currentUser == null) {
            label("공개 범위: 나만 보기 (체험 캡슐은 이 기기에 저장됩니다.)")
        } else {
            label("공유할 친구를 선택하세요. 선택하지 않으면 나만 볼 수 있어요.")
            val friendsStatus = label("친구 목록을 불러오는 중…")
            social.listFriends().addOnCompleteListener { task ->
                if (isFinishing || isDestroyed || screen != "compose") return@addOnCompleteListener
                friendsStatus.visibility = View.GONE
                if (task.isSuccessful) {
                    if (task.result.isEmpty()) label("친구 · 계정에서 닉네임으로 친구 요청을 보낼 수 있어요.")
                    task.result.forEach { friend ->
                        CheckBox(this).apply {
                            text = "${friend.nickname} (${friend.uid.take(8)}…)"
                            setOnCheckedChangeListener { _, checked ->
                                if (checked && selectedFriendIds.size >= 8) {
                                    isChecked = false
                                    Toast.makeText(this@MainActivity, "캡슐 하나는 최대 8명과 공유할 수 있어요.", Toast.LENGTH_SHORT).show()
                                } else if (checked) selectedFriendIds.add(friend.uid) else selectedFriendIds.remove(friend.uid)
                            }
                            body.addView(this)
                        }
                    }
                    friendsReady = true
                    saveButton?.isEnabled = true
                } else {
                    label(accounts.errorMessage(task.exception))
                    friendsReady = true
                    saveButton?.isEnabled = true
                }
            }
        }
        saveButton = button("캡슐 저장") {
            if (!friendsReady) {
                Toast.makeText(this, "친구 목록을 불러오는 중이에요.", Toast.LENGTH_SHORT).show()
                return@button
            }
            when {
                title.text.isBlank() -> title.error = "제목을 입력해 주세요."
                place.text.isBlank() -> place.error = "장소를 입력해 주세요."
                content.text.isBlank() -> content.error = "내용을 입력해 주세요."
                opensAt.timeInMillis <= System.currentTimeMillis() -> Toast.makeText(this, "미래 시간을 선택해 주세요.", Toast.LENGTH_SHORT).show()
                else -> {
                    if (accounts.currentUser != null) {
                        saveButton?.isEnabled = false
                        capsuleStore.create(title.text.toString().trim(), place.text.toString().trim(),
                            content.text.toString().trim(), opensAt.timeInMillis, selectedFriendIds.toList())
                            .addOnCompleteListener { task ->
                                if (isFinishing || isDestroyed || screen != "compose") return@addOnCompleteListener
                        if (task.isSuccessful) capsuleList() else {
                                    saveButton?.isEnabled = true
                                    Toast.makeText(this, accounts.errorMessage(task.exception), Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        val items = capsules()
                        items.put(JSONObject().put("title", title.text.toString().trim())
                            .put("place", place.text.toString().trim()).put("content", content.text.toString().trim())
                            .put("opensAt", opensAt.timeInMillis))
                        prefs.edit().putString("capsules", items.toString()).apply()
                        capsuleList()
                    }
                }
            }
        }
        if (!friendsReady) saveButton?.isEnabled = false
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

    private fun friends() {
        selectedTab = R.id.nav_friends
        page("friends", "친구", "요청을 수락하면 서로의 친구 목록에 추가됩니다.")
        if (accounts.currentUser == null) {
            label("친구를 추가하려면 로그인해 주세요.")
            button("로그인 · 회원가입") { auth() }
            return
        }
        button("닉네임으로 친구 요청") { promptFriendRequest() }
        label("받은 요청", 21)
        val requestStatus = label("요청을 불러오는 중…")
        social.incomingRequests().addOnCompleteListener { task ->
            if (isFinishing || isDestroyed || screen != "friends") return@addOnCompleteListener
            requestStatus.visibility = View.GONE
            if (!task.isSuccessful) {
                label(accounts.errorMessage(task.exception))
            } else if (task.result.isEmpty()) {
                label("받은 요청이 없어요.")
            } else {
                task.result.forEach { request ->
                    label("${request.fromNickname}님의 친구 요청")
                    button("수락") {
                        social.acceptRequest(request).addOnCompleteListener { accepted ->
                            if (accepted.isSuccessful) friends()
                            else Toast.makeText(this, accounts.errorMessage(accepted.exception), Toast.LENGTH_LONG).show()
                        }
                    }
                    button("거절") {
                        social.rejectRequest(request).addOnCompleteListener { rejected ->
                            if (rejected.isSuccessful) friends()
                            else Toast.makeText(this, accounts.errorMessage(rejected.exception), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        label("내 친구", 21)
        val friendListStatus = label("친구 목록을 불러오는 중…")
        social.listFriends().addOnCompleteListener { task ->
            if (isFinishing || isDestroyed || screen != "friends") return@addOnCompleteListener
            friendListStatus.visibility = View.GONE
            if (!task.isSuccessful) {
                label(accounts.errorMessage(task.exception))
            } else if (task.result.isEmpty()) {
                label("수락한 친구가 여기에 표시됩니다.")
            } else {
                task.result.forEach { friend ->
                    label(friend.nickname, 18)
                    button("${friend.nickname} 친구 해제") {
                        social.removeFriend(friend.uid).addOnCompleteListener { removal ->
                            if (removal.isSuccessful) friends()
                            else Toast.makeText(this, accounts.errorMessage(removal.exception), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun settings() {
        selectedTab = R.id.nav_settings
        page("settings", "설정", accounts.nickname?.let { "${it} 계정으로 로그인 중이에요." } ?: "로그인 없이 체험 중이에요.")
        label("계정", 21)
        if (accounts.currentUser != null) {
            label("닉네임 · ${accounts.nickname}")
            button("로그아웃") {
                accounts.signOut()
                selectedTab = R.id.nav_map
                auth()
            }
        } else {
            button("로그인 · 회원가입") { auth() }
        }
        label("앱 설정", 21)
        val locationAllowed = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        label(if (locationAllowed) "위치 권한 · 허용됨" else "위치 권한 · 허용되지 않음")
        button("위치 권한 설정 열기") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        label("위치 권한은 지도 표시용입니다. 지도를 불러올 때 현재 좌표가 OpenStreetMap으로 전송됩니다.")
        label("캡슐은 열람 시간과 공유 대상에 따라 Firestore에서 관리됩니다.")
    }

    private fun promptFriendRequest() {
        val input = EditText(this).apply {
            hint = "친구 닉네임"
            maxLines = 1
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("닉네임으로 친구 요청")
            .setMessage("상대 닉네임을 입력하면 친구 요청을 보냅니다.")
            .setView(input)
            .setNegativeButton("취소", null)
            .setPositiveButton("요청 보내기", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nickname = input.text.toString().trim()
                if (nickname.isBlank()) {
                    input.error = "닉네임을 입력해 주세요."
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                social.sendFriendRequest(nickname).addOnCompleteListener { task ->
                    if (isFinishing || isDestroyed) return@addOnCompleteListener
                    dialog.dismiss()
                    if (task.isSuccessful) {
                        Toast.makeText(this, "${task.result.nickname}님에게 친구 요청을 보냈어요.", Toast.LENGTH_SHORT).show()
                        friends()
                    } else {
                        Toast.makeText(this, accounts.errorMessage(task.exception), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun renderLocalCapsules() {
        val items = capsules()
        if (items.length() == 0) label("아직 묻은 캡슐이 없어요. 첫 번째 기억을 남겨 보세요.")
        for (index in items.length() - 1 downTo 0) {
            val item = items.getJSONObject(index)
            val state = if (System.currentTimeMillis() < item.getLong("opensAt")) "잠김" else "열람 가능"
            button("[$state] ${item.getString("title")}\n${item.getString("place")} · ${dates.format(item.getLong("opensAt"))}") { detail(item) }
        }
    }

    private fun renderCloudCapsules(items: List<SharedCapsule>) {
        if (items.isEmpty()) {
            label("아직 캡슐이 없어요. 첫 기억을 남기거나 친구에게 공유받아 보세요.")
            return
        }
        items.sortedByDescending { it.opensAtMillis }.forEach { item ->
            val own = item.ownerUid == accounts.currentUser?.uid
            val title = if (own) item.title else "${item.ownerNickname}님의 캡슐 · ${item.title}"
            button("$title\n${item.place} · ${dates.format(item.opensAtMillis)}") {
                detail(JSONObject().put("title", title).put("place", item.place)
                    .put("content", item.content).put("opensAt", item.opensAtMillis))
            }
        }
    }

    private fun capsules() = JSONArray(prefs.getString("capsules", "[]") ?: "[]")
    private fun label(value: String, size: Int = 16): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(getColor(if (size <= 14) R.color.spot_muted else R.color.spot_ink))
        setPadding(0, dp(7), 0, dp(7))
        body.addView(this)
    }
    private fun button(value: String, action: () -> Unit) = com.google.android.material.button.MaterialButton(
        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
    ).apply {
        text = value
        isAllCaps = false
        minHeight = dp(50)
        cornerRadius = dp(12)
        strokeColor = ColorStateList.valueOf(getColor(R.color.spot_border))
        setTextColor(getColor(R.color.spot_ink))
        setOnClickListener { action() }
        body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) startLocationUpdates(fine)
            else locationStatus?.text = "위치 권한이 꺼져 있어요. 설정에서 허용하면 현재 위치를 볼 수 있어요."
        }
    }
    override fun onPause() {
        stopLocationUpdates()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        if (screen == "map" && mapWebView != null) startLocationUpdates()
    }
    override fun onDestroy() {
        stopLocationUpdates()
        mapWebView?.apply { stopLoading(); destroy() }
        mapWebView = null
        timer?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 501
    }
}

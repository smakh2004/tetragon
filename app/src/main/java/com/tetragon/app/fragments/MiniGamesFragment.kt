package com.tetragon.app.fragments

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Rive
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.tetragon.app.MainActivity
import com.tetragon.app.R
import com.tetragon.app.connectivityCheck.userPresenceUtils.UserPresenceHelper
import com.tetragon.app.gameModel.GameModel
import com.tetragon.app.gameModel.GameStatus
import com.tetragon.app.ui.uiMathStorm.MathStormActivity
import com.tetragon.app.ui.uiMathStormOnline.OnlineWaitingRoomMathStormActivity
import com.tetragon.app.utils.mathStormUtils.OnlineGameData
import com.tetragon.app.utils.onlineRoomUtils.OnlineRoom
import com.tetragon.app.utils.onlineRoomUtils.OnlineRoomAdapter

class MiniGamesFragment : Fragment() {

    // ---------------- Firebase ----------------
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var roomsListener: ListenerRegistration? = null
    private var myRoomListener: ListenerRegistration? = null
    private var recordListener: ListenerRegistration? = null
    private var onlineStatsListener: ListenerRegistration? = null

    // ---------------- Header ----------------
    private lateinit var recordValue: TextView
    private lateinit var winsValue: TextView
    private lateinit var losesValue: TextView

    // ---------------- List ----------------
    private lateinit var roomsRecycler: RecyclerView
    private lateinit var emptyRoomsText: TextView

    // ---------------- Play online button ----------------
    private lateinit var playOnlineBtn: Button
    private lateinit var playOnlineEnabledContainer: FrameLayout
    private lateinit var playOnlineDisabledContainer: FrameLayout
    private lateinit var playOnlineDisabledTxt: TextView
    private lateinit var searchingIcon: View

    // ---------------- Solo button ----------------
    private lateinit var soloGameBtn: Button
    private lateinit var soloEnabledContainer: FrameLayout
    private lateinit var soloDisabledContainer: FrameLayout
    private lateinit var soloDisabledTxt: TextView

    private lateinit var loadingOverlayContainer: FrameLayout

    // ---------------- State ----------------
    private val rooms = mutableListOf<OnlineRoom>()
    private var roomAdapter: OnlineRoomAdapter? = null

    /** uid -> (firstName, avatarConfig) */
    private val profileCache = HashMap<String, Pair<String, Map<*, *>?>>()

    private var myRoomId: String? = null
    private var isNavigating = false
    private var isBusy = false

    // keeps my room from being treated as abandoned while I'm still waiting
    private val keepAliveHandler = Handler(Looper.getMainLooper())
    private var keepAliveRunnable: Runnable? = null

    // ---------------- Searching Text Animation Handler ----------------
    private val searchingTextHandler = Handler(Looper.getMainLooper())
    private var searchingTextRunnable: Runnable? = null
    private var dotCount = 1

    companion object {
        private const val ROOMS_COLLECTION = "online_games"

        /** A room whose owner hasn't pinged in this long is treated as abandoned. */
        private const val STALE_ROOM_MS = 90_000L

        /** How often my open room refreshes its heartbeat. */
        private const val KEEP_ALIVE_MS = 30_000L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Rive.init(requireContext())
        return inflater.inflate(R.layout.fragment_mini_games, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        UserPresenceHelper.startTracking()
        setupClickListeners()
    }

    private fun initViews(view: View) {
        recordValue = view.findViewById(R.id.recordValue)
        winsValue = view.findViewById(R.id.winsValue)
        losesValue = view.findViewById(R.id.losesValue)

        roomsRecycler = view.findViewById(R.id.roomsRecycler)
        emptyRoomsText = view.findViewById(R.id.emptyRoomsText)

        playOnlineBtn = view.findViewById(R.id.playOnlineBtn)
        playOnlineEnabledContainer = view.findViewById(R.id.playOnlineEnabledContainer)
        playOnlineDisabledContainer = view.findViewById(R.id.playOnlineDisabledContainer)
        playOnlineDisabledTxt = view.findViewById(R.id.playOnlineDisabledTxt)
        searchingIcon = view.findViewById(R.id.searchingIcon)

        soloGameBtn = view.findViewById(R.id.soloGameBtn)
        soloEnabledContainer = view.findViewById(R.id.soloEnabledContainer)
        soloDisabledContainer = view.findViewById(R.id.soloDisabledContainer)
        soloDisabledTxt = view.findViewById(R.id.soloDisabledTxt)

        loadingOverlayContainer = view.findViewById(R.id.loadingOverlayContainer)

        roomsRecycler.layoutManager = LinearLayoutManager(requireContext())
        roomAdapter = OnlineRoomAdapter(
            rooms = rooms,
            onJoinClick = { room -> joinRoom(room) },
            onCancelClick = { cancelMyRoom() }
        )
        roomsRecycler.adapter = roomAdapter
    }

    private fun setupClickListeners() {
        playOnlineBtn.setOnClickListener {
            if (isBusy || isNavigating || myRoomId != null) return@setOnClickListener
            createMyRoom()
        }

        soloGameBtn.setOnClickListener {
            if (isBusy || isNavigating) return@setOnClickListener
            isBusy = true
            isNavigating = true
            cancelMyRoom()
            showLoadingButtons()
            (activity as? MainActivity)?.setBottomNavigationEnabled(false)
            startActivity(Intent(requireContext(), MathStormActivity::class.java))
            requireActivity().overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
        }
    }

    // ==================== BUTTON & RIVE/TEXT ANIMATION STATES ====================

    private fun showIdleButtons() {
        if (!isAdded) return
        stopSearchingTextAnimation()

        playOnlineEnabledContainer.visibility = View.VISIBLE
        playOnlineDisabledContainer.visibility = View.INVISIBLE
        searchingIcon.visibility = View.VISIBLE
        playOnlineDisabledTxt.text = getString(R.string.searching_dots)

        (searchingIcon as? RiveAnimationView)?.pause()

        soloEnabledContainer.visibility = View.VISIBLE
        soloDisabledContainer.visibility = View.INVISIBLE
        soloDisabledTxt.text = getString(R.string.solo_game_30_xp)
    }

    private fun showSearchingButtons() {
        if (!isAdded) return
        playOnlineEnabledContainer.visibility = View.INVISIBLE
        playOnlineDisabledContainer.visibility = View.VISIBLE
        searchingIcon.visibility = View.VISIBLE

        (searchingIcon as? RiveAnimationView)?.play()
        startSearchingTextAnimation()

        soloEnabledContainer.visibility = View.VISIBLE
        soloDisabledContainer.visibility = View.INVISIBLE
    }

    private fun showLoadingButtons() {
        if (!isAdded) return
        stopSearchingTextAnimation()

        playOnlineEnabledContainer.visibility = View.INVISIBLE
        playOnlineDisabledContainer.visibility = View.VISIBLE
        searchingIcon.visibility = View.GONE

        (searchingIcon as? RiveAnimationView)?.pause()

        playOnlineDisabledTxt.text = getString(R.string.loading_caps)

        soloEnabledContainer.visibility = View.INVISIBLE
        soloDisabledContainer.visibility = View.VISIBLE
        soloDisabledTxt.text = getString(R.string.loading_caps)
    }

    private fun startSearchingTextAnimation() {
        stopSearchingTextAnimation()
        dotCount = 1
        val baseText = getString(R.string.searching_dots).trimEnd('.', ' ')

        searchingTextRunnable = object : Runnable {
            override fun run() {
                if (!isAdded || myRoomId == null) return
                val dots = ".".repeat(dotCount)
                playOnlineDisabledTxt.text = "$baseText$dots"
                dotCount = if (dotCount >= 3) 1 else dotCount + 1
                searchingTextHandler.postDelayed(this, 500L)
            }
        }
        searchingTextHandler.post(searchingTextRunnable!!)
    }

    private fun stopSearchingTextAnimation() {
        searchingTextRunnable?.let { searchingTextHandler.removeCallbacks(it) }
        searchingTextRunnable = null
    }

    // ==================== LIFECYCLE ====================

    override fun onStart() {
        super.onStart()
        isNavigating = false
        isBusy = false
        myRoomId = null

        loadingOverlayContainer.visibility = View.VISIBLE
        showIdleButtons()

        // wipe anything left behind by a crash / force-close / previous session
        deleteMyLeftoverRooms()

        attachStatsListeners()
        attachRoomsListener()
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.setBottomNavigationEnabled(true)
        isBusy = false
        if (myRoomId != null && !isNavigating) {
            (searchingIcon as? RiveAnimationView)?.play()
            startSearchingTextAnimation()
        }
    }

    /** Covers tab switches where the fragment is only paused, never stopped. */
    override fun onPause() {
        super.onPause()
        stopSearchingTextAnimation()
        (searchingIcon as? RiveAnimationView)?.pause()
        if (!isNavigating) cancelMyRoom()
    }

    /** Covers show/hide fragment transactions. */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopSearchingTextAnimation()
            (searchingIcon as? RiveAnimationView)?.pause()
            if (!isNavigating) cancelMyRoom()
        } else if (myRoomId != null && !isNavigating) {
            (searchingIcon as? RiveAnimationView)?.play()
            startSearchingTextAnimation()
        }
    }

    override fun onStop() {
        super.onStop()
        stopSearchingTextAnimation()
        (searchingIcon as? RiveAnimationView)?.pause()
        if (!isNavigating) cancelMyRoom()
        stopKeepAlive()

        roomsListener?.remove()
        myRoomListener?.remove()
        recordListener?.remove()
        onlineStatsListener?.remove()

        roomsListener = null
        myRoomListener = null
        recordListener = null
        onlineStatsListener = null
    }

    override fun onDestroyView() {
        stopSearchingTextAnimation()
        if (!isNavigating) cancelMyRoom()
        stopKeepAlive()
        super.onDestroyView()
    }

    // ==================== ROOM CLEANUP ====================

    /**
     * Deletes every open room I own. Runs on entry, so a room left behind by a killed
     * app, a crash or a lost connection never lingers in other players' lists.
     */
    private fun deleteMyLeftoverRooms() {
        val uid = auth.currentUser?.uid ?: return
        db.collection(ROOMS_COLLECTION)
            .whereEqualTo("player1", uid)
            .whereEqualTo("gameStatus", GameStatus.CREATED.name)
            .get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    if (doc.getString("player2").orEmpty().isEmpty()) doc.reference.delete()
                }
            }
    }

    /** Refreshes createdAt so my room isn't seen as abandoned while I'm still waiting. */
    private fun startKeepAlive(roomId: String) {
        stopKeepAlive()
        keepAliveRunnable = object : Runnable {
            override fun run() {
                if (!isAdded || myRoomId != roomId) return
                db.collection(ROOMS_COLLECTION).document(roomId)
                    .update("createdAt", System.currentTimeMillis())
                keepAliveHandler.postDelayed(this, KEEP_ALIVE_MS)
            }
        }
        keepAliveHandler.postDelayed(keepAliveRunnable!!, KEEP_ALIVE_MS)
    }

    private fun stopKeepAlive() {
        keepAliveRunnable?.let { keepAliveHandler.removeCallbacks(it) }
        keepAliveRunnable = null
    }

    // ==================== HEADER STATS ====================

    private fun attachStatsListeners() {
        val uid = auth.currentUser?.uid ?: return

        recordListener?.remove()
        recordListener = db.collection("users").document(uid)
            .collection("games").document("MathStorm")
            .addSnapshotListener { doc, _ ->
                if (!isAdded) return@addSnapshotListener
                recordValue.text = (doc?.getLong("highScore") ?: 0L).toString()
            }

        onlineStatsListener?.remove()
        onlineStatsListener = db.collection("users").document(uid)
            .collection("games").document("OnlineMathStorm")
            .addSnapshotListener { doc, _ ->
                if (!isAdded) return@addSnapshotListener
                winsValue.text = (doc?.getLong("onlineScore") ?: 0L).toString()
                losesValue.text = (doc?.getLong("loses") ?: 0L).toString()
            }
    }

    // ==================== ROOM LIST ====================

    private fun attachRoomsListener() {
        roomsListener?.remove()
        roomsListener = db.collection(ROOMS_COLLECTION)
            .whereEqualTo("gameStatus", GameStatus.CREATED.name)
            .addSnapshotListener { snapshot, error ->
                if (!isAdded) return@addSnapshotListener
                if (error != null || snapshot == null) {
                    loadingOverlayContainer.visibility = View.GONE
                    return@addSnapshotListener
                }
                handleRoomsSnapshot(snapshot.documents)
            }
    }

    private fun handleRoomsSnapshot(documents: List<DocumentSnapshot>) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()

        val parsed = mutableListOf<OnlineRoom>()
        for (doc in documents) {
            val player1 = doc.getString("player1").orEmpty()
            val player2 = doc.getString("player2").orEmpty()
            if (player1.isEmpty() || player2.isNotEmpty()) continue

            // abandoned room — hide it, and delete it if it's one of mine
            val createdAt = doc.getLong("createdAt") ?: 0L
            if (createdAt > 0L && now - createdAt > STALE_ROOM_MS) {
                if (player1 == uid && doc.id != myRoomId) doc.reference.delete()
                continue
            }

            parsed.add(
                OnlineRoom(
                    roomID = doc.id,
                    ownerUid = player1,
                    isMine = player1 == uid
                )
            )
        }

        val ordered = parsed.sortedByDescending { it.isMine }

        if (myRoomId != null && ordered.none { it.isMine } && !isNavigating) {
            myRoomId = null
            stopKeepAlive()
            showIdleButtons()
        }

        val missing = ordered.map { it.ownerUid }
            .distinct()
            .filter { !profileCache.containsKey(it) }

        if (missing.isEmpty()) {
            publishRooms(ordered)
            return
        }

        var pending = missing.size
        for (ownerUid in missing) {
            db.collection("users").document(ownerUid).get()
                .addOnCompleteListener { task ->
                    if (!isAdded) return@addOnCompleteListener
                    val snap = task.result
                    profileCache[ownerUid] = Pair(
                        snap?.getString("firstName").orEmpty(),
                        snap?.get("avatarConfig") as? Map<*, *>
                    )
                    pending--
                    if (pending == 0) publishRooms(ordered)
                }
        }
    }

    private fun publishRooms(list: List<OnlineRoom>) {
        if (!isAdded) return
        rooms.clear()
        rooms.addAll(list.map { room ->
            val profile = profileCache[room.ownerUid]
            room.copy(
                ownerName = profile?.first.orEmpty(),
                avatarConfig = profile?.second
            )
        })
        roomAdapter?.notifyDataSetChanged()

        emptyRoomsText.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
        loadingOverlayContainer.visibility = View.GONE
    }

    // ==================== CREATE / CANCEL / JOIN ====================

    private fun createMyRoom() {
        val uid = auth.currentUser?.uid ?: return
        isBusy = true
        OnlineGameData.myID = uid

        showSearchingButtons()

        val ref = db.collection(ROOMS_COLLECTION).document()
        val data = hashMapOf(
            "roomID" to ref.id,
            "player1" to uid,
            "player2" to "",
            "gameStatus" to GameStatus.CREATED.name,
            "createdAt" to System.currentTimeMillis()
        )

        ref.set(data)
            .addOnSuccessListener {
                isBusy = false
                if (!isAdded || isNavigating) {
                    ref.delete()
                    return@addOnSuccessListener
                }
                myRoomId = ref.id
                listenToMyRoom(ref.id)
                startKeepAlive(ref.id)
            }
            .addOnFailureListener {
                isBusy = false
                showIdleButtons()
            }
    }

    private fun listenToMyRoom(roomId: String) {
        myRoomListener?.remove()
        myRoomListener = db.collection(ROOMS_COLLECTION).document(roomId)
            .addSnapshotListener { snapshot, _ ->
                if (!isAdded || isNavigating) return@addSnapshotListener
                if (snapshot == null || !snapshot.exists()) {
                    myRoomId = null
                    stopKeepAlive()
                    showIdleButtons()
                    return@addSnapshotListener
                }
                val player2 = snapshot.getString("player2").orEmpty()
                if (player2.isNotEmpty()) {
                    val game = snapshot.toObject(GameModel::class.java)
                        ?: return@addSnapshotListener
                    goToWaitingRoom(game)
                }
            }
    }

    private fun cancelMyRoom() {
        val roomId = myRoomId ?: return
        myRoomId = null
        stopKeepAlive()
        myRoomListener?.remove()
        myRoomListener = null

        val ref = db.collection(ROOMS_COLLECTION).document(roomId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (snapshot.exists() && snapshot.getString("player2").orEmpty().isEmpty()) {
                transaction.delete(ref)
            }
            null
        }

        if (isAdded && !isNavigating) showIdleButtons()
    }

    private fun joinRoom(room: OnlineRoom) {
        val uid = auth.currentUser?.uid ?: return
        if (isBusy || isNavigating) return
        isBusy = true
        OnlineGameData.myID = uid

        cancelMyRoom()
        showLoadingButtons()

        val ref = db.collection(ROOMS_COLLECTION).document(room.roomID)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val taken = snapshot.getString("player2").orEmpty()
            if (!snapshot.exists() || taken.isNotEmpty()) {
                return@runTransaction false
            }
            transaction.update(
                ref,
                mapOf(
                    "player2" to uid,
                    "gameStatus" to GameStatus.JOINED.name
                )
            )
            true
        }.addOnSuccessListener { joined ->
            if (!isAdded) return@addOnSuccessListener
            if (joined != true) {
                isBusy = false
                showIdleButtons()
                return@addOnSuccessListener
            }
            ref.get().addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                val game = snapshot.toObject(GameModel::class.java)
                if (game == null) {
                    isBusy = false
                    showIdleButtons()
                    return@addOnSuccessListener
                }
                goToWaitingRoom(game)
            }
        }.addOnFailureListener {
            isBusy = false
            showIdleButtons()
        }
    }

    private fun goToWaitingRoom(game: GameModel) {
        if (isNavigating) return
        isNavigating = true

        stopKeepAlive()
        myRoomListener?.remove()
        myRoomListener = null

        showLoadingButtons()

        OnlineGameData.myID = auth.currentUser?.uid.orEmpty()
        OnlineGameData.saveGameModel(game)

        (activity as? MainActivity)?.setBottomNavigationEnabled(false)
        startActivity(Intent(requireContext(), OnlineWaitingRoomMathStormActivity::class.java))
        requireActivity().overridePendingTransition(
            R.anim.slide_in_right,
            R.anim.slide_out_left
        )
    }
}
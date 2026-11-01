package com.flow.todo.data.cloud

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.flow.todo.data.repository.FlowRepository
import com.flow.todo.domain.model.AccessibilityPreferences
import com.flow.todo.domain.model.AppUsageMetricType
import com.flow.todo.domain.model.CloudSyncState
import com.flow.todo.domain.model.CloudSyncStatus
import com.flow.todo.domain.model.DEFAULT_MOMENTUM_STOPS_HEX
import com.flow.todo.domain.model.DailyCompletionRecord
import com.flow.todo.domain.model.FirebaseCloudConfig
import com.flow.todo.domain.model.FlowGoal
import com.flow.todo.domain.model.GoalCategory
import com.flow.todo.domain.model.GoalComparison
import com.flow.todo.domain.model.GoalPeriod
import com.flow.todo.domain.model.GtdBucket
import com.flow.todo.domain.model.HealthGoalSpec
import com.flow.todo.domain.model.HealthMetricType
import com.flow.todo.domain.model.Project
import com.flow.todo.domain.model.RecurrenceRule
import com.flow.todo.domain.model.TaskItem
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class FirestoreSyncManager(
    private val context: Context,
    private val repository: FlowRepository
) {
    private val sharedPreferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val syncJob = SupervisorJob()
    private val syncScope = CoroutineScope(syncJob + Dispatchers.IO)
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    private val _state: MutableStateFlow<CloudSyncState>
    val state: StateFlow<CloudSyncState>

    init {
        val initialConfig = loadSavedConfig()
        val initialUser = loadSavedUser()
        val isReady = checkFirebaseReady(initialConfig)
        val currentAuthUser = if (isReady) runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() else null

        val resolvedUid = initialUser.uid ?: currentAuthUser?.uid
        val resolvedEmail = initialUser.email ?: currentAuthUser?.email
        val resolvedDisplayName = initialUser.displayName ?: currentAuthUser?.displayName
        val isGoogle = initialUser.isGoogle || (currentAuthUser?.providerData?.any { it.providerId == "google.com" } == true)

        val initialStatus = when {
            resolvedUid != null && isReady -> CloudSyncStatus.SYNCED
            resolvedUid != null && !isReady -> CloudSyncStatus.NOT_CONFIGURED
            else -> CloudSyncStatus.SIGNED_OUT
        }

        val initialMessage = when {
            resolvedUid != null && isReady -> "Signed in as ${resolvedDisplayName ?: resolvedEmail ?: "user"}"
            resolvedUid != null && !isReady -> "Signed in as ${resolvedEmail ?: "user"} • Add Firebase Project ID & API Key below to enable live Firestore sync"
            else -> "Sign in with Google to sync tasks & goals across devices"
        }

        _state = MutableStateFlow(
            CloudSyncState(
                status = initialStatus,
                userUid = resolvedUid,
                userEmail = resolvedEmail,
                userDisplayName = resolvedDisplayName,
                isGoogleAccount = isGoogle,
                customConfig = initialConfig,
                isFirebaseReady = isReady,
                statusMessage = initialMessage
            )
        )
        state = _state.asStateFlow()

        if (isReady && !resolvedUid.isNullOrBlank()) {
            startRealtimeListeners(resolvedUid)
        }
    }

    private data class SavedUser(
        val uid: String?,
        val email: String?,
        val displayName: String?,
        val isGoogle: Boolean
    )

    private fun loadSavedConfig(): FirebaseCloudConfig {
        return FirebaseCloudConfig(
            projectId = sharedPreferences.getString(KEY_PROJECT_ID, "") ?: "",
            applicationId = sharedPreferences.getString(KEY_APPLICATION_ID, "") ?: "",
            apiKey = sharedPreferences.getString(KEY_API_KEY, "") ?: "",
            webClientId = sharedPreferences.getString(KEY_WEB_CLIENT_ID, "") ?: ""
        )
    }

    private fun loadSavedUser(): SavedUser {
        return SavedUser(
            uid = sharedPreferences.getString(KEY_USER_UID, null),
            email = sharedPreferences.getString(KEY_USER_EMAIL, null),
            displayName = sharedPreferences.getString(KEY_USER_DISPLAY_NAME, null),
            isGoogle = sharedPreferences.getBoolean(KEY_IS_GOOGLE_ACCOUNT, false)
        )
    }

    private fun saveUserSession(
        uid: String?,
        email: String?,
        displayName: String?,
        isGoogle: Boolean
    ) {
        sharedPreferences.edit()
            .putString(KEY_USER_UID, uid)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_DISPLAY_NAME, displayName)
            .putBoolean(KEY_IS_GOOGLE_ACCOUNT, isGoogle)
            .apply()
    }

    fun ensureFirebaseInitialized(): Boolean {
        val ready = checkFirebaseReady(_state.value.customConfig)
        if (ready) {
            _state.update { if (!it.isFirebaseReady) it.copy(isFirebaseReady = true) else it }
        }
        return ready
    }

    private fun checkFirebaseReady(config: FirebaseCloudConfig): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        if (runCatching { FirebaseApp.initializeApp(context) != null }.getOrDefault(false)) return true
        if (config.isConfigured) {
            val customInit = runCatching {
                val options = FirebaseOptions.Builder()
                    .setProjectId(config.projectId.trim())
                    .setApplicationId(config.applicationId.trim())
                    .setApiKey(config.apiKey.trim())
                    .build()
                FirebaseApp.initializeApp(context, options) != null
            }.getOrDefault(false)
            return customInit || FirebaseApp.getApps(context).isNotEmpty()
        }
        return false
    }

    fun getGoogleSignInIntent(): Intent {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
        val webClientId = _state.value.customConfig.webClientId.trim()
        if (webClientId.isNotEmpty()) {
            runCatching { gsoBuilder.requestIdToken(webClientId) }
        }
        val client = GoogleSignIn.getClient(context, gsoBuilder.build())
        return client.signInIntent
    }

    suspend fun handleGoogleSignInResult(data: Intent?) {
        _state.update {
            it.copy(
                status = CloudSyncStatus.SIGNING_IN,
                statusMessage = "Signing in with Google..."
            )
        }

        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account: GoogleSignInAccount? = runCatching { task.getResult(ApiException::class.java) }.getOrNull()
            ?: runCatching { task.await() }.getOrNull()
            ?: runCatching { GoogleSignIn.getLastSignedInAccount(context) }.getOrNull()

        if (account == null) {
            _state.update {
                it.copy(
                    status = CloudSyncStatus.ERROR,
                    statusMessage = "Google Sign-In was cancelled or failed"
                )
            }
            return
        }

        val accountId = account.id ?: account.email?.hashCode()?.toString() ?: UUID.randomUUID().toString()
        val email = account.email
        val displayName = account.displayName
        val syncKey = "google_$accountId"

        saveUserSession(
            uid = syncKey,
            email = email,
            displayName = displayName,
            isGoogle = true
        )

        val firebaseReady = ensureFirebaseInitialized()
        if (firebaseReady) {
            val idToken = account.idToken
            if (!idToken.isNullOrBlank()) {
                runCatching {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(credential).await()
                }
            } else if (FirebaseAuth.getInstance().currentUser == null) {
                runCatching {
                    FirebaseAuth.getInstance().signInAnonymously().await()
                }
            }

            _state.update {
                it.copy(
                    status = CloudSyncStatus.SYNCED,
                    userUid = syncKey,
                    userEmail = email,
                    userDisplayName = displayName,
                    isGoogleAccount = true,
                    statusMessage = "Signed in as ${displayName ?: email ?: "Google User"}",
                    isFirebaseReady = true
                )
            }

            startRealtimeListeners(syncKey)
            syncAllNow()
        } else {
            _state.update {
                it.copy(
                    status = CloudSyncStatus.NOT_CONFIGURED,
                    userUid = syncKey,
                    userEmail = email,
                    userDisplayName = displayName,
                    isGoogleAccount = true,
                    statusMessage = "Signed in as ${email ?: displayName ?: "Google User"} • Add Firebase Project ID & API Key below to enable live Firestore sync",
                    isFirebaseReady = false
                )
            }
        }
    }

    suspend fun signInWithEmail(email: String, password: String, isSignUp: Boolean) {
        if (email.isBlank() || password.isBlank()) {
            _state.update { it.copy(status = CloudSyncStatus.ERROR, statusMessage = "Email and password cannot be empty") }
            return
        }
        _state.update {
            it.copy(
                status = CloudSyncStatus.SIGNING_IN,
                statusMessage = if (isSignUp) "Creating account..." else "Signing in..."
            )
        }

        val firebaseReady = ensureFirebaseInitialized()
        if (!firebaseReady) {
            _state.update {
                it.copy(
                    status = CloudSyncStatus.NOT_CONFIGURED,
                    statusMessage = "Firebase is not configured. Please enter Project ID, App ID, and API Key below."
                )
            }
            return
        }

        try {
            val auth = FirebaseAuth.getInstance()
            val authResult = if (isSignUp) {
                auth.createUserWithEmailAndPassword(email.trim(), password).await()
            } else {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
            }

            val user = authResult.user
            val uid = user?.uid ?: UUID.randomUUID().toString()
            val userEmail = user?.email ?: email.trim()
            val displayName = user?.displayName ?: userEmail.substringBefore("@")

            saveUserSession(
                uid = uid,
                email = userEmail,
                displayName = displayName,
                isGoogle = false
            )

            _state.update {
                it.copy(
                    status = CloudSyncStatus.SYNCED,
                    userUid = uid,
                    userEmail = userEmail,
                    userDisplayName = displayName,
                    isGoogleAccount = false,
                    statusMessage = "Signed in as $userEmail",
                    isFirebaseReady = true
                )
            }

            startRealtimeListeners(uid)
            syncAllNow()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = CloudSyncStatus.ERROR,
                    statusMessage = "Authentication failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun getCurrentSyncKey(): String? {
        val userUid = _state.value.userUid
        if (!userUid.isNullOrBlank()) return userUid
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return FirebaseAuth.getInstance().currentUser?.uid
        }
        return null
    }

    fun startRealtimeListeners(syncKey: String) {
        if (!ensureFirebaseInitialized()) return

        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()

        val firestore = FirebaseFirestore.getInstance()
        val userDoc = firestore.collection("users").document(syncKey)

        // 1. Tasks Listener
        val tasksListener = userDoc.collection("tasks").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _state.update { it.copy(status = CloudSyncStatus.ERROR, statusMessage = "Tasks sync error: ${error.message}") }
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                val remoteTasks = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { mapToTask(it, doc.id) }
                }
                if (remoteTasks.isNotEmpty()) {
                    syncScope.launch {
                        repository.upsertTasks(remoteTasks)
                    }
                }
            }
        }
        listenerRegistrations.add(tasksListener)

        // 2. Projects Listener
        val projectsListener = userDoc.collection("projects").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && !snapshot.isEmpty) {
                val remoteProjects = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { mapToProject(it, doc.id) }
                }
                if (remoteProjects.isNotEmpty()) {
                    syncScope.launch {
                        repository.upsertProjects(remoteProjects)
                    }
                }
            }
        }
        listenerRegistrations.add(projectsListener)

        // 3. Goals Listener
        val goalsListener = userDoc.collection("goals").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && !snapshot.isEmpty) {
                val remoteGoals = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { mapToGoal(it, doc.id) }
                }
                if (remoteGoals.isNotEmpty()) {
                    syncScope.launch {
                        repository.upsertGoals(remoteGoals)
                    }
                }
            }
        }
        listenerRegistrations.add(goalsListener)

        // 4. Daily History Listener
        val historyListener = userDoc.collection("dailyHistory").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && !snapshot.isEmpty) {
                val remoteRecords = snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { mapToDailyRecord(it, doc.id) }
                }
                if (remoteRecords.isNotEmpty()) {
                    syncScope.launch {
                        repository.upsertDailyHistoryList(remoteRecords)
                    }
                }
            }
        }
        listenerRegistrations.add(historyListener)

        // 5. Accessibility Preferences Listener
        val prefsListener = userDoc.collection("profile").document("preferences").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                snapshot.data?.let { data ->
                    val remotePrefs = mapToPreferences(data)
                    syncScope.launch {
                        repository.updateAccessibilityPreferences { remotePrefs }
                    }
                }
            }
        }
        listenerRegistrations.add(prefsListener)
    }

    suspend fun syncAllNow() {
        val syncKey = getCurrentSyncKey()
        if (syncKey.isNullOrBlank()) {
            _state.update { it.copy(status = CloudSyncStatus.SIGNED_OUT, statusMessage = "Please sign in to sync") }
            return
        }

        if (!ensureFirebaseInitialized()) {
            _state.update {
                it.copy(
                    status = CloudSyncStatus.NOT_CONFIGURED,
                    statusMessage = "Firebase not initialized • Please check configuration"
                )
            }
            return
        }

        _state.update { it.copy(status = CloudSyncStatus.SYNCING, statusMessage = "Syncing with Firestore...") }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = firestore.collection("users").document(syncKey)

            val localTasks = repository.getAllTasks()
            val localProjects = repository.getAllProjects()
            val localGoals = repository.getAllGoals()
            val localDailyHistory = repository.getAllDailyHistory()
            val localPrefs = repository.accessibilityPreferences.value

            // 1. Sync Tasks
            val tasksColl = userDoc.collection("tasks")
            val remoteTasksSnap = tasksColl.get().await()
            val remoteTasksMap = remoteTasksSnap.documents.associate { doc ->
                doc.id to (doc.data?.let { mapToTask(it, doc.id) })
            }.filterValues { it != null }.mapValues { it.value!! }

            val localTasksMap = localTasks.associateBy { it.id }

            for (localTask in localTasks) {
                val remoteTask = remoteTasksMap[localTask.id]
                if (remoteTask == null || (localTask.completedAt ?: localTask.createdAt) > (remoteTask.completedAt ?: remoteTask.createdAt)) {
                    tasksColl.document(localTask.id).set(taskToMap(localTask)).await()
                }
            }

            val tasksToUpsert = mutableListOf<TaskItem>()
            for ((remoteId, remoteTask) in remoteTasksMap) {
                val localTask = localTasksMap[remoteId]
                if (localTask == null || (remoteTask.completedAt ?: remoteTask.createdAt) > (localTask.completedAt ?: localTask.createdAt)) {
                    tasksToUpsert.add(remoteTask)
                }
            }
            if (tasksToUpsert.isNotEmpty()) {
                repository.upsertTasks(tasksToUpsert)
            }

            // 2. Sync Projects
            val projectsColl = userDoc.collection("projects")
            val remoteProjSnap = projectsColl.get().await()
            val remoteProjMap = remoteProjSnap.documents.associate { doc ->
                doc.id to (doc.data?.let { mapToProject(it, doc.id) })
            }.filterValues { it != null }.mapValues { it.value!! }

            val localProjMap = localProjects.associateBy { it.id }

            for (localProj in localProjects) {
                if (!remoteProjMap.containsKey(localProj.id)) {
                    projectsColl.document(localProj.id).set(projectToMap(localProj)).await()
                }
            }
            val projToUpsert = remoteProjMap.values.filter { !localProjMap.containsKey(it.id) }
            if (projToUpsert.isNotEmpty()) {
                repository.upsertProjects(projToUpsert)
            }

            // 3. Sync Goals
            val goalsColl = userDoc.collection("goals")
            val remoteGoalsSnap = goalsColl.get().await()
            val remoteGoalsMap = remoteGoalsSnap.documents.associate { doc ->
                doc.id to (doc.data?.let { mapToGoal(it, doc.id) })
            }.filterValues { it != null }.mapValues { it.value!! }

            val localGoalsMap = localGoals.associateBy { it.id }

            for (localGoal in localGoals) {
                if (!remoteGoalsMap.containsKey(localGoal.id)) {
                    goalsColl.document(localGoal.id).set(goalToMap(localGoal)).await()
                }
            }
            val goalsToUpsert = remoteGoalsMap.values.filter { !localGoalsMap.containsKey(it.id) }
            if (goalsToUpsert.isNotEmpty()) {
                repository.upsertGoals(goalsToUpsert)
            }

            // 4. Sync Daily History
            val historyColl = userDoc.collection("dailyHistory")
            val remoteHistorySnap = historyColl.get().await()
            val remoteHistoryMap = remoteHistorySnap.documents.associate { doc ->
                val epoch = (doc.data?.get("epochDay") as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L
                epoch to (doc.data?.let { mapToDailyRecord(it, doc.id) })
            }.filterValues { it != null }.mapValues { it.value!! }

            val localHistoryMap = localDailyHistory.associateBy { it.epochDay }

            for (localRecord in localDailyHistory) {
                val remoteRecord = remoteHistoryMap[localRecord.epochDay]
                if (remoteRecord == null || localRecord.updatedAt > remoteRecord.updatedAt) {
                    historyColl.document(localRecord.epochDay.toString()).set(dailyRecordToMap(localRecord)).await()
                }
            }
            val recordsToUpsert = mutableListOf<DailyCompletionRecord>()
            for ((epochDay, remoteRecord) in remoteHistoryMap) {
                val localRecord = localHistoryMap[epochDay]
                if (localRecord == null || remoteRecord.updatedAt > localRecord.updatedAt) {
                    recordsToUpsert.add(remoteRecord)
                }
            }
            if (recordsToUpsert.isNotEmpty()) {
                repository.upsertDailyHistoryList(recordsToUpsert)
            }

            // 5. Sync Preferences
            val prefsDoc = userDoc.collection("profile").document("preferences")
            val remotePrefsSnap = prefsDoc.get().await()
            if (remotePrefsSnap.exists() && remotePrefsSnap.data != null) {
                val remotePrefs = mapToPreferences(remotePrefsSnap.data!!)
                repository.updateAccessibilityPreferences { remotePrefs }
            } else {
                prefsDoc.set(preferencesToMap(localPrefs)).await()
            }

            val now = System.currentTimeMillis()
            val timeString = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now())
            _state.update {
                it.copy(
                    status = CloudSyncStatus.SYNCED,
                    lastSyncedMillis = now,
                    statusMessage = "Synced with Cloud at $timeString"
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    status = CloudSyncStatus.ERROR,
                    statusMessage = "Sync failed: ${e.localizedMessage ?: e.message}"
                )
            }
        }
    }

    fun pushTask(task: TaskItem) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("tasks").document(task.id)
                    .set(taskToMap(task)).await()
            }
        }
    }

    fun deleteRemoteTask(taskId: String) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("tasks").document(taskId)
                    .delete().await()
            }
        }
    }

    fun pushProject(project: Project) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("projects").document(project.id)
                    .set(projectToMap(project)).await()
            }
        }
    }

    fun deleteRemoteProject(projectId: String) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("projects").document(projectId)
                    .delete().await()
            }
        }
    }

    fun pushGoal(goal: FlowGoal) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("goals").document(goal.id)
                    .set(goalToMap(goal)).await()
            }
        }
    }

    fun deleteRemoteGoal(goalId: String) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("goals").document(goalId)
                    .delete().await()
            }
        }
    }

    fun pushDailyHistory(record: DailyCompletionRecord) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("dailyHistory").document(record.epochDay.toString())
                    .set(dailyRecordToMap(record)).await()
            }
        }
    }

    fun pushPreferences(prefs: AccessibilityPreferences) {
        val syncKey = getCurrentSyncKey() ?: return
        if (!ensureFirebaseInitialized()) return
        syncScope.launch {
            runCatching {
                FirebaseFirestore.getInstance().collection("users").document(syncKey)
                    .collection("profile").document("preferences")
                    .set(preferencesToMap(prefs)).await()
            }
        }
    }

    fun updateFirebaseConfig(config: FirebaseCloudConfig) {
        sharedPreferences.edit()
            .putString(KEY_PROJECT_ID, config.projectId.trim())
            .putString(KEY_APPLICATION_ID, config.applicationId.trim())
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_WEB_CLIENT_ID, config.webClientId.trim())
            .apply()

        _state.update { it.copy(customConfig = config) }

        val ready = ensureFirebaseInitialized()
        if (ready) {
            val currentUid = _state.value.userUid
            if (!currentUid.isNullOrBlank()) {
                _state.update {
                    it.copy(
                        status = CloudSyncStatus.SYNCED,
                        statusMessage = "Firebase connected • Signed in as ${it.userDisplayName ?: it.userEmail ?: "user"}",
                        isFirebaseReady = true
                    )
                }
                startRealtimeListeners(currentUid)
                syncScope.launch { syncAllNow() }
            } else {
                _state.update {
                    it.copy(
                        status = CloudSyncStatus.SIGNED_OUT,
                        statusMessage = "Firebase connected • Ready to sign in",
                        isFirebaseReady = true
                    )
                }
            }
        } else {
            _state.update {
                it.copy(
                    isFirebaseReady = false,
                    status = if (it.isSignedIn) CloudSyncStatus.NOT_CONFIGURED else CloudSyncStatus.SIGNED_OUT,
                    statusMessage = if (it.isSignedIn)
                        "Signed in as ${it.userEmail ?: "user"} • Complete Firebase config below"
                    else
                        "Configure Firebase or sign in with Google"
                )
            }
        }
    }

    fun signOut() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()

        saveUserSession(null, null, null, false)

        runCatching {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            GoogleSignIn.getClient(context, gso).signOut()
        }

        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance().signOut()
            }
        }

        _state.update {
            it.copy(
                status = CloudSyncStatus.SIGNED_OUT,
                userUid = null,
                userEmail = null,
                userDisplayName = null,
                isGoogleAccount = false,
                statusMessage = "Signed out • Sign in with Google to sync across devices"
            )
        }
    }

    fun destroy() {
        listenerRegistrations.forEach { it.remove() }
        listenerRegistrations.clear()
        syncJob.cancel()
    }

    companion object {
        private const val PREFS_NAME = "flow_cloud_sync_prefs"
        private const val KEY_PROJECT_ID = "cloud_project_id"
        private const val KEY_APPLICATION_ID = "cloud_application_id"
        private const val KEY_API_KEY = "cloud_api_key"
        private const val KEY_WEB_CLIENT_ID = "cloud_web_client_id"
        private const val KEY_USER_UID = "cloud_user_uid"
        private const val KEY_USER_EMAIL = "cloud_user_email"
        private const val KEY_USER_DISPLAY_NAME = "cloud_user_display_name"
        private const val KEY_IS_GOOGLE_ACCOUNT = "cloud_is_google_account"

        fun taskToMap(task: TaskItem): Map<String, Any?> = mapOf(
            "id" to task.id,
            "title" to task.title,
            "notes" to task.notes,
            "bucketName" to task.bucket.name,
            "dueDateEpochDay" to task.dueDateEpochDay,
            "projectId" to task.projectId,
            "isFlagged" to task.isFlagged,
            "isCompleted" to task.isCompleted,
            "recurrenceRuleRaw" to task.recurrenceRule?.toStorageString(),
            "healthGoalRaw" to task.healthGoal?.toStorageString(),
            "createdAt" to task.createdAt,
            "completedAt" to task.completedAt
        )

        fun mapToTask(data: Map<String, Any?>, docId: String): TaskItem {
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val title = data["title"] as? String ?: ""
            val notes = data["notes"] as? String ?: ""
            val bucketName = data["bucketName"] as? String ?: GtdBucket.INBOX.name
            val bucket = runCatching { GtdBucket.valueOf(bucketName) }.getOrDefault(GtdBucket.INBOX)
            val dueDateEpochDay = (data["dueDateEpochDay"] as? Number)?.toLong()
            val projectId = data["projectId"] as? String
            val isFlagged = data["isFlagged"] as? Boolean ?: false
            val isCompleted = data["isCompleted"] as? Boolean ?: false
            val recurrenceRuleRaw = data["recurrenceRuleRaw"] as? String
            val healthGoalRaw = data["healthGoalRaw"] as? String
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val completedAt = (data["completedAt"] as? Number)?.toLong()

            return TaskItem(
                id = id,
                title = title,
                notes = notes,
                bucket = bucket,
                dueDateEpochDay = dueDateEpochDay,
                projectId = projectId,
                isFlagged = isFlagged,
                isCompleted = isCompleted,
                recurrenceRule = RecurrenceRule.fromStorageString(recurrenceRuleRaw),
                healthGoal = HealthGoalSpec.fromStorageString(healthGoalRaw),
                createdAt = createdAt,
                completedAt = completedAt
            )
        }

        fun projectToMap(project: Project): Map<String, Any?> = mapOf(
            "id" to project.id,
            "name" to project.name,
            "colorHex" to project.colorHex,
            "createdAt" to project.createdAt
        )

        fun mapToProject(data: Map<String, Any?>, docId: String): Project {
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val name = data["name"] as? String ?: ""
            val colorHex = (data["colorHex"] as? Number)?.toLong() ?: 0xFF2F6BFFL
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            return Project(
                id = id,
                name = name,
                colorHex = colorHex,
                createdAt = createdAt
            )
        }

        fun goalToMap(goal: FlowGoal): Map<String, Any?> = mapOf(
            "id" to goal.id,
            "title" to goal.title,
            "categoryName" to goal.category.name,
            "comparisonName" to goal.comparison.name,
            "periodName" to goal.period.name,
            "targetValue" to goal.targetValue,
            "projectId" to goal.projectId,
            "healthMetricName" to goal.healthMetric?.name,
            "targetPackageName" to goal.targetPackageName,
            "targetAppName" to goal.targetAppName,
            "appUsageMetricName" to goal.appUsageMetric?.name,
            "createdAt" to goal.createdAt
        )

        fun mapToGoal(data: Map<String, Any?>, docId: String): FlowGoal {
            val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val title = data["title"] as? String ?: ""
            val categoryName = data["categoryName"] as? String ?: GoalCategory.TASK_COMPLETION.name
            val comparisonName = data["comparisonName"] as? String ?: GoalComparison.AT_LEAST.name
            val periodName = data["periodName"] as? String ?: GoalPeriod.DAILY.name
            val targetValue = (data["targetValue"] as? Number)?.toDouble() ?: 0.0
            val projectId = data["projectId"] as? String
            val healthMetricName = data["healthMetricName"] as? String
            val targetPackageName = data["targetPackageName"] as? String
            val targetAppName = data["targetAppName"] as? String
            val appUsageMetricName = data["appUsageMetricName"] as? String
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return FlowGoal(
                id = id,
                title = title,
                category = runCatching { GoalCategory.valueOf(categoryName) }.getOrDefault(GoalCategory.TASK_COMPLETION),
                comparison = runCatching { GoalComparison.valueOf(comparisonName) }.getOrDefault(GoalComparison.AT_LEAST),
                period = runCatching { GoalPeriod.valueOf(periodName) }.getOrDefault(GoalPeriod.DAILY),
                targetValue = targetValue,
                projectId = projectId,
                healthMetric = healthMetricName?.let { runCatching { HealthMetricType.valueOf(it) }.getOrNull() },
                targetPackageName = targetPackageName,
                targetAppName = targetAppName,
                appUsageMetric = appUsageMetricName?.let { runCatching { AppUsageMetricType.valueOf(it) }.getOrNull() },
                createdAt = createdAt
            )
        }

        fun dailyRecordToMap(record: DailyCompletionRecord): Map<String, Any?> = mapOf(
            "epochDay" to record.epochDay,
            "tasksCompletedCount" to record.tasksCompletedCount,
            "goalsCompletedCount" to record.goalsCompletedCount,
            "totalGoalsCount" to record.totalGoalsCount,
            "momentumScore" to record.momentumScore,
            "updatedAt" to record.updatedAt
        )

        fun mapToDailyRecord(data: Map<String, Any?>, docId: String): DailyCompletionRecord {
            val epochDay = (data["epochDay"] as? Number)?.toLong() ?: docId.toLongOrNull() ?: 0L
            val tasksCompleted = (data["tasksCompletedCount"] as? Number)?.toInt() ?: 0
            val goalsCompleted = (data["goalsCompletedCount"] as? Number)?.toInt() ?: 0
            val totalGoals = (data["totalGoalsCount"] as? Number)?.toInt() ?: 0
            val momentumScore = (data["momentumScore"] as? Number)?.toFloat() ?: 0f
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return DailyCompletionRecord(
                epochDay = epochDay,
                tasksCompletedCount = tasksCompleted,
                goalsCompletedCount = goalsCompleted,
                totalGoalsCount = totalGoals,
                momentumScore = momentumScore,
                updatedAt = updatedAt
            )
        }

        fun preferencesToMap(prefs: AccessibilityPreferences): Map<String, Any?> = mapOf(
            "reduceTransparency" to prefs.reduceTransparency,
            "reduceMotion" to prefs.reduceMotion,
            "increasedContrast" to prefs.increasedContrast,
            "darkThemeOverride" to when (prefs.darkThemeOverride) {
                true -> "DARK"
                false -> "LIGHT"
                null -> "SYSTEM"
            },
            "useDynamicColor" to prefs.useDynamicColor,
            "useMomentumColorSpectrum" to prefs.useMomentumColorSpectrum,
            "syncSystemColorsWithShizuku" to prefs.syncSystemColorsWithShizuku,
            "momentumStopHexes" to prefs.momentumStopHexes
        )

        fun mapToPreferences(data: Map<String, Any?>): AccessibilityPreferences {
            val darkThemeRaw = data["darkThemeOverride"] as? String
            val darkThemeOverride = when (darkThemeRaw) {
                "DARK" -> true
                "LIGHT" -> false
                else -> (data["darkThemeOverride"] as? Boolean)
            }
            val rawStops = (data["momentumStopHexes"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }
            val stops = if (rawStops != null && rawStops.size == 5) rawStops else DEFAULT_MOMENTUM_STOPS_HEX

            return AccessibilityPreferences(
                reduceTransparency = data["reduceTransparency"] as? Boolean ?: false,
                reduceMotion = data["reduceMotion"] as? Boolean ?: false,
                increasedContrast = data["increasedContrast"] as? Boolean ?: false,
                darkThemeOverride = darkThemeOverride,
                useDynamicColor = data["useDynamicColor"] as? Boolean ?: true,
                useMomentumColorSpectrum = data["useMomentumColorSpectrum"] as? Boolean ?: true,
                syncSystemColorsWithShizuku = data["syncSystemColorsWithShizuku"] as? Boolean ?: false,
                momentumStopHexes = stops
            )
        }
    }
}

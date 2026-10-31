package com.flow.todo.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.flow.todo.domain.model.HealthDailySnapshot
import com.flow.todo.domain.model.HealthMetricType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthConnectManager(private val context: Context) {

    val allReadPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(Vo2MaxRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    )

    private val _snapshot = MutableStateFlow(HealthDailySnapshot(totalPermissionCount = allReadPermissions.size))
    val snapshot: StateFlow<HealthDailySnapshot> = _snapshot.asStateFlow()

    suspend fun refreshDailySnapshot(): HealthDailySnapshot = withContext(Dispatchers.IO) {
        val sdkStatus = runCatching {
            HealthConnectClient.getSdkStatus(context)
        }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            val unavailableSnapshot = HealthDailySnapshot(
                isSdkAvailable = false,
                grantedPermissionCount = 0,
                totalPermissionCount = allReadPermissions.size,
                metrics = emptyMap(),
                lastSyncedMillis = System.currentTimeMillis()
            )
            _snapshot.value = unavailableSnapshot
            return@withContext unavailableSnapshot
        }

        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        if (client == null) {
            val unavailableSnapshot = HealthDailySnapshot(
                isSdkAvailable = false,
                grantedPermissionCount = 0,
                totalPermissionCount = allReadPermissions.size,
                metrics = emptyMap(),
                lastSyncedMillis = System.currentTimeMillis()
            )
            _snapshot.value = unavailableSnapshot
            return@withContext unavailableSnapshot
        }

        val granted = runCatching {
            client.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())

        val zoneId = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zoneId).toInstant()
        val now = Instant.now()
        val todayTimeRange = TimeRangeFilter.between(startOfDay, now)
        val last24HoursRange = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now)

        val steps = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[StepsRecord.COUNT_TOTAL]?.toDouble() ?: 0.0
        }.getOrDefault(0.0)

        val activeCalories = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        }.getOrDefault(0.0)

        val exerciseMinutes = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes()?.toDouble() ?: 0.0
        }.getOrDefault(0.0)

        val sleepHours = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = last24HoursRange
                )
            )
            val minutes = response[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.toMinutes() ?: 0L
            minutes.toDouble() / 60.0
        }.getOrDefault(0.0)

        val distanceKm = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0
        }.getOrDefault(0.0)

        val hydrationLiters = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(HydrationRecord.VOLUME_TOTAL),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[HydrationRecord.VOLUME_TOTAL]?.inLiters ?: 0.0
        }.getOrDefault(0.0)

        val floorsClimbed = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL] ?: 0.0
        }.getOrDefault(0.0)

        val weightKg = runCatching {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now)
                )
            )
            response.records.lastOrNull()?.weight?.inKilograms ?: 0.0
        }.getOrDefault(0.0)

        val restingHeartRate = runCatching {
            val response = client.aggregate(
                AggregateRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG),
                    timeRangeFilter = todayTimeRange
                )
            )
            response[HeartRateRecord.BPM_AVG]?.toDouble() ?: 0.0
        }.getOrDefault(0.0)

        val metricsMap = mapOf(
            HealthMetricType.STEPS to steps,
            HealthMetricType.ACTIVE_CALORIES to activeCalories,
            HealthMetricType.EXERCISE_MINUTES to exerciseMinutes,
            HealthMetricType.SLEEP_HOURS to sleepHours,
            HealthMetricType.DISTANCE_KM to distanceKm,
            HealthMetricType.HYDRATION_LITERS to hydrationLiters,
            HealthMetricType.FLOORS_CLIMBED to floorsClimbed,
            HealthMetricType.WEIGHT_KG to weightKg,
            HealthMetricType.RESTING_HEART_RATE to restingHeartRate
        )

        val updatedSnapshot = HealthDailySnapshot(
            isSdkAvailable = true,
            grantedPermissionCount = granted.intersect(allReadPermissions).size,
            totalPermissionCount = allReadPermissions.size,
            metrics = metricsMap,
            lastSyncedMillis = System.currentTimeMillis()
        )

        _snapshot.value = updatedSnapshot
        updatedSnapshot
    }
}
